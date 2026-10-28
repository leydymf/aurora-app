package com.auroraclima.app.access.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.records.data.ReversiblePayloadCipher
import java.io.File
import java.util.Base64
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La marca de la guía se guarda sellada (nunca en claro), se consume al leerse y
 * muere con la cuenta (RF-20). Tiene dos valores (desde el inicio, solo el final) y
 * la de v0.3.3 se lee como «desde el inicio». El cifrador es el falso reversible de
 * las pruebas JVM; el Keystore real no existe en esta JVM.
 */
@RunWith(RobolectricTestRunner::class)
class SealedWalkthroughRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = SealedWalkthroughRepository(context, ReversiblePayloadCipher())

    private fun prefsXml(): String =
        File(context.applicationInfo.dataDir, "shared_prefs/view_meta.xml").takeIf { it.exists() }?.readText().orEmpty()

    @Test
    fun `sin marca no hay nada pendiente`() = runTest {
        assertNull(repository.consume().getOrThrow())
    }

    @Test
    fun `cada marca se consume una sola vez`() = runTest {
        WalkthroughMark.entries.forEach { mark ->
            repository.mark(mark).getOrThrow()

            assertEquals(mark, repository.consume().getOrThrow())
            assertNull(repository.consume().getOrThrow())
        }
    }

    @Test
    fun `una marca nueva reemplaza la anterior`() = runTest {
        repository.mark(WalkthroughMark.FROM_START).getOrThrow()
        repository.mark(WalkthroughMark.TO_FINISH).getOrThrow()

        assertEquals(WalkthroughMark.TO_FINISH, repository.consume().getOrThrow())
    }

    @Test
    fun `la marca de la version anterior se lee como guia desde el inicio`() = runTest {
        // v0.3.2 y v0.3.3 sellaban 16 bytes con el primero en 1.
        val cipher = ReversiblePayloadCipher()
        val legacy = cipher.seal(ByteArray(16).also { it[0] = 1 })
        context.getSharedPreferences("view_meta", Context.MODE_PRIVATE).edit()
            .putString("v0", Base64.getEncoder().encodeToString(legacy.iv))
            .putString("v1", Base64.getEncoder().encodeToString(legacy.ciphertext))
            .commit()

        val reader = SealedWalkthroughRepository(context, cipher)

        assertEquals(WalkthroughMark.FROM_START, reader.consume().getOrThrow())
    }

    @Test
    fun `marcar y consumir a la vez no se pisan`() = runTest {
        // La guía arma la marca al llegar a la nube y la desarma si se va: el
        // desarme lanzado justo después debe ver la marca ya escrita.
        val marking = async { repository.mark(WalkthroughMark.TO_FINISH) }
        val consuming = async { repository.consume() }
        marking.await().getOrThrow()

        assertEquals(WalkthroughMark.TO_FINISH, consuming.await().getOrThrow())
        assertNull(repository.consume().getOrThrow())
    }

    @Test
    fun `lo que queda en disco es el texto sellado y no una bandera legible`() = runTest {
        repository.mark(WalkthroughMark.TO_FINISH).getOrThrow()

        val xml = prefsXml()
        assertTrue("debe haber algo guardado", xml.contains("v1"))
        // Una bandera en claro sería un booleano o un texto legible: aquí solo base64.
        assertFalse(xml.contains("boolean"))
        assertFalse(xml.contains("true"))
        assertFalse(xml.contains("pending", ignoreCase = true))
        assertFalse(xml.contains("tutorial", ignoreCase = true))
        assertFalse(xml.contains("tour", ignoreCase = true))
        assertFalse(xml.contains("guide", ignoreCase = true))
        assertFalse(xml.contains("finish", ignoreCase = true))
    }

    @Test
    fun `consumir deja el archivo vacio`() = runTest {
        repository.mark(WalkthroughMark.TO_FINISH).getOrThrow()
        repository.consume().getOrThrow()

        assertFalse(prefsXml().contains("v1"))
    }

    @Test
    fun `borrar la cuenta retira la marca`() = runTest {
        repository.mark(WalkthroughMark.TO_FINISH).getOrThrow()

        WalkthroughEraser(repository).erase()

        assertNull(repository.consume().getOrThrow())
        assertFalse(prefsXml().contains("v1"))
    }

    @Test
    fun `descartar una activacion sin terminar tambien la retira`() {
        // Sin exención: a esa cuenta nadie la estrenó (ver KDoc del eraser).
        assertFalse(WalkthroughEraser(repository).keptOnUnfinishedActivation)
    }

    @Test
    fun `si la llave no existe marcar falla y no deja nada a medias`() = runTest {
        val noKey = SealedWalkthroughRepository(context, FailingCipher())

        assertTrue(noKey.mark(WalkthroughMark.FROM_START).isFailure)
        assertFalse(prefsXml().contains("v1"))
    }

    @Test
    fun `una marca que no abre se descarta y cuenta como ninguna`() = runTest {
        repository.mark(WalkthroughMark.TO_FINISH).getOrThrow()
        // Otra cuenta o un dato adulterado: el mismo contenido con otro cifrador que no abre.
        val otherAccount = SealedWalkthroughRepository(context, FailingCipher(sealWorks = true))

        assertTrue(otherAccount.consume().isFailure)
        // Y no se queda atascada: la siguiente lectura ya no la encuentra.
        assertNull(repository.consume().getOrThrow())
    }

    @Test
    fun `una marca con otro contenido no cuenta`() = runTest {
        val spy = RecordingCipher()
        val tampered = SealedWalkthroughRepository(context, spy)
        tampered.mark(WalkthroughMark.FROM_START).getOrThrow()
        spy.replaceWithOtherContent = true

        assertNull(tampered.consume().getOrThrow())
    }

    @Test
    fun `sin llave todavia la marca no se consume ni se pierde`() = runTest {
        val cipher = SwitchableCipher()
        val reader = SealedWalkthroughRepository(context, cipher)
        reader.mark(WalkthroughMark.TO_FINISH).getOrThrow()
        cipher.ready = false

        assertTrue(reader.hasMark())
        assertFalse(reader.canOpen())
        assertNull(reader.consume().getOrThrow())
        assertTrue("sigue guardada", reader.hasMark())

        cipher.ready = true
        assertEquals(WalkthroughMark.TO_FINISH, reader.consume().getOrThrow())
        assertFalse(reader.hasMark())
    }

    /** Como el de la DEK: sin sesión, no hay llave. */
    private class SwitchableCipher : PayloadCipher {
        private val inner = ReversiblePayloadCipher()
        var ready = true

        override fun seal(plaintext: ByteArray): SealedPayload = inner.seal(plaintext)

        override fun open(sealed: SealedPayload): ByteArray =
            if (ready) inner.open(sealed) else throw IllegalStateException()

        override fun isReady(): Boolean = ready
    }

    private class FailingCipher(private val sealWorks: Boolean = false) : PayloadCipher {
        private val inner = ReversiblePayloadCipher()

        override fun seal(plaintext: ByteArray): SealedPayload =
            if (sealWorks) inner.seal(plaintext) else throw IllegalStateException()

        override fun open(sealed: SealedPayload): ByteArray = throw IllegalStateException()
    }

    private class RecordingCipher : PayloadCipher {
        private val inner = ReversiblePayloadCipher()
        var replaceWithOtherContent = false

        override fun seal(plaintext: ByteArray): SealedPayload = inner.seal(plaintext)

        override fun open(sealed: SealedPayload): ByteArray =
            if (replaceWithOtherContent) ByteArray(16) else inner.open(sealed)
    }
}

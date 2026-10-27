package com.auroraclima.app.access.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.records.data.ReversiblePayloadCipher
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * El recorrido pendiente se guarda sellado (nunca en claro), se
 * consume al leerse y muere con la cuenta (RF-20). El cifrador es el falso
 * reversible de las pruebas JVM; el Keystore real no existe en esta JVM.
 */
@RunWith(RobolectricTestRunner::class)
class SealedHomeTourRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = SealedHomeTourRepository(context, ReversiblePayloadCipher())

    private fun prefsXml(): String =
        File(context.applicationInfo.dataDir, "shared_prefs/view_meta.xml").takeIf { it.exists() }?.readText().orEmpty()

    @Test
    fun `sin marca no hay recorrido pendiente`() = runTest {
        assertEquals(false, repository.consumePending().getOrThrow())
    }

    @Test
    fun `lo marcado se consume una sola vez`() = runTest {
        repository.markPending().getOrThrow()

        assertEquals(true, repository.consumePending().getOrThrow())
        assertEquals(false, repository.consumePending().getOrThrow())
    }

    @Test
    fun `lo que queda en disco es el texto sellado y no una bandera legible`() = runTest {
        repository.markPending().getOrThrow()

        val xml = prefsXml()
        assertTrue("debe haber algo guardado", xml.contains("v1"))
        // Una bandera en claro sería un booleano o un texto legible: aquí solo base64.
        assertFalse(xml.contains("boolean"))
        assertFalse(xml.contains("true"))
        assertFalse(xml.contains("pending", ignoreCase = true))
        assertFalse(xml.contains("tutorial", ignoreCase = true))
        assertFalse(xml.contains("tour", ignoreCase = true))
    }

    @Test
    fun `consumir deja el archivo vacio`() = runTest {
        repository.markPending().getOrThrow()
        repository.consumePending().getOrThrow()

        assertFalse(prefsXml().contains("v1"))
    }

    @Test
    fun `borrar la cuenta retira la marca pendiente`() = runTest {
        repository.markPending().getOrThrow()

        HomeTourEraser(repository).erase()

        assertEquals(false, repository.consumePending().getOrThrow())
        assertFalse(prefsXml().contains("v1"))
    }

    @Test
    fun `descartar una activacion sin terminar tambien la retira`() {
        // Sin exención: a esa cuenta nadie la estrenó (ver KDoc del eraser).
        assertFalse(HomeTourEraser(repository).keptOnUnfinishedActivation)
    }

    @Test
    fun `si la llave no existe marcar falla y no deja nada a medias`() = runTest {
        val noKey = SealedHomeTourRepository(context, FailingCipher())

        assertTrue(noKey.markPending().isFailure)
        assertFalse(prefsXml().contains("v1"))
    }

    @Test
    fun `una marca que no abre se descarta y cuenta como no pendiente`() = runTest {
        repository.markPending().getOrThrow()
        // Otra cuenta o un dato adulterado: el mismo contenido con otro cifrador que no abre.
        val otherAccount = SealedHomeTourRepository(context, FailingCipher(sealWorks = true))

        assertTrue(otherAccount.consumePending().isFailure)
        // Y no se queda atascada: la siguiente lectura ya no la encuentra.
        assertEquals(false, repository.consumePending().getOrThrow())
    }

    @Test
    fun `una marca con otro contenido no cuenta como pendiente`() = runTest {
        val spy = RecordingCipher()
        val tampered = SealedHomeTourRepository(context, spy)
        tampered.markPending().getOrThrow()
        spy.replaceWithOtherContent = true

        assertEquals(false, tampered.consumePending().getOrThrow())
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

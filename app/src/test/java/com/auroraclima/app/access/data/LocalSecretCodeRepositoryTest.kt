package com.auroraclima.app.access.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.model.StoredCodeHash
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pruebas de la implementación real de la capa data de access (P-13): hasta hoy
 * solo el fake cubría el contrato. Robolectric provee SharedPreferences reales
 * en la JVM; el Keystore no existe aquí, por eso el hasher se prueba aparte,
 * instrumentado (KeystoreSecretCodeHasherTest en androidTest).
 */
@RunWith(RobolectricTestRunner::class)
class LocalSecretCodeRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = LocalSecretCodeRepository(context)

    @Test
    fun `round trip guarda y recupera salt y hash intactos`() = runTest {
        val stored = StoredCodeHash(salt = ByteArray(16) { it.toByte() }, hash = ByteArray(32) { (it * 3).toByte() })

        repository.saveCode(CodeSlot.USER, stored)
        val read = repository.storedCode(CodeSlot.USER)

        assertNotNull(read)
        assertArrayEquals(stored.salt, read!!.salt)
        assertArrayEquals(stored.hash, read.hash)
    }

    @Test
    fun `cada ranura guarda su propio codigo sin pisarse`() = runTest {
        val activation = StoredCodeHash(salt = ByteArray(16) { 1 }, hash = ByteArray(32) { 2 })
        val user = StoredCodeHash(salt = ByteArray(16) { 3 }, hash = ByteArray(32) { 4 })

        repository.saveCode(CodeSlot.ACTIVATION, activation)
        repository.saveCode(CodeSlot.USER, user)

        assertArrayEquals(activation.hash, repository.storedCode(CodeSlot.ACTIVATION)!!.hash)
        assertArrayEquals(user.hash, repository.storedCode(CodeSlot.USER)!!.hash)
    }

    @Test
    fun `sin prefs guardadas devuelve null`() = runTest {
        assertNull(repository.storedCode(CodeSlot.USER))
        assertNull(repository.storedCode(CodeSlot.ACTIVATION))
    }

    @Test
    fun `un dato parcial devuelve null`() = runTest {
        // Solo el salt presente (escritura interrumpida): código inexistente
        preferences().edit().putString("i1_s", "AAAA").commit()

        assertNull(repository.storedCode(CodeSlot.USER))
    }

    @Test
    fun `base64 corrupto devuelve null en vez de lanzar`() = runTest {
        // El fallo jamás cruza hacia la fachada (§3 de ARQUITECTURA.md)
        preferences().edit()
            .putString("i1_s", "%%%esto-no-es-base64%%%")
            .putString("i1_h", "tampoco esto")
            .commit()

        assertNull(repository.storedCode(CodeSlot.USER))
    }

    /** Mismo archivo y claves neutras que usa la implementación (RNF-05). */
    private fun preferences() = context.getSharedPreferences("search_index", Context.MODE_PRIVATE)
}

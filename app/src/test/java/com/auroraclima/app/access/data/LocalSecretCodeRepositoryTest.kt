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
        // Escritura interrumpida: falta una de las dos claves. Se siembra vía
        // saveCode y se borra lo que la implementación escribió de verdad, para
        // que un renombre de archivo o claves rompa el test en vez de dejarlo
        // pasar vacuamente (auditoría D6 M-2).
        repository.saveCode(CodeSlot.USER, StoredCodeHash(salt = ByteArray(16) { 1 }, hash = ByteArray(32) { 2 }))
        val removed = preferences().edit().remove(preferences().all.keys.first()).commit()
        require(removed)

        assertNull(repository.storedCode(CodeSlot.USER))
    }

    @Test
    fun `base64 corrupto devuelve null en vez de lanzar`() = runTest {
        // El fallo jamás cruza hacia la fachada (§3 de ARQUITECTURA.md). La
        // corrupción sobrescribe las claves realmente escritas (D6 M-2).
        repository.saveCode(CodeSlot.USER, StoredCodeHash(salt = ByteArray(16) { 1 }, hash = ByteArray(32) { 2 }))
        val editor = preferences().edit()
        preferences().all.keys.forEach { editor.putString(it, "%%%esto-no-es-base64%%%") }
        require(editor.commit())

        assertNull(repository.storedCode(CodeSlot.USER))
    }

    /** Mismo archivo y claves neutras que usa la implementación (RNF-05). */
    private fun preferences() = context.getSharedPreferences("search_index", Context.MODE_PRIVATE)
}

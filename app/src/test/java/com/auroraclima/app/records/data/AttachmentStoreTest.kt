package com.auroraclima.app.records.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.MediaCipher
import com.auroraclima.app.records.domain.model.AttachmentNotSavedException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verificación tras sellar (v0.3.2, defecto del Keystore de Android 8.0): el
 * almacén vuelve a abrir el archivo completo y lo compara con lo escrito. Si no
 * coincide, el archivo se elimina y se lanza [AttachmentNotSavedException]. En
 * ningún momento aparece una copia en claro en disco.
 */
@RunWith(RobolectricTestRunner::class)
class AttachmentStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mediaDir = File(context.filesDir, "media_store")
    private val content = ByteArray(200_000) { (it % 251).toByte() }

    @After
    fun tearDown() {
        mediaDir.deleteRecursively()
    }

    @Test
    fun `un archivo integro se guarda y se abre igual`() {
        val store = AttachmentStore(context, ReversibleMediaCipher())
        val ref = store.newRef()

        store.write(ref) { it.write(content) }

        assertArrayEquals(content, store.read(ref) { it.readBytes() })
    }

    @Test
    fun `si el sellado pierde bytes no queda archivo y se lanza el error propio`() {
        val store = AttachmentStore(context, TruncatingMediaCipher())
        val ref = store.newRef()

        assertThrows(AttachmentNotSavedException::class.java) { store.write(ref) { it.write(content) } }

        assertFalse("no queda un archivo truncado", File(mediaDir, ref).exists())
        assertEquals(0L, store.sizeOf(ref))
    }

    @Test
    fun `si el archivo sellado no abre no queda archivo y se lanza el error propio`() {
        // El caso de un sello GCM que no cuadra: la apertura lanza en vez de entregar menos.
        val unopenable = object : MediaCipher {
            private val reversible = ReversibleMediaCipher()

            override fun sealing(target: OutputStream): OutputStream = reversible.sealing(target)

            override fun opening(source: InputStream): InputStream = throw IOException()
        }
        val store = AttachmentStore(context, unopenable)
        val ref = store.newRef()

        val error = assertThrows(AttachmentNotSavedException::class.java) { store.write(ref) { it.write(content) } }

        assertTrue(error.cause is IOException)
        assertFalse("no queda el archivo sellado", File(mediaDir, ref).exists())
    }

    @Test
    fun `un fallo de quien escribe sigue siendo un error generico`() {
        val store = AttachmentStore(context, ReversibleMediaCipher())
        val ref = store.newRef()

        val error = assertThrows(IOException::class.java) {
            store.write(ref) {
                it.write(content, 0, 10)
                throw IOException()
            }
        }

        assertFalse(error is AttachmentNotSavedException)
        assertFalse(File(mediaDir, ref).exists())
    }

    @Test
    fun `un Error de quien escribe tampoco deja un archivo a medias`() {
        // Un OutOfMemoryError al comprimir una imagen: cerrar el stream sellaría lo parcial.
        val store = AttachmentStore(context, ReversibleMediaCipher())
        val ref = store.newRef()

        assertThrows(OutOfMemoryError::class.java) {
            store.write(ref) {
                it.write(content, 0, 1000)
                throw OutOfMemoryError()
            }
        }

        assertFalse(File(mediaDir, ref).exists())
    }

    @Test
    fun `la verificacion no deja copias en claro en disco`() {
        val before = appFiles()
        val store = AttachmentStore(context, ReversibleMediaCipher())
        val ref = store.newRef()

        store.write(ref) { it.write(content) }

        val after = appFiles()
        assertEquals("solo aparece el archivo cifrado", setOf(File(mediaDir, ref).path), after - before)
        assertTrue(after.none { File(it).readBytes().contentEquals(content) })
    }

    /** Todo archivo de la app: almacenamiento interno completo y caché. */
    private fun appFiles(): Set<String> = listOfNotNull(context.filesDir.parentFile, context.cacheDir)
        .flatMap { root -> root.walkTopDown().filter { it.isFile }.map { it.path }.toList() }
        .toSet()
}

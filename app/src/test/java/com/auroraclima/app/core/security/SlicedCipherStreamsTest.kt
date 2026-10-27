package com.auroraclima.app.core.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Trozos acotados hacia el cifrador (v0.3.2): el defecto del Keystore de Android
 * 8.0 y 8.1 solo se ve en esos emuladores, así que el tope de
 * [KEYSTORE_SLICE_BYTES] por llamada se fija aquí, en la JVM y con cualquier
 * versión. La ida y vuelta usa AES-GCM real del proveedor de la JVM.
 */
class SlicedCipherStreamsTest {
    private val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
    private val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }

    private fun cipher(mode: Int): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, key, GCMParameterSpec(128, nonce)) }

    private fun randomBytes(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    /** Destino que anota el tamaño de cada escritura que le llega. */
    private class RecordingOutputStream : OutputStream() {
        val sizes = mutableListOf<Int>()
        val bytes = ByteArrayOutputStream()

        override fun write(b: Int) {
            sizes += 1
            bytes.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            sizes += len
            bytes.write(b, off, len)
        }
    }

    /** Fuente que anota cuánto se le pide en cada lectura. */
    private class RecordingInputStream(source: InputStream) : FilterInputStream(source) {
        val requested = mutableListOf<Int>()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            requested += len
            return super.read(b, off, len)
        }
    }

    @Test
    fun `una escritura grande llega al destino en trozos de a lo sumo 32 KB`() {
        val content = randomBytes(1024 * 1024)
        val target = RecordingOutputStream()

        slicedSealing(target).use { it.write(content) }

        assertTrue("ningún trozo pasa del tope", target.sizes.all { it <= KEYSTORE_SLICE_BYTES })
        assertArrayEquals(content, target.bytes.toByteArray())
    }

    @Test
    fun `las escrituras pequenas se juntan en trozos de 32 KB`() {
        val content = randomBytes(256 * 1024)
        val target = RecordingOutputStream()

        slicedSealing(target).use { stream ->
            content.toList().chunked(8 * 1024).forEach { stream.write(it.toByteArray()) }
        }

        assertEquals(List(8) { KEYSTORE_SLICE_BYTES }, target.sizes)
        assertArrayEquals(content, target.bytes.toByteArray())
    }

    @Test
    fun `una escritura de tamano raro tampoco pasa del tope`() {
        val content = randomBytes(3 * KEYSTORE_SLICE_BYTES + 17)
        val target = RecordingOutputStream()

        slicedSealing(target).use { stream ->
            stream.write(content, 0, 5)
            stream.write(content, 5, content.size - 5)
        }

        assertTrue(target.sizes.all { it <= KEYSTORE_SLICE_BYTES })
        assertArrayEquals(content, target.bytes.toByteArray())
    }

    @Test
    fun `ida y vuelta con AES GCM real en varios tamanos`() {
        for (size in listOf(0, 1, 64 * 1024, 1024 * 1024 + 3)) {
            val content = randomBytes(size)
            val sealed = ByteArrayOutputStream()
            slicedSealing(CipherOutputStream(sealed, cipher(Cipher.ENCRYPT_MODE))).use { it.write(content) }

            val source = RecordingInputStream(ByteArrayInputStream(sealed.toByteArray()))
            val opened = SlicedCipherInputStream(source, cipher(Cipher.DECRYPT_MODE)).use { it.readBytes() }

            assertArrayEquals("ida y vuelta con $size bytes", content, opened)
            assertTrue("nunca pide más del tope", source.requested.all { it <= KEYSTORE_SLICE_BYTES })
        }
    }

    @Test
    fun `leer de a un byte y pedir cero bytes funcionan`() {
        val content = randomBytes(100)
        val sealed = ByteArrayOutputStream()
        slicedSealing(CipherOutputStream(sealed, cipher(Cipher.ENCRYPT_MODE))).use { it.write(content) }

        SlicedCipherInputStream(ByteArrayInputStream(sealed.toByteArray()), cipher(Cipher.DECRYPT_MODE)).use { stream ->
            assertEquals(0, stream.read(ByteArray(4), 0, 0))
            val first = stream.read()
            assertEquals(content[0].toInt() and 0xFF, first)
            val rest = stream.readBytes()
            assertArrayEquals(content.copyOfRange(1, content.size), rest)
            assertEquals(-1, stream.read())
        }
    }

    @Test
    fun `un sellado alterado lanza IOException al abrir`() {
        val sealed = ByteArrayOutputStream()
        slicedSealing(CipherOutputStream(sealed, cipher(Cipher.ENCRYPT_MODE))).use { it.write(randomBytes(70_000)) }
        val tampered = sealed.toByteArray().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }

        assertThrows(IOException::class.java) {
            SlicedCipherInputStream(ByteArrayInputStream(tampered), cipher(Cipher.DECRYPT_MODE)).use { it.readBytes() }
        }
    }

    @Test
    fun `cerrar antes del final no lanza y cierra la fuente`() {
        val sealed = ByteArrayOutputStream()
        slicedSealing(CipherOutputStream(sealed, cipher(Cipher.ENCRYPT_MODE))).use { it.write(randomBytes(100_000)) }
        var closed = false
        val source = object : FilterInputStream(ByteArrayInputStream(sealed.toByteArray())) {
            override fun close() {
                closed = true
                super.close()
            }
        }

        val stream = SlicedCipherInputStream(source, cipher(Cipher.DECRYPT_MODE))
        stream.read(ByteArray(10))
        stream.close()

        assertTrue(closed)
    }
}

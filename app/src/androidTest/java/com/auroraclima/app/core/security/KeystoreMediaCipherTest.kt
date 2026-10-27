package com.auroraclima.app.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cifrado por stream de adjuntos con la llave real del Android Keystore
 * (RF-33, RNF-01). Instrumentado por la misma razón que KeystorePayloadCipherTest:
 * la JVM del host no provee el proveedor «AndroidKeyStore».
 *
 * Desde v0.3.2 cubre 64 KB, 256 KB, 1 MB y el tope de adjunto, con una sola
 * escritura grande y con escrituras pequeñas: el Keystore de Android 8.0
 * truncaba sin error lo que recibía de más de ~128 KB en una llamada. Correr
 * también en API 26 y 27.
 *
 * Ejecutar con `./gradlew connectedDebugAndroidTest` en un emulador.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreMediaCipherTest {
    private val cipher = KeystoreMediaCipher()

    private fun seal(plaintext: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        cipher.sealing(output).use { it.write(plaintext) }
        return output.toByteArray()
    }

    private fun open(sealed: ByteArray): ByteArray = cipher.opening(ByteArrayInputStream(sealed)).use { it.readBytes() }

    @Test
    fun sealAndOpenRoundTripsByStream() {
        // Varios MB, como un adjunto real: el contrato es por stream.
        val plaintext = ByteArray(3 * 1024 * 1024).also { SecureRandom().nextBytes(it) }

        val sealed = seal(plaintext)
        assertArrayEquals(plaintext, open(sealed))
    }

    @Test
    fun everySizeRoundTripsWithOneLargeWrite() {
        // v0.3.2: en Android 8.0 una sola escritura de más de ~128 KB llegaba
        // entera al Keystore y el sellado quedaba truncado sin error.
        for (size in SIZES) {
            val plaintext = ByteArray(size).also { SecureRandom().nextBytes(it) }

            val sealed = seal(plaintext)

            assertEquals("formato [nonce][sellado][tag] con $size bytes", size + OVERHEAD_BYTES, sealed.size)
            assertArrayEquals("se abre completo con $size bytes", plaintext, open(sealed))
        }
    }

    @Test
    fun everySizeRoundTripsWithSmallWrites() {
        // Así escribe la app: la copia de documentos va de 8 KB en 8 KB.
        for (size in SIZES) {
            val plaintext = ByteArray(size).also { SecureRandom().nextBytes(it) }
            val output = ByteArrayOutputStream()
            cipher.sealing(output).use { stream ->
                var offset = 0
                while (offset < size) {
                    val count = minOf(SMALL_WRITE_BYTES, size - offset)
                    stream.write(plaintext, offset, count)
                    offset += count
                }
            }

            assertArrayEquals("se abre completo con $size bytes", plaintext, open(output.toByteArray()))
        }
    }

    @Test
    fun maxAttachmentSizeRoundTripsFileToFile() {
        // El tope de adjunto, de archivo a archivo y en escrituras de 1 MB: el
        // contenido nunca está entero en memoria de la prueba.
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val sealedFile = File(directory, "media_cipher_test")
        try {
            val size = AttachmentPolicy.MAX_DOCUMENT_BYTES.toInt()
            val chunk = ByteArray(LARGE_WRITE_BYTES)
            val random = SecureRandom()
            val written = MessageDigest.getInstance("SHA-256")
            sealedFile.outputStream().use { raw ->
                cipher.sealing(raw).use { stream ->
                    var left = size
                    while (left > 0) {
                        val count = minOf(chunk.size, left)
                        random.nextBytes(chunk)
                        written.update(chunk, 0, count)
                        stream.write(chunk, 0, count)
                        left -= count
                    }
                }
            }
            assertEquals(size.toLong() + OVERHEAD_BYTES, sealedFile.length())

            val opened = MessageDigest.getInstance("SHA-256")
            var total = 0L
            sealedFile.inputStream().use { raw ->
                cipher.opening(raw).use { stream ->
                    while (true) {
                        val count = stream.read(chunk)
                        if (count < 0) break
                        opened.update(chunk, 0, count)
                        total += count
                    }
                }
            }
            assertEquals(size.toLong(), total)
            assertTrue("mismo contenido al abrir", MessageDigest.isEqual(written.digest(), opened.digest()))
        } finally {
            sealedFile.delete()
        }
    }

    @Test
    fun sealedBytesDoNotContainPlaintext() {
        val marker = "contenido sensible del adjunto".toByteArray()
        val sealed = seal(marker)

        assertFalse(sealed.toList().windowed(marker.size).any { it == marker.toList() })
    }

    @Test
    fun eachSealUsesAFreshNonce() {
        val plaintext = "mismo contenido".toByteArray()

        val first = seal(plaintext)
        val second = seal(plaintext)

        // Los primeros 12 bytes son el nonce: jamás deben repetirse.
        assertFalse(first.copyOfRange(0, 12).contentEquals(second.copyOfRange(0, 12)))
    }

    @Test
    fun openingSurvivesNewCipherInstance() {
        // La llave persiste en el Keystore: otra instancia descifra lo sellado.
        val plaintext = "persistencia entre instancias".toByteArray()
        val sealed = seal(plaintext)

        val other = KeystoreMediaCipher()
        val opened = other.opening(ByteArrayInputStream(sealed)).use { it.readBytes() }

        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun tamperedStreamFailsToOpen() {
        val sealed = seal("integridad".toByteArray())
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()

        // GCM autentica: un byte alterado debe impedir la apertura completa.
        assertThrows(IOException::class.java) { open(sealed) }
    }

    @Test
    fun truncatedHeaderFailsToOpen() {
        assertThrows(Exception::class.java) { open(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun truncatedBodyFailsToOpen() {
        // Lo que dejaba el defecto de Android 8.0: un sellado sin parte del cuerpo.
        val sealed = seal(ByteArray(256 * 1024).also { SecureRandom().nextBytes(it) })
        val truncated = sealed.copyOfRange(0, sealed.size / 2)

        assertThrows(IOException::class.java) { open(truncated) }
    }

    private companion object {
        val SIZES = listOf(64 * 1024, 256 * 1024, 1024 * 1024)
        const val OVERHEAD_BYTES = 12 + 16
        const val SMALL_WRITE_BYTES = 8 * 1024
        const val LARGE_WRITE_BYTES = 1024 * 1024
    }
}

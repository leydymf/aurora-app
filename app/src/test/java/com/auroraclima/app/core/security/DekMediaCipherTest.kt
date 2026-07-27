package com.auroraclima.app.core.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cifrado por stream del viaje de evidencias con la DEK recuperable (RF-39):
 * `javax.crypto` puro, así que se prueba en JVM con cripto real (RNF-01) —
 * a diferencia del cifrador local de Keystore, que va instrumentado.
 */
class DekMediaCipherTest {
    private val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val cipher = DekMediaCipher(FakeSyncKeyManager(dek = dek))

    private fun sealed(content: ByteArray, with: DekMediaCipher = cipher): ByteArray {
        val out = ByteArrayOutputStream()
        with.sealing(out).use { it.write(content) }
        return out.toByteArray()
    }

    private fun opened(blob: ByteArray, with: DekMediaCipher = cipher): ByteArray =
        with.opening(ByteArrayInputStream(blob)).use { it.readBytes() }

    @Test
    fun `sellar y abrir por stream recupera el contenido`() {
        // Tamaño de varios trozos (1 MB): el contenido nunca pasa completo por
        // memoria en producción, pero el round-trip debe ser exacto byte a byte.
        val content = ByteArray(1_048_576).also { SecureRandom().nextBytes(it) }

        assertArrayEquals(content, opened(sealed(content)))
    }

    @Test
    fun `el sellado no contiene el contenido en claro`() {
        val content = "cabecera reconocible %PDF-1.7".toByteArray(Charsets.UTF_8)

        val blob = sealed(content)

        // Ni al inicio (tras el nonce) ni en ninguna posición.
        assertFalse(blob.toList().windowed(content.size).any { it.toByteArray().contentEquals(content) })
    }

    @Test
    fun `cada sellado usa un nonce fresco`() {
        val content = "mismo contenido".toByteArray(Charsets.UTF_8)

        val first = sealed(content)
        val second = sealed(content)

        assertFalse(first.copyOfRange(0, 12).contentEquals(second.copyOfRange(0, 12)))
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `otra instancia con la misma DEK abre lo sellado`() {
        // La propiedad de RF-39: lo subido por el dispositivo A se abre en el B
        // que restauró la DEK.
        val content = "evidencia".toByteArray(Charsets.UTF_8)
        val blob = sealed(content)

        val other = DekMediaCipher(FakeSyncKeyManager(dek = dek.copyOf()))

        assertArrayEquals(content, opened(blob, with = other))
    }

    @Test
    fun `una DEK distinta no abre`() {
        val blob = sealed("x".toByteArray(Charsets.UTF_8))
        val stranger = DekMediaCipher(
            FakeSyncKeyManager(dek = ByteArray(32).also { SecureRandom().nextBytes(it) })
        )

        assertTrue(runCatching { opened(blob, with = stranger) }.isFailure)
    }

    @Test
    fun `un blob truncado no abre`() {
        val blob = sealed("contenido completo".toByteArray(Charsets.UTF_8))

        assertTrue(runCatching { opened(blob.copyOfRange(0, blob.size - 4)) }.isFailure)
    }

    @Test
    fun `sin DEK establecida lanza`() {
        val without = DekMediaCipher(FakeSyncKeyManager(dek = null))

        assertTrue(runCatching { sealed("x".toByteArray(Charsets.UTF_8), with = without) }.isFailure)
        assertTrue(runCatching { opened(sealed("x".toByteArray(Charsets.UTF_8)), with = without) }.isFailure)
    }
}

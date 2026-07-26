package com.auroraclima.app.core.security

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cifrador del payload con la DEK recuperable (RF-39): a diferencia del legado
 * de Keystore, la llave viene del gestor y el cifrado es `javax.crypto` puro —
 * por eso SÍ se prueba en JVM con cripto real (RNF-01).
 */
class DekPayloadCipherTest {
    private val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private val keyManager = FakeSyncKeyManager(dek = dek)
    private val cipher = DekPayloadCipher(keyManager)

    @Test
    fun `sellar y abrir con la misma DEK recupera el contenido`() {
        val plaintext = "Bucaramanga 24°C, lluvia ligera".toByteArray(Charsets.UTF_8)

        assertArrayEquals(plaintext, cipher.open(cipher.seal(plaintext)))
    }

    @Test
    fun `el sellado no contiene el contenido en claro`() {
        val plaintext = "Bucaramanga 24°C, lluvia ligera".toByteArray(Charsets.UTF_8)

        assertFalse(cipher.seal(plaintext).ciphertext.contentEquals(plaintext))
    }

    @Test
    fun `cada sellado usa un nonce fresco`() {
        val plaintext = "cielo despejado".toByteArray(Charsets.UTF_8)

        val first = cipher.seal(plaintext)
        val second = cipher.seal(plaintext)

        assertFalse(first.iv.contentEquals(second.iv))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
    }

    @Test
    fun `otra instancia con la misma DEK abre lo sellado`() {
        // Es la propiedad que hace posible RF-39: la DEK restaurada en un
        // dispositivo nuevo abre lo que selló el dispositivo original.
        val plaintext = "cielo despejado".toByteArray(Charsets.UTF_8)
        val sealed = cipher.seal(plaintext)

        val other = DekPayloadCipher(FakeSyncKeyManager(dek = dek.copyOf()))

        assertArrayEquals(plaintext, other.open(sealed))
    }

    @Test
    fun `una DEK distinta no abre`() {
        val sealed = cipher.seal("x".toByteArray(Charsets.UTF_8))
        val stranger = DekPayloadCipher(
            FakeSyncKeyManager(dek = ByteArray(32).also { SecureRandom().nextBytes(it) })
        )

        assertTrue(runCatching { stranger.open(sealed) }.isFailure)
    }

    @Test
    fun `sin DEK establecida lanza y el push queda pendiente`() {
        // §3: el repositorio traduce a Result; el reconciliador re-sube después.
        val without = DekPayloadCipher(FakeSyncKeyManager(dek = null))

        assertTrue(runCatching { without.seal("x".toByteArray(Charsets.UTF_8)) }.isFailure)
        assertTrue(runCatching { without.open(cipher.seal("x".toByteArray(Charsets.UTF_8))) }.isFailure)
    }
}

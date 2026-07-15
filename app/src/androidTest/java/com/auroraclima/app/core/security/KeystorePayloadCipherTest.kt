package com.auroraclima.app.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas del cifrador real del payload de sync con el Android Keystore (RF-37,
 * RNF-01). Instrumentadas y no Robolectric: la JVM del host no provee el
 * proveedor «AndroidKeyStore» (mismo motivo que `KeystoreSecretCodeHasherTest`).
 */
@RunWith(AndroidJUnit4::class)
class KeystorePayloadCipherTest {
    private val cipher = KeystorePayloadCipher()

    @Test
    fun sealThenOpenRoundTrips() {
        val plaintext = "Bucaramanga 24°C, lluvia ligera".toByteArray(Charsets.UTF_8)

        val sealed = cipher.seal(plaintext)

        assertArrayEquals(plaintext, cipher.open(sealed))
    }

    @Test
    fun ciphertextIsNotPlaintext() {
        // RNF-01: lo que sale del dispositivo no expone el contenido en claro.
        val plaintext = "Bucaramanga 24°C, lluvia ligera".toByteArray(Charsets.UTF_8)

        val sealed = cipher.seal(plaintext)

        assertFalse(sealed.ciphertext.contentEquals(plaintext))
    }

    @Test
    fun eachSealUsesAFreshNonce() {
        // AES-GCM exige nonce único por operación: dos sellados difieren en iv y ciphertext.
        val plaintext = "cielo despejado".toByteArray(Charsets.UTF_8)

        val first = cipher.seal(plaintext)
        val second = cipher.seal(plaintext)

        assertFalse(first.iv.contentEquals(second.iv))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
    }

    @Test
    fun keyIsStableAcrossInstances() {
        // La llave vive en el Keystore, no en la instancia: otra sesión abre lo sellado.
        val plaintext = "cielo despejado".toByteArray(Charsets.UTF_8)
        val sealed = cipher.seal(plaintext)

        assertArrayEquals(plaintext, KeystorePayloadCipher().open(sealed))
    }

    @Test
    fun openWithWrongNonceFails() {
        val sealed = cipher.seal("x".toByteArray(Charsets.UTF_8))
        val tampered = SealedPayload(ciphertext = sealed.ciphertext, iv = ByteArray(sealed.iv.size))

        var failed = false
        try {
            cipher.open(tampered)
        } catch (expected: Exception) {
            failed = true
        }
        assertEquals(true, failed)
    }
}

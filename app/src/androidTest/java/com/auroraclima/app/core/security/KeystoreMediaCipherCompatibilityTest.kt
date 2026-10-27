package com.auroraclima.app.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El formato de los adjuntos no cambió en v0.3.2: lo sellado por v0.3.1 se sigue
 * abriendo y lo sellado ahora lo abre la lectura de v0.3.1.
 *
 * La llave del Keystore no se puede exportar, así que ningún archivo de otro
 * teléfono sirve de muestra. La prueba reproduce aquí, con la MISMA llave (alias
 * `local_media_key`), el código exacto de v0.3.1 —`CipherOutputStream` y
 * `CipherInputStream` sobre `[nonce de 12][sellado GCM]`— y lo cruza con el
 * nuevo. Las escrituras de v0.3.1 van de 8 KB en 8 KB, como las hacía la app.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreMediaCipherCompatibilityTest {
    private val cipher = KeystoreMediaCipher()

    @Test
    fun loSelladoPorV031SeSigueAbriendo() {
        for (size in SIZES) {
            val plaintext = ByteArray(size).also { SecureRandom().nextBytes(it) }

            val sealedByV031 = sealLikeV031(plaintext)
            val opened = cipher.opening(ByteArrayInputStream(sealedByV031)).use { it.readBytes() }

            assertArrayEquals("lo sellado por v0.3.1 abre con $size bytes", plaintext, opened)
        }
    }

    @Test
    fun loSelladoAhoraLoAbreLaLecturaDeV031() {
        for (size in SIZES) {
            val plaintext = ByteArray(size).also { SecureRandom().nextBytes(it) }
            val output = ByteArrayOutputStream()
            cipher.sealing(output).use { it.write(plaintext) }

            val opened = openLikeV031(output.toByteArray())

            assertArrayEquals("v0.3.1 abre lo sellado ahora con $size bytes", plaintext, opened)
        }
    }

    /** `KeystoreMediaCipher.sealing` de v0.3.1, alimentado como lo hacía la app. */
    private fun sealLikeV031(plaintext: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val legacy = Cipher.getInstance(TRANSFORMATION)
        legacy.init(Cipher.ENCRYPT_MODE, mediaKey())
        output.write(legacy.iv)
        CipherOutputStream(output, legacy).use { stream ->
            var offset = 0
            while (offset < plaintext.size) {
                val count = minOf(APP_WRITE_BYTES, plaintext.size - offset)
                stream.write(plaintext, offset, count)
                offset += count
            }
        }
        return output.toByteArray()
    }

    /** `KeystoreMediaCipher.opening` de v0.3.1. */
    private fun openLikeV031(sealed: ByteArray): ByteArray {
        val source = ByteArrayInputStream(sealed)
        val iv = ByteArray(IV_SIZE_BYTES).also { source.read(it) }
        val legacy = Cipher.getInstance(TRANSFORMATION)
        legacy.init(Cipher.DECRYPT_MODE, mediaKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return CipherInputStream(source, legacy).use { it.readBytes() }
    }

    /** La llave de adjuntos de la app; sellar una vez garantiza que exista. */
    private fun mediaKey(): SecretKey {
        cipher.sealing(ByteArrayOutputStream()).close()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private companion object {
        val SIZES = listOf(64 * 1024, 1024 * 1024)
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "local_media_key"
        const val IV_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
        const val APP_WRITE_BYTES = 8 * 1024
    }
}

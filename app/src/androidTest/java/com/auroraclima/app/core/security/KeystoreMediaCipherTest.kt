package com.auroraclima.app.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cifrado por stream de adjuntos con la llave real del Android Keystore
 * (RF-33, RNF-01). Instrumentado por la misma razón que KeystorePayloadCipherTest:
 * la JVM del host no provee el proveedor «AndroidKeyStore».
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
}

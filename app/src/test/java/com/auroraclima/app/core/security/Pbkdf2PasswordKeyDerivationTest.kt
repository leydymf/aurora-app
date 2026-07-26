package com.auroraclima.app.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Vectores conocidos de PBKDF2-HMAC-SHA256 (RF-39): los publicados con la
 * especificación (password/salt/c/dkLen=32) que toda implementación debe
 * reproducir byte a byte. Corren en JVM: `javax.crypto` es de la plataforma
 * Java, no del Keystore. Los conteos bajos de los vectores mantienen la suite
 * rápida; el parámetro real (600k) es un dato del sobre, no de esta clase.
 */
class Pbkdf2PasswordKeyDerivationTest {
    private val derivation = Pbkdf2PasswordKeyDerivation()

    @Test
    fun `vector conocido con una iteracion`() {
        val derived = derivation.derive("password", "salt".toByteArray(Charsets.UTF_8), 1)

        assertArrayEquals(
            hex("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b"),
            derived
        )
    }

    @Test
    fun `vector conocido con dos iteraciones`() {
        val derived = derivation.derive("password", "salt".toByteArray(Charsets.UTF_8), 2)

        assertArrayEquals(
            hex("ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43"),
            derived
        )
    }

    @Test
    fun `vector conocido con 4096 iteraciones`() {
        val derived = derivation.derive("password", "salt".toByteArray(Charsets.UTF_8), 4096)

        assertArrayEquals(
            hex("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a"),
            derived
        )
    }

    @Test
    fun `es determinista y sensible a cada parametro`() {
        val salt = "salt".toByteArray(Charsets.UTF_8)
        val base = derivation.derive("password", salt, 2)

        // Misma entrada, misma llave (requisito para reabrir el sobre publicado)…
        assertArrayEquals(base, derivation.derive("password", salt, 2))
        // …y cualquier cambio de contraseña, salt o iteraciones da otra llave.
        assertFalse(base.contentEquals(derivation.derive("passwore", salt, 2)))
        assertFalse(base.contentEquals(derivation.derive("password", "sal7".toByteArray(Charsets.UTF_8), 2)))
        assertFalse(base.contentEquals(derivation.derive("password", salt, 3)))
    }

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

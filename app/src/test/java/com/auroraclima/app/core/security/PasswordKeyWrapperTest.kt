package com.auroraclima.app.core.security

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sobre de la DEK (RF-39): envoltura y desenvoltura con la KEK derivada de la
 * contraseña. Cripto real (PBKDF2 + AES-GCM de la JVM) con iteraciones bajas
 * para que la suite no pague el costo de las 600k de producción — el conteo es
 * un parámetro del sobre y su valor real lo fija quien lo provee.
 */
class PasswordKeyWrapperTest {
    private val wrapper = PasswordKeyWrapper(Pbkdf2PasswordKeyDerivation(), iterations = TEST_ITERATIONS)

    private val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Test
    fun `envolver y desenvolver con la misma contrasena recupera la DEK`() {
        val envelope = wrapper.wrap(dek, "brisa-serena-04")

        assertArrayEquals(dek, wrapper.unwrap(envelope, "brisa-serena-04"))
    }

    @Test
    fun `el sobre no contiene la DEK en claro`() {
        val envelope = wrapper.wrap(dek, "brisa-serena-04")

        assertFalse(envelope.ciphertext.contentEquals(dek))
    }

    @Test
    fun `contrasena incorrecta no abre el sobre`() {
        val envelope = wrapper.wrap(dek, "brisa-serena-04")

        assertTrue(failsToUnwrap(envelope, "brisa-serena-05"))
    }

    @Test
    fun `blob corrupto no abre`() {
        val envelope = wrapper.wrap(dek, "brisa-serena-04")
        val corrupted = envelope.copy(
            ciphertext = envelope.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        )

        assertTrue(failsToUnwrap(corrupted, "brisa-serena-04"))
    }

    @Test
    fun `nonce corrupto no abre`() {
        val envelope = wrapper.wrap(dek, "brisa-serena-04")
        val corrupted = envelope.copy(nonce = ByteArray(envelope.nonce.size))

        assertTrue(failsToUnwrap(corrupted, "brisa-serena-04"))
    }

    @Test
    fun `salt corrupto no abre`() {
        // El salt cambia la KEK derivada: el tag GCM deja de verificar.
        val envelope = wrapper.wrap(dek, "brisa-serena-04")
        val corrupted = envelope.copy(salt = ByteArray(envelope.salt.size))

        assertTrue(failsToUnwrap(corrupted, "brisa-serena-04"))
    }

    @Test
    fun `cada envoltura usa salt y nonce frescos`() {
        val first = wrapper.wrap(dek, "brisa-serena-04")
        val second = wrapper.wrap(dek, "brisa-serena-04")

        assertFalse(first.salt.contentEquals(second.salt))
        assertFalse(first.nonce.contentEquals(second.nonce))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
        // Ambos sobres siguen abriendo la misma DEK.
        assertArrayEquals(dek, wrapper.unwrap(first, "brisa-serena-04"))
        assertArrayEquals(dek, wrapper.unwrap(second, "brisa-serena-04"))
    }

    @Test
    fun `desenvolver respeta las iteraciones del sobre, no las propias`() {
        // Un sobre publicado con otro conteo (parámetro histórico o futuro) debe
        // seguir abriendo aunque el valor por defecto local haya cambiado.
        val published = PasswordKeyWrapper(Pbkdf2PasswordKeyDerivation(), iterations = TEST_ITERATIONS * 2)
            .wrap(dek, "brisa-serena-04")

        assertEquals(TEST_ITERATIONS * 2, published.iterations)
        assertArrayEquals(dek, wrapper.unwrap(published, "brisa-serena-04"))
    }

    private fun failsToUnwrap(envelope: WrappedKeyEnvelope, password: String): Boolean = try {
        wrapper.unwrap(envelope, password)
        false
    } catch (expected: Exception) {
        true
    }

    private companion object {
        const val TEST_ITERATIONS = 1_000
    }
}

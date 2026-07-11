package com.auroraclima.app.access.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas del hasher real con el Android Keystore (P-13). Instrumentadas y no
 * Robolectric: la JVM del host no provee el proveedor «AndroidKeyStore», así
 * que el HMAC con llave no exportable solo puede probarse en dispositivo.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretCodeHasherTest {
    private val hasher = KeystoreSecretCodeHasher()

    @Test
    fun sameSaltAndCodeAlwaysProduceSameHash() {
        val salt = hasher.newSalt()

        val first = hasher.hash(salt, "amanecer26")
        val second = hasher.hash(salt, "amanecer26")

        // Determinismo por (salt, código): sin él la validación de RF-12 sería imposible
        assertArrayEquals(first, second)
    }

    @Test
    fun hashIsStableAcrossInstances() {
        // La llave vive en el Keystore, no en la instancia: otro proceso u otra
        // sesión deben producir el mismo hash para el mismo (salt, código)
        val salt = hasher.newSalt()

        assertArrayEquals(
            hasher.hash(salt, "amanecer26"),
            KeystoreSecretCodeHasher().hash(salt, "amanecer26")
        )
    }

    @Test
    fun differentSaltsProduceDifferentHashes() {
        val saltA = hasher.newSalt()
        val saltB = hasher.newSalt()

        assertFalse(saltA.contentEquals(saltB))
        assertFalse(hasher.hash(saltA, "amanecer26").contentEquals(hasher.hash(saltB, "amanecer26")))
    }

    @Test
    fun differentCodesProduceDifferentHashes() {
        val salt = hasher.newSalt()

        assertFalse(hasher.hash(salt, "amanecer26").contentEquals(hasher.hash(salt, "atardecer26")))
    }

    @Test
    fun newSaltHasExpectedSize() {
        assertEquals(16, hasher.newSalt().size)
    }
}

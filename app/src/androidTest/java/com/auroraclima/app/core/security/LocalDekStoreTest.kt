package com.auroraclima.app.core.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Copia local de la DEK envuelta con el Keystore (RF-39, «sobre doble»).
 * Instrumentada y no Robolectric: la JVM del host no provee el proveedor
 * «AndroidKeyStore» (mismo motivo que `KeystorePayloadCipherTest`).
 */
@RunWith(AndroidJUnit4::class)
class LocalDekStoreTest {
    private val store = LocalDekStore(ApplicationProvider.getApplicationContext())

    private val dek = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @Before
    fun cleanSlate() {
        store.erase()
    }

    @Test
    fun writeThenReadRoundTripsWithGeneration() {
        store.write("uid-a", dek, generation = 3)

        val stored = store.read("uid-a")

        assertArrayEquals(dek, stored?.dek)
        assertEquals(3L, stored?.generation)
    }

    @Test
    fun readWithAnotherUidReturnsNull() {
        // Una cuenta nueva en el mismo teléfono jamás hereda la DEK de la anterior.
        store.write("uid-a", dek, generation = 1)

        assertNull(store.read("uid-b"))
    }

    @Test
    fun readWithoutStoredDekReturnsNull() {
        assertNull(store.read("uid-a"))
    }

    @Test
    fun writeReplacesPreviousCopy() {
        // Auto-reparación y cambio de contraseña re-publican: la copia local
        // debe quedar en la generación nueva, no acumular estados.
        store.write("uid-a", dek, generation = 1)
        val rotated = ByteArray(32).also { SecureRandom().nextBytes(it) }

        store.write("uid-a", rotated, generation = 2)

        val stored = store.read("uid-a")
        assertArrayEquals(rotated, stored?.dek)
        assertEquals(2L, stored?.generation)
    }

    @Test
    fun survivesNewInstance() {
        // La copia vive en prefs + Keystore, no en la instancia (proceso nuevo).
        store.write("uid-a", dek, generation = 5)

        val stored = LocalDekStore(ApplicationProvider.getApplicationContext()).read("uid-a")

        assertArrayEquals(dek, stored?.dek)
        assertEquals(5L, stored?.generation)
    }

    @Test
    fun eraseRemovesEverything() {
        // RF-20: tras la eliminación, el siguiente uso parte como instalación limpia.
        store.write("uid-a", dek, generation = 1)

        store.erase()

        assertNull(store.read("uid-a"))
    }
}

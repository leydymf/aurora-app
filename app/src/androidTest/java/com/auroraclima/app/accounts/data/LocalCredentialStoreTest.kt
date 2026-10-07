package com.auroraclima.app.accounts.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Blob de credenciales del reingreso silencioso (P-31 opción C). Instrumentada
 * y no Robolectric: la JVM del host no provee el proveedor «AndroidKeyStore»
 * (mismo motivo que `LocalDekStoreTest`).
 */
@RunWith(AndroidJUnit4::class)
class LocalCredentialStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = LocalCredentialStore(context)

    @Before
    fun cleanSlate() {
        store.erase()
    }

    @Test
    fun saveThenLoadRoundTrips() {
        store.save("usuaria@correo.co", "clave-segura-1")

        val stored = store.load()

        assertEquals("usuaria@correo.co", stored?.email)
        assertEquals("clave-segura-1", stored?.password)
    }

    @Test
    fun loadWithoutBlobReturnsNull() {
        assertNull(store.load())
        assertFalse(store.exists())
    }

    @Test
    fun existsReportsPresenceWithoutDecrypting() {
        store.save("usuaria@correo.co", "clave-segura-1")

        assertTrue(store.exists())
    }

    @Test
    fun saveReplacesPreviousCredentials() {
        // Cambio de contraseña (caso 2 del ciclo de vida): el blob se re-escribe.
        store.save("usuaria@correo.co", "clave-vieja-1")

        store.save("usuaria@correo.co", "clave-nueva-1")

        assertEquals("clave-nueva-1", store.load()?.password)
    }

    @Test
    fun eraseDestroysBlobAndReportsAbsence() {
        store.save("usuaria@correo.co", "clave-segura-1")

        store.erase()

        assertNull(store.load())
        assertFalse(store.exists())
    }

    @Test
    fun eraseIsIdempotent() {
        store.erase()
        store.erase()

        assertFalse(store.exists())
    }

    @Test
    fun survivesNewInstance() {
        // El blob vive en prefs + Keystore, no en la instancia (proceso nuevo).
        store.save("usuaria@correo.co", "clave-segura-1")

        val stored = LocalCredentialStore(context).load()

        assertEquals("usuaria@correo.co", stored?.email)
        assertEquals("clave-segura-1", stored?.password)
    }

    @Test
    fun tamperedBlobDegradesToAbsenceWithoutCrash() {
        // Divergencia deliberada del criterio de LocalDekStore: un blob que no
        // descifra se destruye y se opera como si no existiera — la ruta de
        // recuperación es el login manual, nunca un error visible.
        store.save("usuaria@correo.co", "clave-segura-1")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(BLOB_KEY, "QWR 1bHRlcmFkbw==")
            .commit()

        assertNull(store.load())
        assertFalse(store.exists())
    }

    @Test
    fun halfPresentBlobIsDestroyedDefensively() {
        // B-2: mitad presente por manipulación externa — exists() no debe
        // seguir prometiendo un blob que jamás abrirá.
        store.save("usuaria@correo.co", "clave-segura-1")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(IV_KEY)
            .commit()

        assertNull(store.load())
        assertFalse(store.exists())
    }

    @Test
    fun blobLengthDoesNotRevealCredentialLength() {
        // B-1: relleno a bloque fijo — credenciales de longitudes distintas
        // producen blobs del mismo tamaño en el XML.
        store.save("a@b.co", "corta-123")
        val shortBlob = storedBlobLength()

        store.save("usuaria.con.correo.largo@dominio-extenso.example.com", "una-contraseña-mucho-más-larga-9")
        val longBlob = storedBlobLength()

        assertEquals(shortBlob, longBlob)
    }

    private fun storedBlobLength(): Int = requireNotNull(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(BLOB_KEY, null)
    ).length

    @Test
    fun unparseableIvDegradesToAbsenceWithoutCrash() {
        store.save("usuaria@correo.co", "clave-segura-1")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(IV_KEY, "no-es-base64!!")
            .commit()

        assertNull(store.load())
        assertFalse(store.exists())
    }

    private companion object {
        // Réplica de las constantes privadas del store: si los nombres neutros
        // cambian, estos tests de adulteración deben cambiar con ellos.
        const val PREFS_NAME = "account_meta"
        const val IV_KEY = "a0"
        const val BLOB_KEY = "a1"
    }
}

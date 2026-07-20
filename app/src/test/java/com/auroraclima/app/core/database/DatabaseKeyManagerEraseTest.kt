package com.auroraclima.app.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * La mitad comprobable en JVM del cripto-borrado de RF-20: el blob envuelto de
 * la passphrase desaparece de las prefs aunque el Keystore no exista aquí
 * (mismo límite que P-13) — el mejor esfuerzo por paso no se detiene. La
 * destrucción del alias real queda cubierta por la pasada manual en emulador.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseKeyManagerEraseTest {
    @Test
    fun `erase limpia el blob de la passphrase aunque el keystore falle`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Mismos nombres neutros que usa DatabaseKeyManager (store_meta / s0 / s1).
        val preferences = context.getSharedPreferences("store_meta", Context.MODE_PRIVATE)
        preferences.edit().putString("s0", "AAAA").putString("s1", "BBBB").commit()

        DatabaseKeyManager(context).erase()

        assertTrue(preferences.all.isEmpty())
    }
}

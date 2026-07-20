package com.auroraclima.app.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** La marca durable de RF-20 sobrevive en prefs reales y se retira limpia. */
@RunWith(RobolectricTestRunner::class)
class PrefsPendingErasureFlagTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `izar, consultar y retirar la marca sobre prefs reales`() = runTest {
        val flag = PrefsPendingErasureFlag(context)
        assertFalse(flag.isRaised())

        assertTrue(flag.raise())
        assertTrue(flag.isRaised())
        // Otra instancia lee la misma marca: es durable, no estado en memoria.
        assertTrue(PrefsPendingErasureFlag(context).isRaised())

        flag.clear()
        assertFalse(flag.isRaised())
    }
}

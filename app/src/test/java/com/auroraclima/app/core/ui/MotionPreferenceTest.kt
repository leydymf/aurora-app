package com.auroraclima.app.core.ui

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** «Quitar animaciones» del sistema deja la escala en 0: es la señal de reducir movimiento. */
@RunWith(RobolectricTestRunner::class)
class MotionPreferenceTest {
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun setScale(value: Float) {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, value)
    }

    @Test
    fun `con la escala en cero se piden menos animaciones`() {
        setScale(0f)
        assertTrue(resolver.prefersReducedMotion())
    }

    @Test
    fun `con la escala normal se anima`() {
        setScale(1f)
        assertFalse(resolver.prefersReducedMotion())
    }

    @Test
    fun `una escala acelerada o lenta no cuenta como reducir`() {
        setScale(0.5f)
        assertFalse(resolver.prefersReducedMotion())
        setScale(2f)
        assertFalse(resolver.prefersReducedMotion())
    }
}

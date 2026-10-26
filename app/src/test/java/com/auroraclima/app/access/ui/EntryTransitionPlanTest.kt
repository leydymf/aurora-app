package com.auroraclima.app.access.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calendario de la transición de entrada: ~700 ms, con fundidos o, si el sistema lo pide, quieta. */
class EntryTransitionPlanTest {
    private val animated = EntryTransitionPlan.forMotion(reduceMotion = false)
    private val still = EntryTransitionPlan.forMotion(reduceMotion = true)

    @Test
    fun `dura alrededor de 700 ms con o sin animaciones`() {
        assertEquals(700L, animated.totalMillis)
        assertEquals(700L, still.totalMillis)
    }

    @Test
    fun `con animaciones la marca aparece sostiene y se desvanece`() {
        assertFalse(animated.isStatic)
        assertEquals(0f, animated.brandAlphaAt(0), 0.001f)
        assertEquals(0.5f, animated.brandAlphaAt(75), 0.001f)
        assertEquals(1f, animated.brandAlphaAt(150), 0.001f)
        assertEquals(1f, animated.brandAlphaAt(350), 0.001f)
        assertEquals(1f, animated.brandAlphaAt(549), 0.01f)
        assertEquals(0.5f, animated.brandAlphaAt(625), 0.001f)
        assertEquals(0f, animated.brandAlphaAt(700), 0.001f)
    }

    @Test
    fun `la opacidad nunca sale de cero a uno`() {
        (-50L..900L).forEach { t ->
            val alpha = animated.brandAlphaAt(t)
            assertTrue("t=$t alpha=$alpha", alpha in 0f..1f)
        }
    }

    @Test
    fun `reducir animaciones muestra la marca quieta y completa todo el tiempo`() {
        assertTrue(still.isStatic)
        assertEquals(0L, still.fadeInMillis)
        assertEquals(0L, still.fadeOutMillis)
        (0L..700L step 50L).forEach { t -> assertEquals("t=$t", 1f, still.brandAlphaAt(t), 0f) }
    }
}

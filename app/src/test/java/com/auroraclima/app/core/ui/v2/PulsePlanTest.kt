package com.auroraclima.app.core.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El halo y la flecha de la práctica (v0.3.3) se mueven con un ciclo suave y, con
 * «Quitar animaciones», quedan fijos (regla del ADR 2026-10-25 (c)).
 */
class PulsePlanTest {
    private val animated = PulsePlan.forMotion(reduceMotion = false)
    private val reduced = PulsePlan.forMotion(reduceMotion = true)

    @Test
    fun `con animaciones la fase recorre el ciclo y vuelve al reposo`() {
        assertFalse(animated.isStatic)
        assertEquals(0f, animated.phaseAt(0L), TOLERANCE)
        assertEquals(1f, animated.phaseAt(animated.periodMillis / 2), TOLERANCE)
        assertEquals(0f, animated.phaseAt(animated.periodMillis), TOLERANCE)
        assertTrue(
            "a un cuarto del ciclo el halo va a medio abrir",
            animated.phaseAt(animated.periodMillis / 4) in 0.4f..0.6f
        )
    }

    @Test
    fun `con animaciones reducidas la fase no cambia nunca`() {
        assertTrue(reduced.isStatic)
        listOf(0L, 1L, 400L, 800L, 1_599L, 1_000_000L).forEach { instant ->
            assertEquals("a los $instant ms", PulsePlan.STATIC_PHASE, reduced.phaseAt(instant), 0f)
        }
    }

    @Test
    fun `la fase queda siempre entre cero y uno`() {
        (0L..animated.periodMillis * 2 step 37L).forEach { instant ->
            assertTrue("a los $instant ms", animated.phaseAt(instant) in 0f..1f)
        }
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}

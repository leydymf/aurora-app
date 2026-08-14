package com.auroraclima.app.questionnaire.ui

import com.auroraclima.app.core.ui.v2.V2Palette
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fija el mapa nivel→severidad de la decisión directora 2026-08-14 (reversión
 * de P-41): una regresión que pintara de verde un nivel extremo sería el peor
 * error posible de esta pantalla (acta §3.2: sub-orientar daña más que
 * sobre-orientar).
 */
class GuidanceSeverityTest {
    @Test
    fun `cada orden de nivel recibe su par semaforico`() {
        assertEquals(V2Palette.accentSeverity1, severityAccent(1))
        assertEquals(V2Palette.accentSeverity2, severityAccent(2))
        assertEquals(V2Palette.accentSeverity3, severityAccent(3))
        assertEquals(V2Palette.accentSeverity4, severityAccent(4))
    }

    @Test
    fun `un orden fuera de la escala degrada al neutro`() {
        assertEquals(V2Palette.accentGroup, severityAccent(0))
        assertEquals(V2Palette.accentGroup, severityAccent(5))
        assertEquals(V2Palette.accentGroup, severityAccent(-1))
    }
}

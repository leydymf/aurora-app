package com.auroraclima.app.access.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** El ancho de texto de cada tarjeta de Acciones rápidas (v0.3.3) sale de la fila. */
class QuickAccessLabelWidthTest {
    @Test
    fun `tres tarjetas en un telefono de 360 dp`() {
        // 312 dp útiles a 2,625 px/dp = 819 px; separación 32 px y margen 26 px por lado.
        assertEquals(199, quickAccessLabelWidthPx(rowWidthPx = 819, gapPx = 32, sidePaddingPx = 26, count = 3))
    }

    @Test
    fun `el numero de tarjetas cuenta`() {
        assertEquals(341, quickAccessLabelWidthPx(rowWidthPx = 819, gapPx = 32, sidePaddingPx = 26, count = 2))
    }

    @Test
    fun `nunca negativo ni con cero tarjetas`() {
        assertEquals(0, quickAccessLabelWidthPx(rowWidthPx = 40, gapPx = 32, sidePaddingPx = 26, count = 3))
        assertEquals(0, quickAccessLabelWidthPx(rowWidthPx = 819, gapPx = 32, sidePaddingPx = 26, count = 0))
    }
}

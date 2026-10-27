package com.auroraclima.app.core.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las etiquetas de la barra de pestañas caben siempre en una línea (v0.3.3): si no
 * caben con la letra elegida, se reducen solo lo necesario.
 */
class TabLabelFitTest {
    @Test
    fun `si cabe conserva el tamano elegido`() {
        assertEquals(1f, oneLineScale(textWidth = 250, available = 280), 0f)
        assertEquals(1f, oneLineScale(textWidth = 280, available = 280), 0f)
    }

    @Test
    fun `si no cabe se reduce justo por debajo de lo que entra`() {
        val scale = oneLineScale(textWidth = 360, available = 280)

        assertTrue("reduce", scale < 1f)
        assertTrue("cabe con margen de redondeo", 360 * scale <= 280 - 1)
        assertTrue("no reduce de más", 360 * scale >= 280 * 0.95f)
    }

    @Test
    fun `sin medidas no reduce`() {
        assertEquals(1f, oneLineScale(textWidth = 0, available = 280), 0f)
        assertEquals(1f, oneLineScale(textWidth = 300, available = 0), 0f)
    }
}

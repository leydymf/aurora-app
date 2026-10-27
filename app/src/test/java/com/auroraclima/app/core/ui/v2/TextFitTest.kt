package com.auroraclima.app.core.ui.v2

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La búsqueda de la letra que no parte palabras (v0.3.3) y sus piezas puras: el ancho
 * del texto no escala en proporción a la letra, así que cada candidato se comprueba.
 */
class TextFitTest {
    @Test
    fun `si la primera escala ya sirve es esa`() {
        assertEquals(0.9f, firstFittingScale(0.9f) { true }, 0f)
    }

    @Test
    fun `sin proporcion la busqueda sigue reduciendo hasta que de verdad cabe`() {
        // Ancho medido de «Llamar»: 209 px a escala 1 y 203 px a 12/13, no 193. Con un
        // modelo así, la escala proporcional (199/209) no alcanza y hay que seguir.
        fun widthAt(scale: Float): Float = 209f - (1f - scale) * 78f
        val start = oneLineScale(textWidth = 209, available = 199)

        val scale = firstFittingScale(start) { widthAt(it) <= 199f }

        assertTrue("cabe", widthAt(scale) <= 199f)
        assertTrue("no reduce más de un paso de lo necesario", widthAt(scale / SHRINK_STEP) > 199f)
    }

    @Test
    fun `si ningun candidato sirve devuelve el siguiente de la serie sin probarlo`() {
        val tried = mutableListOf<Float>()

        val scale = firstFittingScale(1f) {
            tried += it
            false
        }

        assertEquals(MAX_SHRINK_STEPS - 1, tried.size)
        assertEquals(tried.last() * SHRINK_STEP, scale, 0.0001f)
    }

    @Test
    fun `una escala inicial mayor que uno no agranda la letra`() {
        assertEquals(1f, firstFittingScale(1.5f) { true }, 0f)
    }

    @Test
    fun `los cortes entre palabras pasan y los de mitad de palabra no`() {
        assertTrue(breaksOnlyAtSpaces("Guardar una nota", listOf(12, 16)))
        assertTrue(breaksOnlyAtSpaces("Llamar", listOf(6)))
        assertFalse(breaksOnlyAtSpaces("Llamar", listOf(5, 6)))
        assertFalse(breaksOnlyAtSpaces("Informarme", listOf(5, 10)))
    }

    @Test
    fun `escalar reduce la letra y el interlineado, y no agranda`() {
        val style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)

        val scaled = style.scaledBy(0.5f)

        assertEquals(6.5f, scaled.fontSize.value, 0.001f)
        assertEquals(9f, scaled.lineHeight.value, 0.001f)
        assertSame(style, style.scaledBy(1f))
        assertSame(style, style.scaledBy(1.2f))
    }

    @Test
    fun `sin interlineado ni letra especificados no falla`() {
        val noLineHeight = TextStyle(fontSize = 13.sp)
        assertEquals(TextUnit.Unspecified, noLineHeight.scaledBy(0.5f).lineHeight)
        val noFontSize = TextStyle()
        assertSame(noFontSize, noFontSize.scaledBy(0.5f))
    }
}

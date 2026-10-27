package com.auroraclima.app.core.ui.v2

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El globo del recorrido va debajo del elemento si cabe, si no encima, y si no cabe en
 * ninguno de los dos lados (letra grande en pantalla baja) se pega al borde del lado
 * con más espacio, siempre dentro de la franja usable.
 */
class SpotlightPlacementTest {
    private val top = 40f
    private val bottom = 1_000f
    private val gap = 10f

    @Test
    fun `con sitio debajo va debajo`() {
        val spot = SpotlightPlacement.place(Rect(0f, 100f, 300f, 200f), 300f, top, bottom, gap)

        assertTrue(spot.below)
        assertEquals(210f, spot.top, 0f)
    }

    @Test
    fun `sin sitio debajo va encima`() {
        val spot = SpotlightPlacement.place(Rect(0f, 800f, 300f, 900f), 300f, top, bottom, gap)

        assertFalse(spot.below)
        assertEquals(490f, spot.top, 0f)
    }

    @Test
    fun `sin sitio en ningun lado se pega al borde del lado mas amplio`() {
        // Elemento muy alto: 300 de aire arriba y 400 abajo, globo de 500.
        val spot = SpotlightPlacement.place(Rect(0f, 350f, 300f, 590f), 500f, top, bottom, gap)

        assertTrue(spot.below)
        assertEquals(bottom - 500f, spot.top, 0f)
    }

    @Test
    fun `un globo mas alto que la franja empieza arriba de ella`() {
        val spot = SpotlightPlacement.place(Rect(0f, 300f, 300f, 400f), 2_000f, top, bottom, gap)

        assertTrue("nunca por encima de la franja usable", spot.top >= top)
    }

    @Test
    fun `si el globo cruza la nube su fila de botones termina antes de ella`() {
        // Globo de 16 a 344 de ancho, nube de 288 a 340 a la altura de sus botones.
        val bubble = Rect(16f, 400f, 344f, 600f)
        val cloud = Rect(288f, 540f, 340f, 592f)

        val inset = SpotlightPlacement.actionsEndInset(bubble, cloud, gap = 8f, contentPadding = 20f)

        // La fila acaba en 344 - 20 = 324; debe acabar en 288 - 8 = 280.
        assertEquals(44f, inset, 0.001f)
    }

    @Test
    fun `si el globo no cruza la nube la fila conserva su ancho`() {
        val bubble = Rect(16f, 100f, 344f, 300f)
        val cloud = Rect(288f, 540f, 340f, 592f)

        assertEquals(0f, SpotlightPlacement.actionsEndInset(bubble, cloud, gap = 8f, contentPadding = 20f), 0f)
        assertEquals(0f, SpotlightPlacement.actionsEndInset(bubble, null, gap = 8f, contentPadding = 20f), 0f)
    }

    @Test
    fun `un elemento abajo de todo deja el globo encima y dentro de la franja`() {
        // La barra de pestañas: pegada al borde inferior.
        val spot = SpotlightPlacement.place(Rect(0f, 940f, 120f, 1_000f), 240f, top, bottom, gap)

        assertFalse(spot.below)
        assertEquals(940f - gap - 240f, spot.top, 0f)
        assertTrue(spot.top >= top)
    }
}

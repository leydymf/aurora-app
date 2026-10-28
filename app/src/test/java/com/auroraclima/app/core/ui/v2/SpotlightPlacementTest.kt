package com.auroraclima.app.core.ui.v2

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tarjeta de la guía va debajo del elemento si cabe, si no encima, y si no cabe
 * entera en ninguno de los dos lados (letra grande en pantalla baja) va en el lado con
 * más espacio con el alto de ese lado: nunca tapa el elemento (v0.3.4).
 */
class SpotlightPlacementTest {
    private val top = 40f
    private val bottom = 1_000f
    private val gap = 10f
    private val metrics = SpotlightMetrics(
        edgeMargin = 16f,
        maxBubbleWidth = 480f,
        gap = 14f,
        arrowGap = 64f,
        holePadding = 6f,
        minLit = 96f,
        bubblePadding = 20f
    )

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
    fun `sin sitio en ningun lado va al lado mas amplio sin tapar el elemento`() {
        // Elemento muy alto: 300 de aire arriba y 400 abajo, tarjeta de 500.
        val target = Rect(0f, 350f, 300f, 590f)
        val spot = SpotlightPlacement.place(target, 500f, top, bottom, gap)

        assertTrue(spot.below)
        assertFalse(spot.fits)
        assertEquals(target.bottom + gap, spot.top, 0f)
        assertEquals("se acorta al alto de ese lado", bottom - (target.bottom + gap), spot.maxHeight, 0f)
    }

    @Test
    fun `una tarjeta mas alta que la franja queda entera de su lado`() {
        val target = Rect(0f, 300f, 300f, 400f)
        val spot = SpotlightPlacement.place(target, 2_000f, top, bottom, gap)

        assertTrue("debajo hay más sitio", spot.below)
        assertTrue(spot.top >= target.bottom)
        assertTrue(spot.top + spot.maxHeight <= bottom)
    }

    @Test
    fun `con letra grande en pantalla baja la tarjeta nunca tapa el elemento`() {
        // Pantalla de 568 de alto; elementos a varias alturas, tarjetas de varios altos.
        val shortTop = 24f
        val shortBottom = 544f
        listOf(60f, 200f, 300f, 420f, 480f).forEach { y ->
            val target = Rect(16f, y, 344f, y + 60f)
            listOf(120f, 300f, 600f).forEach { height ->
                val frame = SpotlightPlacement.frame(target, null, 360f, shortTop, shortBottom, height, metrics)
                assertFalse(
                    "tarjeta de $height con el elemento en $y lo tapa",
                    frame.bubble.overlaps(frame.hole)
                )
                assertTrue(frame.bubble.top >= shortTop - 0.001f && frame.bubble.bottom <= shortBottom + 0.001f)
            }
        }
    }

    @Test
    fun `la tarjeta deja libre el circulo y si cabe el sitio de la flecha`() {
        val target = Rect(16f, 200f, 344f, 260f)
        val ring = { hole: Rect -> hole.inflate(20f) }
        val frame = SpotlightPlacement.frame(target, null, 360f, 40f, 1_000f, 200f, metrics, ringOf = ring)

        assertEquals(frame.hole.inflate(20f), frame.ring)
        assertFalse(frame.bubble.overlaps(frame.ring))
        assertEquals("se aparta lo que pide la flecha", frame.ring.bottom + 64f, frame.bubble.top, 0.001f)
        // Sin sitio para la flecha, al menos el aire mínimo.
        val tight = SpotlightPlacement.frame(target, null, 360f, 40f, 560f, 230f, metrics, ringOf = ring)
        assertFalse(tight.bubble.overlaps(tight.ring))
    }

    @Test
    fun `si la tarjeta cruza la nube su fila de botones termina antes de ella`() {
        // Tarjeta de 16 a 344 de ancho, nube de 288 a 340 a la altura de sus botones.
        val bubble = Rect(16f, 400f, 344f, 600f)
        val cloud = Rect(288f, 540f, 340f, 592f)

        val inset = SpotlightPlacement.actionsEndInset(bubble, cloud, gap = 8f, contentPadding = 20f)

        // La fila acaba en 344 - 20 = 324; debe acabar en 288 - 8 = 280.
        assertEquals(44f, inset, 0.001f)
    }

    @Test
    fun `si la tarjeta no cruza la nube la fila conserva su ancho`() {
        val bubble = Rect(16f, 100f, 344f, 300f)
        val cloud = Rect(288f, 540f, 340f, 592f)

        assertEquals(0f, SpotlightPlacement.actionsEndInset(bubble, cloud, gap = 8f, contentPadding = 20f), 0f)
        assertEquals(0f, SpotlightPlacement.actionsEndInset(bubble, null, gap = 8f, contentPadding = 20f), 0f)
    }

    @Test
    fun `de derecha a izquierda la nube esta a la izquierda y el recorte tambien`() {
        // Tarjeta de 16 a 344; nube a la izquierda, de 20 a 72, a la altura de sus botones.
        val bubble = Rect(16f, 400f, 344f, 600f)
        val cloud = Rect(20f, 540f, 72f, 592f)

        val inset = SpotlightPlacement.actionsEndInset(
            bubble,
            cloud,
            gap = 8f,
            contentPadding = 20f,
            rightToLeft = true
        )

        // La fila empieza en 16 + 20 = 36; debe empezar en 72 + 8 = 80.
        assertEquals(44f, inset, 0.001f)
        // Sin saber la dirección, el recorte se habría calculado contra el lado contrario.
        assertTrue(SpotlightPlacement.actionsEndInset(bubble, cloud, 8f, 20f, rightToLeft = false) > 200f)
    }

    @Test
    fun `el cuadro no deja bajar la tarjeta de la nube y le recorta los botones`() {
        // Pantalla de 360 de ancho; el elemento (una pestaña) abajo de todo y la nube 20
        // por encima de la barra, a la derecha: la tarjeta va encima del elemento y la cruza.
        val tab = Rect(130f, 900f, 230f, 960f)
        val cloud = Rect(288f, 828f, 340f, 880f)

        val frame = SpotlightPlacement.frame(
            target = tab,
            keepClear = cloud,
            areaWidth = 360f,
            top = 40f,
            bottom = 1_000f,
            bubbleHeight = 200f,
            metrics = metrics
        )

        assertTrue("la tarjeta no baja del borde inferior de la nube", frame.bubble.bottom <= cloud.bottom + 0.001f)
        assertTrue(
            "la fila de botones termina antes de la nube",
            frame.bubble.right - 20f - frame.actionsEndInset <= cloud.left - 14f
        )
        assertTrue("la tarjeta va encima del elemento", frame.bubble.bottom <= frame.hole.top)
        assertFalse(frame.bubble.overlaps(frame.hole))
    }

    @Test
    fun `el cuadro centra la tarjeta debajo del engranaje`() {
        val gear = Rect(300f, 60f, 348f, 108f)

        val frame = SpotlightPlacement.frame(gear, null, 360f, 40f, 1_000f, 200f, metrics)

        assertEquals(16f, frame.bubble.left, 0.001f)
        assertEquals(344f, frame.bubble.right, 0.001f)
        assertTrue(frame.bubble.top >= frame.hole.bottom)
        assertEquals(0f, frame.actionsEndInset, 0f)
    }

    @Test
    fun `un elemento abajo de todo deja la tarjeta encima y dentro de la franja`() {
        // La barra de pestañas: pegada al borde inferior.
        val spot = SpotlightPlacement.place(Rect(0f, 940f, 120f, 1_000f), 240f, top, bottom, gap)

        assertFalse(spot.below)
        assertEquals(940f - gap - 240f, spot.top, 0f)
        assertTrue(spot.top >= top)
    }

    @Test
    fun `sin medir va al lado con mas sitio y no a uno sin espacio`() {
        // Una pestaña abajo de todo: debajo no hay sitio. Sin el alto de la tarjeta
        // aún, debe ir encima con el alto de ese lado para poder medirse.
        val tab = Rect(130f, 940f, 230f, 1_000f)
        val spot = SpotlightPlacement.place(tab, 0f, top, bottom, gap)

        assertFalse(spot.below)
        assertTrue(spot.maxHeight > 0f)
    }

    @Test
    fun `un elemento muy alto deja la tarjeta entera abajo y se ilumina lo que queda encima`() {
        // Acciones rápidas apiladas con letra 2.0: casi toda la altura útil.
        val section = Rect(16f, 590f, 1064f, 1390f)
        val cloud = Rect(890f, 1510f, 1027f, 1645f)
        val frame = SpotlightPlacement.frame(section, cloud, 1080f, 105f, 1752f, 540f, metrics)

        assertEquals("la tarjeta tiene su alto entero", 540f, frame.bubble.height, 0.5f)
        assertTrue("y no baja de la nube", frame.bubble.bottom <= cloud.bottom + 0.5f)
        assertFalse("no tapa lo iluminado", frame.bubble.overlaps(frame.hole))
        assertTrue("lo iluminado es parte del elemento", frame.hole.top >= section.top - 6f - 0.5f)
        assertTrue("y se ve algo de él", frame.hole.height >= 96f)
    }

    @Test
    fun `si casi nada quedaria iluminado se aplica la regla general`() {
        val section = Rect(16f, 1300f, 1064f, 1390f)
        val frame = SpotlightPlacement.frame(section, null, 1080f, 1000f, 1752f, 900f, metrics)

        assertFalse(frame.bubble.overlaps(frame.hole))
        assertEquals(section.inflate(6f), frame.hole)
    }
}

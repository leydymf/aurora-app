package com.auroraclima.app.core.ui.v2

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.atan2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los trazos a mano de la guía (v0.3.4): la flecha llega a su objetivo sin cruzar la
 * tarjeta ni la nube, con su vuelta antes de llegar, y el círculo rodea el elemento
 * pasándose de donde empezó.
 */
class HandDrawnGeometryTest {
    private val metrics = CurlMetrics(loopRadius = 18f, headLength = 14f, clearance = 4f, minLength = 30f)
    private val screen = Rect(0f, 0f, 1080f, 2200f)

    @Test
    fun `la flecha empieza y termina donde se pide y tiene vuelta si hay sitio`() {
        val shape = HandDrawnGeometry.curlArrow(Offset(300f, 1200f), Offset(500f, 800f), emptyList(), screen, metrics)
        assertNotNull(shape)
        assertEquals(Offset(300f, 1200f), shape!!.body.first().start)
        assertEquals(Offset(500f, 800f), shape.tip)
        assertTrue("con sitio lleva rizo: varios tramos", shape.body.size > 1)
    }

    @Test
    fun `la flecha no cruza la tarjeta ni la nube`() {
        val card = Rect(40f, 1220f, 1040f, 1700f)
        val cloud = Rect(880f, 1900f, 1040f, 2060f)
        val target = Rect(700f, 600f, 1000f, 760f)
        val from = Offset(600f, 1210f)
        val to = Offset(820f, 770f)
        val shape = HandDrawnGeometry.curlArrow(from, to, listOf(card, cloud, target), screen, metrics)
        assertNotNull(shape)
        samples(shape!!).forEach { point ->
            assertFalse("pasa por la tarjeta en $point", card.contains(point))
            assertFalse("pasa por la nube en $point", cloud.contains(point))
        }
    }

    @Test
    fun `si un lado del rizo choca prueba el otro`() {
        val from = Offset(500f, 1200f)
        val to = Offset(500f, 700f)
        val free = HandDrawnGeometry.curlArrow(from, to, emptyList(), screen, metrics)!!
        // Un obstáculo justo en lo más alejado del primer rizo obliga a usar el otro lado.
        val farthest = samples(free).maxBy { kotlin.math.abs(it.x - from.x) }
        val obstacle = Rect(farthest.x - 6f, farthest.y - 6f, farthest.x + 6f, farthest.y + 6f)
        val other = HandDrawnGeometry.curlArrow(from, to, listOf(obstacle), screen, metrics)
        assertNotNull(other)
        assertTrue("sigue con rizo, del otro lado", other!!.body.size > 1)
        assertTrue((samples(other).maxBy { kotlin.math.abs(it.x - from.x) }.x - from.x) * (farthest.x - from.x) < 0)
        samples(other).forEach { assertFalse(obstacle.inflate(metrics.clearance).contains(it)) }
    }

    @Test
    fun `de derecha a izquierda tambien esquiva`() {
        // La nube a la izquierda, la tarjeta igual: el trazo sale hacia la izquierda.
        val card = Rect(40f, 1220f, 1040f, 1700f)
        val cloud = Rect(40f, 1900f, 200f, 2060f)
        val from = Offset(400f, 1210f)
        val shape = HandDrawnGeometry.curlArrow(from, Offset(200f, 760f), listOf(card, cloud), screen, metrics)
        assertNotNull(shape)
        samples(shape!!).forEach { assertFalse(card.contains(it) || cloud.contains(it)) }
    }

    @Test
    fun `sin sitio no hay flecha`() {
        assertNull(HandDrawnGeometry.curlArrow(Offset(10f, 10f), Offset(20f, 15f), emptyList(), screen, metrics))
        // Todo el camino bloqueado: mejor ninguna que una encima de la tarjeta.
        val wall = Rect(0f, 900f, 1080f, 1100f)
        assertNull(HandDrawnGeometry.curlArrow(Offset(500f, 1200f), Offset(500f, 800f), listOf(wall), screen, metrics))
    }

    @Test
    fun `la flecha no sale de la pantalla`() {
        val shape = HandDrawnGeometry.curlArrow(Offset(30f, 1200f), Offset(30f, 700f), emptyList(), screen, metrics)
        assertNotNull(shape)
        samples(shape!!).forEach { assertTrue("fuera en $it", screen.contains(it)) }
    }

    @Test
    fun `el circulo rodea el elemento y se pasa del inicio`() {
        val bar = Rect(80f, 900f, 1000f, 1060f)
        val points = HandDrawnGeometry.handCircle(bar, margin = 20f)
        assertTrue(HandDrawnGeometry.CIRCLE_SWEEP > 2 * Math.PI)
        assertTrue("termina en otro punto que el inicio", (points.first() - points.last()).getDistance() > 1f)
        // Ningún punto entra en la parte central del elemento.
        val core = bar.deflate(bar.height / 2f)
        points.forEach { assertFalse("entra al elemento en $it", core.contains(it)) }
        // Recorre más de una vuelta alrededor del centro.
        var turned = 0.0
        points.zipWithNext().forEach { (a, b) ->
            val angleA = atan2((a.y - bar.center.y).toDouble(), (a.x - bar.center.x).toDouble())
            val angleB = atan2((b.y - bar.center.y).toDouble(), (b.x - bar.center.x).toDouble())
            var delta = angleB - angleA
            if (delta > Math.PI) delta -= 2 * Math.PI
            if (delta < -Math.PI) delta += 2 * Math.PI
            turned += delta
        }
        assertTrue("da ${"%.2f".format(turned)} rad", turned > 2 * Math.PI)
    }

    @Test
    fun `el trazo se dibuja en su tiempo y quieto aparece completo`() {
        val animated = DrawPlan.forMotion(reduceMotion = false)
        assertEquals(0f, animated.progressAt(0L), 0f)
        assertEquals(1f, animated.progressAt(animated.durationMillis), 0f)
        assertTrue(animated.progressAt(animated.durationMillis / 2) in 0.5f..1f)
        val still = DrawPlan.forMotion(reduceMotion = true)
        assertTrue(still.isStatic)
        listOf(0L, 10L, 5_000L).forEach { assertEquals(1f, still.progressAt(it), 0f) }
    }

    @Test
    fun `la respiracion es lenta y quieta con animaciones reducidas`() {
        val breathing = PulsePlan.breathing(reduceMotion = false)
        assertTrue(breathing.periodMillis > PulsePlan.forMotion(reduceMotion = false).periodMillis)
        assertTrue(PulsePlan.breathing(reduceMotion = true).isStatic)
        assertTrue(breathScale(1f) <= 1.05f)
    }

    private fun samples(shape: CurlArrowShape): List<Offset> =
        shape.body.flatMap { segment -> (0..40).map { segment.pointAt(it / 40f) } } +
            listOf(shape.headLeft, shape.headRight)
}

package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Trazos a mano de la guía de Aurora (v0.3.4): la flecha con rizo y el círculo que
 * rodea un elemento. La geometría es pura ([HandDrawnGeometry]) para probar en la JVM
 * que la flecha no cruza la tarjeta ni la nube; aquí solo se dibuja, con Path y sin
 * recursos de imagen. Son decorativos: no tienen semántica ni reciben toques.
 */

/** Un tramo cúbico de Bézier. */
data class CubicSegment(val start: Offset, val control1: Offset, val control2: Offset, val end: Offset) {
    /** El punto en [t] (0..1). */
    fun pointAt(t: Float): Offset {
        val u = 1f - t
        return start * (u * u * u) + control1 * (3f * u * u * t) + control2 * (3f * u * t * t) + end * (t * t * t)
    }
}

/** La flecha: el cuerpo en tramos, la punta y los extremos de sus dos trazos. */
data class CurlArrowShape(
    val body: List<CubicSegment>,
    val tip: Offset,
    val headLeft: Offset,
    val headRight: Offset
)

/** Medidas de la flecha en píxeles. */
data class CurlMetrics(
    val loopRadius: Float,
    val headLength: Float,
    val clearance: Float,
    val minLength: Float
)

object HandDrawnGeometry {
    /**
     * Flecha de [from] a [to] que hace una vuelta antes de llegar. Prueba el rizo a un
     * lado y al otro; si ambos cruzan algo de [avoid] (la tarjeta, la nube, el propio
     * elemento) o salen de [within], prueba una curva sin rizo, y si tampoco cabe no
     * hay flecha: el hueco de luz y la tarjeta ya dicen dónde mirar.
     */
    fun curlArrow(from: Offset, to: Offset, avoid: List<Rect>, within: Rect?, metrics: CurlMetrics): CurlArrowShape? {
        val length = (to - from).getDistance()
        if (length < metrics.minLength) return null
        val withLoop = if (length >= LOOP_MIN_FACTOR * metrics.loopRadius + metrics.headLength) {
            SIDES.map { looped(from, to, it, metrics) }
        } else {
            emptyList()
        }
        val plain = SIDES.map { bowed(from, to, it, metrics) }
        return (withLoop + plain).firstOrNull { isClear(it, avoid, within, metrics.clearance) }
    }

    /**
     * Círculo trazado a mano alrededor de [around]: una elipse imperfecta (radio que
     * ondula un poco y crece al avanzar) que da más de una vuelta y se pasa de donde
     * empezó. Devuelve la polilínea en orden de trazo.
     */
    fun handCircle(around: Rect, margin: Float, points: Int = CIRCLE_POINTS): List<Offset> {
        val center = around.center
        val radiusX = around.width / 2f + margin + around.height * 0.1f
        val radiusY = around.height / 2f * 1.35f + margin
        return (0..points).map { index ->
            val share = index.toFloat() / points
            val angle = CIRCLE_START + CIRCLE_SWEEP * share
            val wobble = 1f + 0.03f * sin(2f * angle + 0.7f) + 0.015f * sin(5f * angle + 1.9f)
            val drift = 0.975f + 0.05f * share
            Offset(
                center.x + radiusX * wobble * drift * cos(angle),
                center.y + radiusY * wobble * drift * sin(angle)
            )
        }
    }

    /** Lo que recorre el círculo, en radianes: más de una vuelta. */
    const val CIRCLE_SWEEP = (2 * PI + 0.45).toFloat()

    private const val CIRCLE_START = -2.2f
    private const val CIRCLE_POINTS = 72
    private const val LOOP_MIN_FACTOR = 4f
    private const val SAMPLES_PER_SEGMENT = 24
    private const val HEAD_ANGLE = 0.49f // ~28°
    private const val KAPPA = 0.5523f
    private val SIDES = listOf(1f, -1f)

    /** Salida, vuelta (elipse en cuatro cuartos) y llegada, con el rizo del lado [side]. */
    private fun looped(from: Offset, to: Offset, side: Float, metrics: CurlMetrics): CurlArrowShape {
        val length = (to - from).getDistance()
        val u = (to - from) / length
        val n = Offset(-u.y, u.x) * side
        val radiusAlong = metrics.loopRadius
        val radiusAcross = metrics.loopRadius * 1.15f
        val meet = from + u * (length * 0.42f)
        val center = meet + n * radiusAcross
        fun point(angle: Float) = center + n * (-radiusAcross * cos(angle)) + u * (radiusAlong * sin(angle))
        fun velocity(angle: Float) = n * (radiusAcross * sin(angle)) + u * (radiusAlong * cos(angle))
        val quarter = (PI / 2).toFloat()
        val loop = (0 until 4).map { i ->
            val a0 = quarter * i
            val a1 = quarter * (i + 1)
            CubicSegment(
                start = point(a0),
                control1 = point(a0) + velocity(a0) * (KAPPA),
                control2 = point(a1) - velocity(a1) * (KAPPA),
                end = point(a1)
            )
        }
        val lead = CubicSegment(
            start = from,
            control1 = from + u * (length * 0.14f) - n * (length * 0.06f),
            control2 = meet - u * (length * 0.14f),
            end = meet
        )
        val tail = CubicSegment(
            start = meet,
            control1 = meet + u * (length * 0.16f),
            control2 = to - u * (length * 0.16f) + n * (length * 0.05f),
            end = to
        )
        return withHead(listOf(lead) + loop + tail, metrics.headLength)
    }

    /** Curva suave sin rizo, combada hacia [side]. */
    private fun bowed(from: Offset, to: Offset, side: Float, metrics: CurlMetrics): CurlArrowShape {
        val length = (to - from).getDistance()
        val u = (to - from) / length
        val bow = Offset(-u.y, u.x) * (side * length * 0.18f)
        val segment = CubicSegment(
            start = from,
            control1 = from + u * (length / 3f) + bow,
            control2 = from + u * (length * 2f / 3f) + bow,
            end = to
        )
        return withHead(listOf(segment), metrics.headLength)
    }

    /** Punta de dos trazos a ±28° de la dirección de llegada. */
    private fun withHead(body: List<CubicSegment>, headLength: Float): CurlArrowShape {
        val last = body.last()
        val tip = last.end
        val incoming = tip - last.pointAt(0.9f)
        val direction = incoming / incoming.getDistance().coerceAtLeast(1e-3f)
        val back = direction * -headLength
        return CurlArrowShape(
            body = body,
            tip = tip,
            headLeft = tip + rotate(back, HEAD_ANGLE),
            headRight = tip + rotate(back, -HEAD_ANGLE)
        )
    }

    private fun rotate(vector: Offset, angle: Float): Offset = Offset(
        vector.x * cos(angle) - vector.y * sin(angle),
        vector.x * sin(angle) + vector.y * cos(angle)
    )

    private fun isClear(shape: CurlArrowShape, avoid: List<Rect>, within: Rect?, clearance: Float): Boolean {
        val blocked = avoid.map { it.inflate(clearance) }
        val samples = shape.body.flatMap { segment ->
            (0..SAMPLES_PER_SEGMENT).map { segment.pointAt(it.toFloat() / SAMPLES_PER_SEGMENT) }
        } + listOf(shape.headLeft, shape.headRight)
        return samples.none { point -> blocked.any { it.contains(point) } } &&
            (within == null || samples.all { within.contains(it) })
    }
}

/**
 * Cómo se dibuja un trazo (v0.3.4): de inicio a fin en [durationMillis], con salida
 * suave. Puro, como [PulsePlan]. Con «Quitar animaciones» ([still]) el trazo aparece
 * completo y no se pide ningún cuadro.
 */
data class DrawPlan(val durationMillis: Long) {
    val isStatic: Boolean get() = durationMillis == 0L

    /** Cuánto del trazo se ve a los [elapsedMillis], de 0 a 1. */
    fun progressAt(elapsedMillis: Long): Float {
        if (isStatic) return 1f
        val t = (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        val rest = 1f - t
        return 1f - rest * rest * rest
    }

    companion object {
        private const val DRAW_MILLIS = 400L

        val still = DrawPlan(durationMillis = 0L)

        fun forMotion(reduceMotion: Boolean): DrawPlan = if (reduceMotion) still else DrawPlan(DRAW_MILLIS)
    }
}

/**
 * El avance del trazo de [plan]; vuelve a empezar cuando cambia [key] (otro paso,
 * otro elemento). Usa cuadros finitos: termina y deja de pedirlos.
 */
@Composable
fun rememberDrawProgress(plan: DrawPlan, key: Any?): State<Float> {
    val progress = remember(plan, key) { mutableFloatStateOf(plan.progressAt(0L)) }
    if (!plan.isStatic) {
        LaunchedEffect(plan, key) {
            val start = withFrameMillis { it }
            while (progress.floatValue < 1f) {
                withFrameMillis { now -> progress.floatValue = plan.progressAt(now - start) }
            }
        }
    }
    return progress
}

/**
 * El círculo a mano alrededor de [target] y la flecha con rizo que sale de [card] y
 * lo señala, en las coordenadas de esta capa. La flecha esquiva la tarjeta y lo que
 * haya en [avoid] (la nube); si no cabe, solo queda el círculo. Ambos se trazan con
 * [draw] cada vez que cambia [key] y después el círculo respira con [breath].
 * Decorativo: no recibe toques ni tiene semántica.
 */
@Composable
fun HandDrawnCue(
    target: Rect?,
    card: Rect?,
    avoid: List<Rect>,
    color: Color,
    draw: DrawPlan,
    breath: PulsePlan,
    key: Any?,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val progress = rememberDrawProgress(draw, key)
    val phase = rememberPulsePhase(breath)
    val cue = remember(target, card, avoid, density) {
        target?.let { with(density) { cueFor(it, card, avoid, this) } }
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val shape = cue ?: return@Canvas
        val drawn = progress.value
        drawHandCircle(shape.circle, color, drawn, if (drawn >= 1f) breathScale(phase.value) else 1f)
        shape.arrow?.let { drawCurlArrow(it, color, drawn) }
    }
}

/**
 * Lo que ocupa el círculo a mano alrededor de [target] (su caja): la capa de recorrido
 * aparta la tarjeta de aquí para que no tape el círculo.
 */
fun handCueRing(target: Rect, density: Density): Rect = boundsOf(handCircleAround(target, density))

private fun handCircleAround(target: Rect, density: Density): List<Offset> =
    HandDrawnGeometry.handCircle(target, margin = with(density) { CIRCLE_MARGIN.toPx() })

private fun boundsOf(points: List<Offset>): Rect =
    Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

/** El círculo y, si cabe, la flecha de una señal. */
private class Cue(val circle: List<Offset>, val arrow: CurlArrowShape?)

private fun cueFor(target: Rect, card: Rect?, avoid: List<Rect>, density: Density): Cue = with(density) {
    val circle = handCircleAround(target, density)
    val ring = boundsOf(circle)
    val arrow = card?.let { from ->
        val gap = ARROW_GAP.toPx()
        val targetBelow = ring.center.y > from.center.y
        // Sale del borde de la tarjeta que mira al objetivo, corrida hacia el lado
        // contrario, para que el trazo vaya en diagonal y haya sitio para la vuelta.
        val towardRight = ring.center.x > from.center.x
        val startX = if (towardRight) from.left + from.width * 0.28f else from.right - from.width * 0.28f
        val start = Offset(startX, if (targetBelow) from.bottom + gap else from.top - gap)
        val endX = (ring.center.x + (if (startX < ring.center.x) -1f else 1f) * target.width * 0.12f)
            .coerceIn(ring.left, ring.right)
        val end = Offset(endX, if (targetBelow) ring.top - gap else ring.bottom + gap)
        HandDrawnGeometry.curlArrow(
            from = start,
            to = end,
            avoid = avoid + from + target,
            within = null,
            metrics = CurlMetrics(
                loopRadius = LOOP_RADIUS.toPx(),
                headLength = HEAD_LENGTH.toPx(),
                clearance = CLEARANCE.toPx(),
                minLength = MIN_ARROW.toPx()
            )
        )
    }
    Cue(circle, arrow)
}

private val CIRCLE_MARGIN = 10.dp
private val ARROW_GAP = 8.dp
private val LOOP_RADIUS = 13.dp
private val HEAD_LENGTH = 12.dp
private val CLEARANCE = 2.dp
private val MIN_ARROW = 28.dp

/** Escala del círculo que respira: casi nada, despacio ([PulsePlan.breathing]). */
fun breathScale(phase: Float): Float = 1f + BREATH_AMPLITUDE * phase

private const val BREATH_AMPLITUDE = 0.03f

/** Grosor de los trazos a mano. */
val HandStrokeWidth = 3.dp

/** Dibuja la flecha hasta [progress] de su largo; la punta aparece al final. */
fun DrawScope.drawCurlArrow(shape: CurlArrowShape, color: Color, progress: Float) {
    val path = Path().apply {
        moveTo(shape.body.first().start.x, shape.body.first().start.y)
        shape.body.forEach { cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y) }
    }
    val stroke = handStroke()
    drawPartial(path, progress, color, stroke)
    val head = ((progress - HEAD_FROM) / (1f - HEAD_FROM)).coerceIn(0f, 1f)
    if (head > 0f) {
        listOf(shape.headLeft, shape.headRight).forEach { end ->
            drawLine(
                color = color,
                start = shape.tip,
                end = shape.tip + (end - shape.tip) * head,
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Dibuja el círculo a mano hasta [progress], con la escala [scale] (la respiración). */
fun DrawScope.drawHandCircle(points: List<Offset>, color: Color, progress: Float, scale: Float = 1f) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        // Puntos medios como anclas y los puntos como control: una curva suave.
        for (i in 1 until points.size - 1) {
            val mid = (points[i] + points[i + 1]) / 2f
            quadraticTo(points[i].x, points[i].y, mid.x, mid.y)
        }
        lineTo(points.last().x, points.last().y)
    }
    val xs = points.map { it.x }
    val ys = points.map { it.y }
    val pivot = Offset((xs.min() + xs.max()) / 2f, (ys.min() + ys.max()) / 2f)
    scale(scale, pivot) { drawPartial(path, progress, color, handStroke()) }
}

private const val HEAD_FROM = 0.85f

private fun DrawScope.handStroke() =
    Stroke(width = HandStrokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawPartial(path: Path, progress: Float, color: Color, stroke: Stroke) {
    if (progress >= 1f) {
        drawPath(path, color, style = stroke)
        return
    }
    if (progress <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val partial = Path()
    measure.getSegment(0f, measure.length * progress, partial, true)
    drawPath(partial, color, style = stroke)
}

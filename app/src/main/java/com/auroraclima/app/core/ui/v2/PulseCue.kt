package com.auroraclima.app.core.ui.v2

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos

/**
 * Calendario de la señal que muestra dónde actuar (práctica del tutorial): el halo
 * respira y la flecha se mece, en un ciclo suave. Es puro, como el de la transición
 * de entrada: la pantalla solo le pregunta la fase de cada instante, así que el
 * movimiento se prueba en la JVM.
 *
 * Con la preferencia de reducir animaciones, [forMotion] devuelve un calendario
 * quieto: el halo y la flecha se ven, fijos, a media fase, y no se pide ningún cuadro.
 */
data class PulsePlan(val periodMillis: Long) {
    /** Sin ciclo: nada se mueve. */
    val isStatic: Boolean get() = periodMillis == 0L

    /** Fase a los [elapsedMillis], entre 0 (reposo) y 1 (máximo), con subida y bajada suaves. */
    fun phaseAt(elapsedMillis: Long): Float {
        if (isStatic) return STATIC_PHASE
        val turn = (elapsedMillis % periodMillis).toFloat() / periodMillis
        return ((1f - cos(2 * PI * turn).toFloat()) / 2f).coerceIn(0f, 1f)
    }

    companion object {
        /** Fase fija con animaciones reducidas: un halo visible, ni recogido ni abierto del todo. */
        const val STATIC_PHASE = 0.5f
        private const val PERIOD_MILLIS = 1_600L

        fun forMotion(reduceMotion: Boolean): PulsePlan = PulsePlan(if (reduceMotion) 0L else PERIOD_MILLIS)
    }
}

/**
 * La fase en curso de [plan], como estado que se lee al dibujar (no recompone en cada
 * cuadro). Usa los cuadros de animación infinita, que las pruebas de Compose saben
 * distinguir: no las dejan ocupadas para siempre.
 */
@Composable
fun rememberPulsePhase(plan: PulsePlan): State<Float> {
    val phase = remember(plan) { mutableFloatStateOf(plan.phaseAt(0L)) }
    if (!plan.isStatic) {
        LaunchedEffect(plan) {
            val start = withInfiniteAnimationFrameMillis { it }
            while (true) {
                withInfiniteAnimationFrameMillis { now -> phase.floatValue = plan.phaseAt(now - start) }
            }
        }
    }
    return phase
}

/**
 * Halo que respira alrededor del elemento: un relleno del color dado que se abre por
 * fuera de su forma y se desvanece al abrirse. Se dibuja DETRÁS, sin ocupar espacio
 * ni recibir toques, así que el elemento (con su propio fondo) queda encima y solo se
 * ve el aro. Decorativo: la etiqueta de al lado dice qué hacer.
 */
fun Modifier.pulseHalo(phase: State<Float>, color: Color, cornerRadius: Dp): Modifier = drawBehind {
    val progress = phase.value
    val spread = (HALO_MIN_SPREAD + (HALO_MAX_SPREAD - HALO_MIN_SPREAD) * progress).toPx()
    val alpha = HALO_MAX_ALPHA - (HALO_MAX_ALPHA - HALO_MIN_ALPHA) * progress
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(-spread, -spread),
        size = Size(size.width + 2 * spread, size.height + 2 * spread),
        cornerRadius = CornerRadius(cornerRadius.toPx() + spread)
    )
}

/**
 * Flecha que apunta hacia abajo, al elemento que la sigue, meciéndose con la fase.
 * Dibujada (sin recurso nuevo) y sin semántica: es decorativa, como el halo.
 */
@Composable
fun CueArrow(phase: State<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(width = ARROW_WIDTH, height = ARROW_HEIGHT)
            .graphicsLayer { translationY = (ARROW_TRAVEL * phase.value).toPx() }
    ) {
        val stroke = ARROW_STROKE.toPx()
        val centerX = size.width / 2
        val tip = Offset(centerX, size.height - stroke)
        drawLine(color, Offset(centerX, stroke), tip, strokeWidth = stroke, cap = StrokeCap.Round)
        val head = size.width / 2 - stroke
        drawLine(color, Offset(centerX - head, tip.y - head), tip, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(centerX + head, tip.y - head), tip, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Ancho de [CueArrow]: lo usan quienes la alinean con el centro de otro elemento. */
val CueArrowWidth = 20.dp

private val ARROW_WIDTH = CueArrowWidth
private val ARROW_HEIGHT = 28.dp
private val ARROW_STROKE = 2.5.dp
private val ARROW_TRAVEL = 5.dp
private val HALO_MIN_SPREAD = 4.dp
private val HALO_MAX_SPREAD = 12.dp
private const val HALO_MIN_ALPHA = 0.18f
private const val HALO_MAX_ALPHA = 0.5f

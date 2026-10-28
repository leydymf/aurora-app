package com.auroraclima.app.core.ui.v2

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import kotlin.math.PI
import kotlin.math.cos

/**
 * Calendario de un movimiento cíclico suave (v0.3.3; desde v0.3.4, la respiración del
 * círculo a mano de la guía, [breathing]). Es puro, como el de la transición de
 * entrada: la pantalla solo le pregunta la fase de cada instante, así que el
 * movimiento se prueba en la JVM.
 *
 * Con la preferencia de reducir animaciones el calendario es quieto: la fase queda
 * fija a la mitad y no se pide ningún cuadro.
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

        /** Quieto: el de «Quitar animaciones», y el de una señal que ya se mudó a otro sitio. */
        val still = PulsePlan(periodMillis = 0L)

        fun forMotion(reduceMotion: Boolean): PulsePlan = if (reduceMotion) still else PulsePlan(PERIOD_MILLIS)

        private const val BREATH_PERIOD_MILLIS = 3_200L

        /** La respiración muy lenta del círculo a mano de la guía (v0.3.4); quieta con animaciones reducidas. */
        fun breathing(reduceMotion: Boolean): PulsePlan = if (reduceMotion) still else PulsePlan(BREATH_PERIOD_MILLIS)
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

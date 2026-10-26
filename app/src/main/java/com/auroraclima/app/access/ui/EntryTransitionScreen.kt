package com.auroraclima.app.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.rememberReduceMotion
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2Voice
import kotlinx.coroutines.delay

/**
 * Transición de entrada: tras teclear el código de acceso correcto en la
 * fachada, la marca de Aurora se ve unos 700 ms antes de la pantalla siguiente,
 * para que la persona sepa dónde está. El destino lo decide el NavHost
 * ([onFinished]), que además debe comprobar que la persona sigue aquí. Solo
 * aparece al entrar: la salida rápida sigue siendo inmediata y no pasa por aquí.
 *
 * Es una ruta del grafo seguro, así que el bloqueo de capturas y la nube de salida
 * rigen desde su primer cuadro (RNF-02); tocar la nube la abandona sin esperar. El
 * efecto de espera solo avanza con la entrada reanudada, y el NavHost vuelve a
 * comprobar el destino: la ventana de un cuadro entre el pop y la cancelación
 * no puede reabrir el interior.
 */
@Composable
fun EntryTransitionRoute(onFinished: () -> Unit) {
    val plan = EntryTransitionPlan.forMotion(reduceMotion = rememberReduceMotion())
    AuroraV2Theme(V2Voice.Interior) {
        EntryTransitionScreen(plan = plan, onFinished = onFinished)
    }
}

@Composable
internal fun EntryTransitionScreen(plan: EntryTransitionPlan, onFinished: () -> Unit) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    LaunchedEffect(plan) {
        if (plan.isStatic) {
            delay(plan.totalMillis)
        } else {
            val start = withFrameMillis { it }
            while (elapsedMillis < plan.totalMillis) {
                elapsedMillis = withFrameMillis { it } - start
            }
        }
        // Solo si esta entrada sigue a la vista: si la nube, atrás o el segundo
        // plano ya la sacaron (o la app está en pausa), no se avanza. Espera a
        // reanudar y se cancela si la entrada muere.
        lifecycle.withResumed { currentOnFinished() }
    }
    // Mismo fondo que la pantalla siguiente: al desvanecerse la marca no hay
    // salto de color. La opacidad se lee dentro de la capa para no recomponer
    // en cada cuadro.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground),
        contentAlignment = Alignment.Center
    ) {
        BrandHeader(modifier = Modifier.graphicsLayer { alpha = plan.brandAlphaAt(elapsedMillis) })
    }
}

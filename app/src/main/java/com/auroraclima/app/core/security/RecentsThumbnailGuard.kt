package com.auroraclima.app.core.security

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Momento en que el guard reevalúa la miniatura. Separarlos es lo que permite
 * probar la decisión sin emulador (la API de Android no tiene getter).
 */
internal enum class ThumbnailTrigger {
    /** La búsqueda se activó o se vació. */
    SearchChanged,

    /** La fachada volvió a primer plano. */
    ReturnedToForeground,

    /** Se abandonó la fachada (navegación o destrucción de la actividad). */
    LeftFacade
}

/**
 * Decisión del guard, aislada como lógica pura (auditoría A-1 del cierre
 * pre-merge). `true` habilita la miniatura, `false` la oculta y `null` significa
 * no tocar el flag.
 *
 * La regla: ocultar en cuanto hay texto en la barra, y **no restaurar mientras la
 * fachada no esté en primer plano**. Esto último es el latch: el ON_PAUSE que
 * vacía la barra baja [hidden] antes de que se dibuje el frame con la barra
 * vacía, así que restaurar en ese momento volvería a exponer el último frame —
 * el que todavía muestra el código a medio teclear.
 */
internal fun recentsThumbnailDecision(trigger: ThumbnailTrigger, hidden: Boolean, resumed: Boolean): Boolean? =
    when (trigger) {
        ThumbnailTrigger.SearchChanged -> when {
            hidden -> false
            resumed -> true
            else -> null
        }
        ThumbnailTrigger.ReturnedToForeground -> if (hidden) null else true
        // Al dejar la fachada se restaura siempre: dentro manda FLAG_SECURE, y
        // una app de clima sin miniatura sería un distinguidor (ADR 2026-10-05).
        ThumbnailTrigger.LeftFacade -> true
    }

/**
 * Miniatura de recientes de la fachada (RF-14, cierre de P-14): mientras la barra
 * de búsqueda está activa el sistema no debe fotografiar el código a medio
 * teclear — la limpieza en ON_PAUSE llega tarde, el snapshot se toma del último
 * frame. Con [hidden] la miniatura muestra el splash (API 33+) y se restaura al
 * volver a la fachada normal o al salir del composable, nunca con la actividad
 * en segundo plano (ver [recentsThumbnailDecision]). El precio del latch: tras
 * buscar y pulsar Inicio, la tarjeta de recientes muestra el splash hasta que la
 * fachada vuelve al frente — indistinguible de un arranque reciente.
 *
 * [hidden] NO es clave del efecto a propósito: al cambiar una clave, Compose
 * ejecuta el `onDispose` del efecto viejo, que restauraría la miniatura con la
 * actividad ya pausada y anularía el latch (auditoría A-1).
 *
 * Único punto de la app que toca esta API (vedada fuera de core/security por
 * `NoTracesGuardTest`). En API < 33 no existe y queda el mejor esfuerzo de
 * ON_PAUSE; ese residual vive en P-84.
 */
@Composable
fun RecentsThumbnailGuard(hidden: Boolean) {
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentHidden = rememberUpdatedState(hidden)
    DisposableEffect(activity, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                activity.applyThumbnailDecision(
                    ThumbnailTrigger.ReturnedToForeground,
                    hidden = currentHidden.value,
                    resumed = true
                )
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Valores reales aunque esta rama los ignore: así el sitio de llamada
            // no miente si algún día `LeftFacade` pasa a depender de ellos.
            activity.applyThumbnailDecision(
                ThumbnailTrigger.LeftFacade,
                hidden = currentHidden.value,
                resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            )
        }
    }
    LaunchedEffect(activity, lifecycleOwner, hidden) {
        activity.applyThumbnailDecision(
            ThumbnailTrigger.SearchChanged,
            hidden = hidden,
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }
}

private fun Activity?.applyThumbnailDecision(trigger: ThumbnailTrigger, hidden: Boolean, resumed: Boolean) {
    val enabled = recentsThumbnailDecision(trigger, hidden, resumed) ?: return
    setRecentsThumbnailEnabled(enabled)
}

private fun Activity?.setRecentsThumbnailEnabled(enabled: Boolean) {
    if (this != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setRecentsScreenshotEnabled(enabled)
    }
}

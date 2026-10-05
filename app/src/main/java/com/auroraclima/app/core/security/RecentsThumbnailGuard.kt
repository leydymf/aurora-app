package com.auroraclima.app.core.security

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Miniatura de recientes de la fachada (RF-14, cierre de P-14): mientras la barra
 * de búsqueda está activa el sistema no debe fotografiar el código a medio
 * teclear — la limpieza en ON_PAUSE llega tarde, el snapshot se toma del último
 * frame. Con [hidden] la miniatura muestra el splash (API 33+); al volver a la
 * fachada normal, o al salir del composable, se restaura la miniatura real: una
 * app de clima sin miniatura siempre sería un distinguidor (ADR 2026-10-05).
 * Único punto de la app que toca esta API (vedada fuera de core/security por
 * `NoTracesGuardTest`). En API < 33 no existe y queda el mejor esfuerzo de ON_PAUSE.
 */
@Composable
fun RecentsThumbnailGuard(hidden: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, hidden) {
        activity.setRecentsThumbnailEnabled(!hidden)
        onDispose { activity.setRecentsThumbnailEnabled(true) }
    }
}

private fun Activity?.setRecentsThumbnailEnabled(enabled: Boolean) {
    if (this != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setRecentsScreenshotEnabled(enabled)
    }
}

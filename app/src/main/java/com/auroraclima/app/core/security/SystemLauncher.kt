package com.auroraclima.app.core.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Plomería única para lanzar una pantalla DEL SISTEMA desde el espacio
 * interno (selector de fotos, cámara, archivos, crear documento, compartir):
 * arma la ida-y-vuelta esperada ([SessionManager.expectSystemReturn]) para que
 * el ON_STOP de la pantalla externa no vacíe la pila, y si el lanzamiento
 * falla la desarma ([SessionManager.cancelSystemReturn]) y avisa. Extraída al
 * aparecer el segundo consumidor (adjuntos RF-33 y exportación RF-45) para
 * que ninguna vía pueda olvidar el `cancelSystemReturn`.
 */
@Composable
fun rememberSystemLauncher(onLaunchFailed: () -> Unit): (launch: () -> Unit) -> Unit {
    val sessionManager = LocalSessionManager.current
    return remember(sessionManager, onLaunchFailed) {
        { launch ->
            sessionManager.expectSystemReturn()
            runCatching(launch).onFailure {
                sessionManager.cancelSystemReturn()
                onLaunchFailed()
            }
        }
    }
}

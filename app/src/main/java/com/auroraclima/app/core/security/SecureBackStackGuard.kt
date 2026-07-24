package com.auroraclima.app.core.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import java.util.UUID

/**
 * Ausencia de indicios en el back stack (RF-06, hallazgo M-02): si la app pasa a
 * background estando dentro del grafo seguro, el grafo completo se saca de la pila,
 * de modo que volver desde recientes (o tras muerte de proceso) siempre cae en la
 * fachada de clima y el botón atrás jamás reabre el espacio interno.
 *
 * Los cambios de configuración (rotación) no expulsan: la usuaria sigue en pantalla.
 * La restauración se revisa también al componer, porque en API 26-27 el estado de
 * navegación se guarda antes de ON_STOP y podría restaurar una ruta interna.
 *
 * [suppressStopPop] (RF-33): una ida-y-vuelta esperada a una pantalla del sistema
 * (selector de archivos, cámara) también dispara ON_STOP, pero la usuaria DEBE
 * regresar a su formulario con lo elegido — limpiarle la pila rompería el flujo.
 * La excepción la gobierna `SessionManager.isAwaitingSystemReturn()`: de un solo
 * uso, con caducidad, y con la sesión aún bajo el plazo de gracia y el temporizador
 * de inactividad (si no vuelve a tiempo, el cierre de sesión hace el pop igual).
 */
@Composable
fun SecureBackStackGuard(
    navController: NavController,
    secureGraphRoute: String,
    facadeRoute: String,
    suppressStopPop: () -> Boolean = { false }
) {
    // Síncrono en la fase de apply, ANTES del primer draw (auditoría A2): una
    // restauración tras muerte de proceso que caiga dentro del grafo seguro vuelve
    // a la fachada sin que la pantalla interna llegue a dibujarse ni un frame.
    // El token distingue muerte de proceso de recreación (auditoría N1): la rotación
    // restaura en el MISMO proceso (token igual) y no expulsa. Solo API 26-27 puede
    // persistir una ruta interna (guarda estado antes de ON_STOP); en 28+ el pop de
    // ON_STOP ocurre antes del guardado y la pila persistida ya está limpia.
    val savedProcessToken = rememberSaveable { mutableStateOf(ProcessToken.current) }
    DisposableEffect(navController) {
        if (savedProcessToken.value != ProcessToken.current) {
            navController.exitSecureGraph(secureGraphRoute, facadeRoute)
            savedProcessToken.value = ProcessToken.current
        }
        onDispose { }
    }
    AppBackgroundObserver(
        onStopWithoutRotation = {
            if (!suppressStopPop()) navController.exitSecureGraph(secureGraphRoute, facadeRoute)
        }
    )
}

/** Pop compartido de las guardias: si la ruta actual está dentro del grafo seguro, vuelve a la fachada. */
internal fun NavController.exitSecureGraph(secureGraphRoute: String, facadeRoute: String) {
    val insideSecureGraph = currentDestination?.hierarchy?.any { it.route == secureGraphRoute } == true
    if (insideSecureGraph) {
        popBackStack(route = facadeRoute, inclusive = false)
    }
}

/**
 * Marcador de vida del proceso: sobrevive recreaciones de la Activity (rotación)
 * pero no a la muerte del proceso. `object` manual: estado de plataforma sin lógica
 * de negocio, registrado en la adenda N1 del ADR 2026-07-09 (mismo criterio que la
 * excepción del 2026-07-08 para SecureFlagCounter).
 */
private object ProcessToken {
    val current: String = UUID.randomUUID().toString()
}

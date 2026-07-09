package com.auroraclima.app.core.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy

/**
 * Conecta la sesión del espacio interno con la navegación y el ciclo de vida
 * (RF-16, RNF-03). Complementa a [SecureBackStackGuard] (que limpia la PILA en
 * ON_STOP): este guardián gobierna la SESIÓN.
 *
 * - Entrar al grafo seguro inicia la sesión (idempotente).
 * - Todo cierre de sesión — salida rápida, inactividad, background prolongado —
 *   saca el grafo seguro de la pila con el mismo pop del resto de guardias: la
 *   fachada aparece en el siguiente frame con la barra vacía (RF-16 criterio 2)
 *   y el botón atrás no puede regresar (criterio 3).
 * - ON_STOP sin rotación arranca el plazo de gracia; ON_START lo cancela.
 */
@Composable
fun SessionGuard(
    navController: NavController,
    sessionManager: SessionManager,
    secureGraphRoute: String,
    facadeRoute: String
) {
    SyncSessionWithDestination(navController, sessionManager, secureGraphRoute)
    PopToFacadeOnSessionClose(navController, sessionManager, secureGraphRoute, facadeRoute)
    AppBackgroundObserver(
        onStopWithoutRotation = sessionManager::appMovedToBackground,
        onStart = sessionManager::appReturnedToForeground
    )
}

/**
 * La sesión sigue al destino: entrar al grafo la inicia y salir de él EN PRIMER
 * PLANO (back físico o salida rápida) la cierra en el acto — todo camino de salida
 * converge en la misma limpieza (auditoría H-4). El pop que dispara ON_STOP no
 * cierra aquí (el ciclo de vida ya no está STARTED): a ese caso lo gobierna el
 * plazo de gracia de background.
 */
@Composable
private fun SyncSessionWithDestination(
    navController: NavController,
    sessionManager: SessionManager,
    secureGraphRoute: String
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(navController, sessionManager, lifecycleOwner) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val insideSecureGraph = destination.hierarchy.any { it.route == secureGraphRoute }
            val inForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            when {
                insideSecureGraph -> sessionManager.startSession()
                inForeground -> sessionManager.closeSession()
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}

@Composable
private fun PopToFacadeOnSessionClose(
    navController: NavController,
    sessionManager: SessionManager,
    secureGraphRoute: String,
    facadeRoute: String
) {
    LaunchedEffect(navController, sessionManager) {
        sessionManager.closeEvents.collect {
            navController.exitSecureGraph(secureGraphRoute, facadeRoute)
        }
    }
}

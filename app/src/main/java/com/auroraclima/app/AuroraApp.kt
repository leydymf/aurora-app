package com.auroraclima.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auroraclima.app.access.ui.AccessPlaceholderScreen
import com.auroraclima.app.access.ui.ActivationPlaceholderScreen
import com.auroraclima.app.core.security.SearchGateResult
import com.auroraclima.app.core.security.SecureBackStackGuard
import com.auroraclima.app.core.security.secureGraph
import com.auroraclima.app.weather.ui.WeatherScreen
import com.auroraclima.app.weather.ui.WeatherViewModel

/**
 * Navegación raíz: la fachada de clima es el inicio y el único camino hacia el
 * espacio interno son los eventos de la puerta de búsqueda (RF-05 criterio 2, RF-12).
 * Las rutas usan nombres neutros porque quedan legibles en el APK (RNF-05).
 */
@Composable
fun AuroraApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) { HomeRoute(navController) }
        // Toda ruta interna vive dentro del grafo seguro: bloqueo de capturas y de
        // recientes garantizado por construcción (RNF-02), sin envolver pantalla por
        // pantalla. TODO(RF-16): la salida rápida del día 3 debe salir del grafo con
        // popBackStack(HOME_ROUTE, false).
        secureGraph(route = INNER_ROUTE, startDestination = INTRO_ROUTE) {
            screen(INTRO_ROUTE) { ActivationPlaceholderScreen() }
            screen(PANEL_ROUTE) { AccessPlaceholderScreen() }
        }
    }
    // Al pasar a background dentro del grafo seguro, la pila vuelve a la fachada:
    // recientes, muerte de proceso y botón atrás nunca reabren el espacio interno
    // sin código (RF-06, auditoría M-02 / P-10).
    SecureBackStackGuard(
        navController = navController,
        secureGraphRoute = INNER_ROUTE,
        facadeRoute = HOME_ROUTE
    )
}

/** Fachada de clima (RF-01..03) y puerta de búsqueda hacia el espacio interno (RF-12). */
@Composable
private fun HomeRoute(navController: NavHostController) {
    val viewModel: WeatherViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    // Mejor esfuerzo: limpiar la barra en ON_PAUSE para que la miniatura de
    // recientes no muestre un código a medio teclear (RF-14, auditoría M-03/M-04).
    // El snapshot lo toma el sistema del último frame visible, así que ON_STOP
    // llegaría tarde; la verificación en emulador queda en P-14 de PENDIENTES.md.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.onSearchCancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.gateEvents.collect { destination ->
            when (destination) {
                SearchGateResult.OpenActivation -> navController.navigate(INTRO_ROUTE)
                SearchGateResult.OpenAccess -> navController.navigate(PANEL_ROUTE)
                SearchGateResult.PassThrough -> Unit
            }
        }
    }
    WeatherScreen(
        state = state,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onCitySelected = viewModel::onCitySelected,
        onSearchCancel = viewModel::onSearchCancel
    )
}

private const val HOME_ROUTE = "home"
private const val INNER_ROUTE = "inner"
private const val INTRO_ROUTE = "intro"
private const val PANEL_ROUTE = "panel"

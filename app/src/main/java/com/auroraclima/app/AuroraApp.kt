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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auroraclima.app.access.ui.AccessPlaceholderScreen
import com.auroraclima.app.access.ui.ActivationPlaceholderScreen
import com.auroraclima.app.core.security.SearchGateResult
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
        composable(HOME_ROUTE) {
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
        // TODO(RF-15, RF-16): antes de poner contenido real en estas rutas (día 3), sacar
        // el grafo interno del back stack al pasar la app a background: la restauración
        // desde recientes o tras muerte de proceso NO debe caer dentro sin código
        // (auditoría M-02). Integrarlo con la salida rápida y el cierre de sesión.
        composable(INTRO_ROUTE) { ActivationPlaceholderScreen() }
        composable(PANEL_ROUTE) { AccessPlaceholderScreen() }
    }
}

private const val HOME_ROUTE = "home"
private const val INTRO_ROUTE = "intro"
private const val PANEL_ROUTE = "panel"

package com.auroraclima.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.auroraclima.app.core.security.secureGraph
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2TabBarHeight
import com.auroraclima.app.core.ui.v2.V2Voice

/**
 * [content] en una ruta del grafo seguro real con el inset del panel, como en la app:
 * la nube queda donde queda sobre la barra de pestañas. Lo comparten las pruebas que
 * arman la maquetación real de las pestañas (`InnerTabsLayout`).
 */
@Composable
internal fun InPanelRoute(content: @Composable () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = GRAPH) {
        secureGraph(route = GRAPH, startDestination = SCREEN) {
            screen(SCREEN, cloudBottomInset = V2TabBarHeight) {
                AuroraV2Theme(V2Voice.Interior) { content() }
            }
        }
    }
}

private const val GRAPH = "g"
private const val SCREEN = "s"

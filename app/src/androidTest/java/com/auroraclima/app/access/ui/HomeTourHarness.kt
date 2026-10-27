package com.auroraclima.app.access.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.auroraclima.app.R
import com.auroraclima.app.core.security.secureGraph
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.SpotlightTargets
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2TabBar
import com.auroraclima.app.core.ui.v2.V2TabBarHeight
import com.auroraclima.app.core.ui.v2.V2TabItem
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.spotlightTarget

/**
 * El Inicio con su barra de pestañas y el recorrido encima, dentro del grafo seguro
 * real: la misma composición que `InnerTabsHost` en la pestaña Inicio, sin los
 * ViewModels de Hilt (el paso lo da la prueba). La ruta declara el inset de la barra,
 * como el panel, así que la nube queda donde queda en la app.
 */
@Composable
internal fun HomeTourHarness(step: HomeTourStep?, onNext: () -> Unit, onSkip: () -> Unit, targets: SpotlightTargets) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = GRAPH) {
        secureGraph(route = GRAPH, startDestination = SCREEN) {
            screen(SCREEN, cloudBottomInset = V2TabBarHeight) {
                AuroraV2Theme(V2Voice.Interior) {
                    HomeTourLayer(step = step, onNext = onNext, onSkip = onSkip, targets = targets) {
                        HomeWithTabs()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWithTabs() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            PanelScreen(
                onStartGuidance = {},
                onOpenHistory = {},
                onOpenSettings = {},
                onCall = {},
                onSaveNote = {},
                onLearn = {}
            )
        }
        V2TabBar(
            items = listOf(
                V2TabItem(V2Icons.home, stringResource(R.string.access_tab_home)),
                V2TabItem(V2Icons.guide, stringResource(R.string.access_tab_guide)),
                V2TabItem(V2Icons.journal, stringResource(R.string.access_tab_journal))
            ),
            selectedIndex = 0,
            onSelect = {},
            itemModifier = { index ->
                when (index) {
                    1 -> Modifier.spotlightTarget(HomeTourStep.GUIDE)
                    2 -> Modifier.spotlightTarget(HomeTourStep.JOURNAL)
                    else -> Modifier
                }
            }
        )
    }
}

/** Las cadenas de un paso como las muestra el globo. */
internal fun tourTexts(context: Context, step: HomeTourStep): Triple<String, String, String> = Triple(
    context.getString(step.titleRes),
    context.getString(step.bodyRes),
    context.getString(R.string.access_tour_counter, step.number, HomeTourStep.count)
)

private const val GRAPH = "g"
private const val SCREEN = "s"

package com.auroraclima.app.access.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.auroraclima.app.HOME_TAB
import com.auroraclima.app.InPanelRoute
import com.auroraclima.app.InnerTabsLayout
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.SpotlightTargets

/**
 * El Inicio con su barra de pestañas y el recorrido encima, dentro del grafo seguro
 * real: la maquetación de la app (`InnerTabsLayout`) en la pestaña Inicio, sin los
 * ViewModels de Hilt (el paso lo da la prueba). La ruta declara el inset de la barra,
 * como el panel, así que la nube queda donde queda en la app.
 */
@Composable
internal fun HomeTourHarness(step: HomeTourStep?, onNext: () -> Unit, onSkip: () -> Unit, targets: SpotlightTargets) {
    InPanelRoute {
        HomeTourLayer(step = step, onNext = onNext, onSkip = onSkip, targets = targets) {
            InnerTabsLayout(selectedTab = HOME_TAB, onSelectTab = {}) {
                PanelScreen(
                    onStartGuidance = {},
                    onOpenHistory = {},
                    onOpenSettings = {},
                    onCall = {},
                    onSaveNote = {},
                    onLearn = {}
                )
            }
        }
    }
}

/** Las cadenas de un paso como las muestra el globo. */
internal fun tourTexts(context: Context, step: HomeTourStep): Triple<String, String, String> = Triple(
    context.getString(step.titleRes),
    context.getString(step.bodyRes),
    context.getString(R.string.access_tour_counter, step.number, HomeTourStep.count)
)

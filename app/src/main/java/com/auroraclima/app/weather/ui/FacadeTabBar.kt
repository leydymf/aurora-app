package com.auroraclima.app.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.V2TabBar
import com.auroraclima.app.core.ui.v2.V2TabItem

/** Pestañas de la fachada (v2-01/v2-03): Hoy · 5 días · Lugares. */
internal enum class FacadeTab { Today, Forecast, Places }

/**
 * Barra de pestañas compartida por P01 y P03. «Lugares» no es un destino propio:
 * abre la búsqueda/recientes de P01, la misma barra que es la puerta del código
 * (decisión D4 — RF-05, RF-12); por eso recibe acciones y no rutas.
 */
@Composable
internal fun FacadeTabBar(
    selectedTab: FacadeTab,
    onOpenToday: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenPlaces: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(onOpenToday, onOpenForecast, onOpenPlaces)
    V2TabBar(
        items = listOf(
            V2TabItem(R.drawable.ic_weather_cloud, stringResource(R.string.weather_tab_today)),
            V2TabItem(R.drawable.ic_weather_calendar, stringResource(R.string.weather_tab_forecast)),
            V2TabItem(R.drawable.ic_weather_pin, stringResource(R.string.weather_tab_places))
        ),
        selectedIndex = selectedTab.ordinal,
        onSelect = { index -> if (index != selectedTab.ordinal) actions[index]() },
        modifier = modifier
    )
}

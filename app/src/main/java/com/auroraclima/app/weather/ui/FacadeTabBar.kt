package com.auroraclima.app.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.V2TabBar
import com.auroraclima.app.core.ui.v2.V2TabItem

/** Pestañas de la fachada (v2-01/v2-03): Hoy · 5 días. */
internal enum class FacadeTab { Today, Forecast }

/** Barra de pestañas compartida por P01 y P03. */
@Composable
internal fun FacadeTabBar(
    selectedTab: FacadeTab,
    onOpenToday: () -> Unit,
    onOpenForecast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(onOpenToday, onOpenForecast)
    V2TabBar(
        items = listOf(
            V2TabItem(R.drawable.ic_weather_cloud, stringResource(R.string.weather_tab_today)),
            V2TabItem(R.drawable.ic_weather_calendar, stringResource(R.string.weather_tab_forecast))
        ),
        selectedIndex = selectedTab.ordinal,
        onSelect = { index -> if (index != selectedTab.ordinal) actions[index]() },
        modifier = modifier
    )
}

package com.auroraclima.app.weather.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La fachada muestra la carga y el error con reintento (H-1) tanto en la búsqueda como
 * en el clima de la ciudad elegida, con la misma pieza y sin texto del interior.
 */
@RunWith(AndroidJUnit4::class)
class WeatherLoadErrorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var searchRetries = 0
    private var weatherRetries = 0

    private fun show(state: WeatherUiState) {
        composeRule.setContent {
            AuroraV2Theme(V2Voice.Facade) {
                WeatherScreen(
                    state = state,
                    onSearchQueryChange = {},
                    onCitySelected = {},
                    onSearchCancel = {},
                    onOpenForecast = {},
                    onRetrySearch = { searchRetries++ },
                    onRetryWeather = { weatherRetries++ }
                )
            }
        }
    }

    @Test
    fun mientrasSeBuscaSeVeLaCarga() {
        show(WeatherUiState(searchQuery = "Cali", isSearchActive = true, isSearching = true))

        composeRule.onNodeWithContentDescription(string(R.string.weather_loading)).assertExists()
    }

    @Test
    fun siLaBusquedaFallaSeOfreceReintentar() {
        show(WeatherUiState(searchQuery = "Cali", isSearchActive = true, searchFailed = true))

        composeRule.onNodeWithText(string(R.string.weather_error_message)).assertExists()
        composeRule.onNodeWithText(string(R.string.weather_retry)).performClick()
        assertEquals(1, searchRetries)
        assertEquals(0, weatherRetries)
    }

    @Test
    fun mientrasCargaElClimaDeLaCiudadElegidaSeVeLaCarga() {
        show(WeatherUiState(isLoadingWeather = true))

        composeRule.onNodeWithContentDescription(string(R.string.weather_loading)).assertExists()
    }

    @Test
    fun siElClimaFallaSeOfreceReintentar() {
        show(WeatherUiState(weatherFailed = true))

        composeRule.onNodeWithText(string(R.string.weather_error_message)).assertExists()
        composeRule.onNodeWithText(string(R.string.weather_retry)).performClick()
        assertEquals(1, weatherRetries)
        assertEquals(0, searchRetries)
    }

    private fun string(id: Int) = composeRule.activity.getString(id)
}

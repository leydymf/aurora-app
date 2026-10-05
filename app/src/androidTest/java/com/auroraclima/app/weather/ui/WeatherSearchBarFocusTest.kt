package com.auroraclima.app.weather.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.theme.AuroraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresión del foco de la barra de búsqueda (RF-02): al teclear la primera
 * letra la pantalla pasa a modo búsqueda y el TextField NO debe recrearse —
 * si cambiara de posición en la composición, el foco se perdería, el teclado
 * se cerraría y habría que volver a tocar la barra para cada letra.
 */
@RunWith(AndroidJUnit4::class)
class WeatherSearchBarFocusTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchBarKeepsFocusAndTextAcrossActivation() {
        var lastQuery = ""
        composeRule.setContent {
            var state by remember { mutableStateOf(WeatherUiState(headerDate = "lun 6 jul")) }
            AuroraTheme {
                WeatherScreen(
                    state = state,
                    onSearchQueryChange = { query ->
                        lastQuery = query
                        // Réplica del ViewModel: la primera letra activa la búsqueda.
                        state = state.copy(searchQuery = query, isSearchActive = query.isNotEmpty())
                    },
                    onCitySelected = {},
                    onSearchCancel = {},
                    onOpenForecast = {}
                )
            }
        }

        val searchBar = composeRule.onNode(hasSetTextAction())
        searchBar.performTextInput("B")
        composeRule.waitForIdle()

        // El modo búsqueda quedó activo (apareció el icono de cancelar; desde la UI V2
        // es un botón de icono con descripción, no un texto)…
        val cancelLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.weather_search_cancel)
        composeRule.onNodeWithContentDescription(cancelLabel).assertExists()
        // …y la misma barra conserva el foco, lista para la segunda letra.
        composeRule.onNode(hasSetTextAction()).assertIsFocused()

        composeRule.onNode(hasSetTextAction()).performTextInput("u")
        composeRule.waitForIdle()
        assertEquals("Bu", lastQuery)
    }
}

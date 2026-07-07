package com.auroraclima.app.weather.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherViewModelTest {
    @Test
    fun `estado inicial tiene busqueda vacia y fecha formateada`() {
        val viewModel = WeatherViewModel()

        val state = viewModel.uiState.value

        assertEquals("", state.searchQuery)
        assertTrue(state.headerDate.isNotBlank())
    }

    @Test
    fun `cambiar la busqueda actualiza el estado`() {
        val viewModel = WeatherViewModel()

        viewModel.onSearchQueryChange("Bucara")

        assertEquals("Bucara", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `la fecha del encabezado sigue el formato del mockup P01`() {
        // lunes 6 de julio de 2026, como en alta-fidelidad-03
        assertEquals("lun 6 jul", formatHeaderDate(LocalDate.of(2026, 7, 6)))
    }
}

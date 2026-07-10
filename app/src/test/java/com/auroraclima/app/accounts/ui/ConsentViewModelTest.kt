package com.auroraclima.app.accounts.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentViewModelTest {
    private val viewModel = ConsentViewModel()

    @Test
    fun `la casilla nace sin preseleccionar`() {
        // RF-17 criterio 1: sin casillas preseleccionadas
        assertFalse(viewModel.uiState.value.isAccepted)
    }

    @Test
    fun `marcar y desmarcar la casilla actualiza el estado`() {
        viewModel.onAcceptanceChange(true)
        assertTrue(viewModel.uiState.value.isAccepted)

        viewModel.onAcceptanceChange(false)
        assertFalse(viewModel.uiState.value.isAccepted)
    }
}

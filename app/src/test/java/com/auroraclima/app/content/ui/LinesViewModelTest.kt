package com.auroraclima.app.content.ui

import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository()

    @Test
    fun `las lineas se cargan y se exponen sin error`() = runTest {
        // RF-23 criterio 1: se muestran las líneas vigentes.
        repository.linesResult = Result.success(
            listOf(line("l01", number = "123"), line("l02", order = 2, number = "155", hours = "24 horas"))
        )

        val viewModel = LinesViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("123", "155"), state.lines.map { it.number })
        assertNull(state.errorRes)
    }

    @Test
    fun `un fallo del repositorio se muestra solo como mensaje generico`() = runTest {
        // Primera apertura sin red (RF-26): mensaje genérico con reintento; el
        // detalle de la excepción jamás llega al estado de UI (P-24).
        repository.linesResult = Result.failure(RuntimeException("detalle interno del SDK"))

        val viewModel = LinesViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(R.string.content_error_generic, state.errorRes)
        assertEquals(emptyList<String>(), state.lines.map { it.id })
    }

    @Test
    fun `reintentar tras un fallo vuelve a consultar y recupera el contenido`() = runTest {
        // RF-26: al recuperar la red, el reintento deja la pantalla operativa.
        repository.linesResult = Result.failure(RuntimeException("sin red"))
        val viewModel = LinesViewModel(repository)
        advanceUntilIdle()

        repository.linesResult = Result.success(listOf(line("l01")))
        viewModel.onRetry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals(listOf("l01"), state.lines.map { it.id })
        assertEquals(2, repository.linesCalls)
    }
}

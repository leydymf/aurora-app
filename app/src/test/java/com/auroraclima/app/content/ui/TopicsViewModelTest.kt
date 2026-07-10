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
class TopicsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository()

    @Test
    fun `los temas se cargan y se exponen sin error`() = runTest {
        repository.topicsResult = Result.success(listOf(topic("t01"), topic("t02", order = 2)))

        val viewModel = TopicsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("t01", "t02"), state.topics.map { it.id })
        assertNull(state.errorRes)
    }

    @Test
    fun `un fallo del repositorio se muestra solo como mensaje generico`() = runTest {
        // El detalle de la excepción revela proyecto y colecciones: no debe llegar
        // al estado de UI en ninguna forma (P-24) — solo el recurso genérico.
        repository.topicsResult = Result.failure(RuntimeException("detalle interno del SDK"))

        val viewModel = TopicsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(R.string.content_error_generic, state.errorRes)
        assertEquals(emptyList<String>(), state.topics.map { it.id })
    }

    @Test
    fun `reintentar tras un fallo vuelve a consultar y recupera el contenido`() = runTest {
        repository.topicsResult = Result.failure(RuntimeException("sin red"))
        val viewModel = TopicsViewModel(repository)
        advanceUntilIdle()

        repository.topicsResult = Result.success(listOf(topic("t01")))
        viewModel.onRetry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals(listOf("t01"), state.topics.map { it.id })
        assertEquals(2, repository.topicsCalls)
    }
}

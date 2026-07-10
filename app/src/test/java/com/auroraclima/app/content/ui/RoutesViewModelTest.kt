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
class RoutesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository()

    @Test
    fun `los sectores con sus entidades se exponen sin error`() = runTest {
        repository.routesResult = Result.success(
            listOf(
                route("r01", agencies = listOf(agency("a01"), agency("a02"))),
                route("r02", order = 2)
            )
        )

        val viewModel = RoutesViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("r01", "r02"), state.routes.map { it.id })
        assertEquals(listOf("a01", "a02"), state.routes.first().agencies.map { it.id })
        assertNull(state.errorRes)
    }

    @Test
    fun `un fallo del repositorio se muestra solo como mensaje generico`() = runTest {
        repository.routesResult = Result.failure(RuntimeException("detalle interno del SDK"))

        val viewModel = RoutesViewModel(repository)
        advanceUntilIdle()

        assertEquals(R.string.content_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `reintentar tras un fallo recupera los sectores`() = runTest {
        repository.routesResult = Result.failure(RuntimeException("sin red"))
        val viewModel = RoutesViewModel(repository)
        advanceUntilIdle()

        repository.routesResult = Result.success(listOf(route("r01")))
        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals(listOf("r01"), viewModel.uiState.value.routes.map { it.id })
        assertNull(viewModel.uiState.value.errorRes)
        assertEquals(2, repository.routesCalls)
    }
}

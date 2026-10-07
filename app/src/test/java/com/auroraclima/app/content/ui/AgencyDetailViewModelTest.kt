package com.auroraclima.app.content.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgencyDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeContentRepository()

    private fun viewModel(routeId: String, agencyId: String) = AgencyDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(ROUTE_ID_ARG to routeId, AGENCY_ID_ARG to agencyId)),
        repository = repository
    )

    @Test
    fun `la entidad pedida se encuentra con el titulo de su sector`() = runTest {
        repository.routesResult = Result.success(
            listOf(route("r01", title = "Protección", agencies = listOf(agency("a01"), agency("a02"))))
        )

        val viewModel = viewModel("r01", "a02")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("a02", state.agency?.id)
        assertEquals("r01", state.routeId)
        assertEquals("Protección", state.routeTitle)
        assertNull(state.errorRes)
    }

    @Test
    fun `la seleccion visual del sector llega al estado y ausente queda nula`() = runTest {
        // Esquema v2 (ADR 2026-08-14): el ViewModel propaga icon/accent tal
        // cual; la resolución (y su degradación) es de la capa de presentación.
        repository.routesResult = Result.success(
            listOf(route("r01", agencies = listOf(agency("a01")), icon = "pin", accent = "sand"))
        )

        val viewModel = viewModel("r01", "a01")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("pin", state.routeIcon)
        assertEquals("sand", state.routeAccent)

        repository.routesResult = Result.success(
            listOf(route("r01", agencies = listOf(agency("a01"))))
        )
        viewModel.onRetry()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.routeIcon)
        assertNull(viewModel.uiState.value.routeAccent)
    }

    @Test
    fun `una entidad inexistente muestra el mensaje generico`() = runTest {
        repository.routesResult = Result.success(listOf(route("r01")))

        val viewModel = viewModel("r01", "desconocida")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.agency)
        assertEquals(R.string.content_error_generic, state.errorRes)
    }

    @Test
    fun `un fallo del repositorio muestra el mensaje generico`() = runTest {
        repository.routesResult = Result.failure(RuntimeException("detalle interno"))

        val viewModel = viewModel("r01", "a01")
        advanceUntilIdle()

        assertEquals(R.string.content_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `reintentar recarga la entidad`() = runTest {
        repository.routesResult = Result.failure(RuntimeException("sin red"))
        val viewModel = viewModel("r01", "a01")
        advanceUntilIdle()

        repository.routesResult = Result.success(listOf(route("r01", agencies = listOf(agency("a01")))))
        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals("a01", viewModel.uiState.value.agency?.id)
        assertNull(viewModel.uiState.value.errorRes)
    }
}

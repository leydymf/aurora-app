package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Detalle de un registro (RF-34): carga por id; un id que ya no existe y un
 * fallo de la BD cifrada convergen en el mismo error genérico (P-24/P-36).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJournalRepository()

    private fun viewModel(id: Long = 7) =
        JournalDetailViewModel(SavedStateHandle(mapOf(ENTRY_ID_ARG to id.toString())), repository)

    @Test
    fun `carga el registro y lo expone completo`() = runTest {
        val entry = JournalEntry(
            id = 7,
            occurredOn = LocalDate.of(2026, 6, 28),
            kind = EntryKind.ECONOMIC,
            detail = "tomó el dinero del mercado"
        )
        repository.entryResult = Result.success(entry)

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals(entry, state.entry)
    }

    @Test
    fun `un id inexistente muestra el error generico`() = runTest {
        repository.entryResult = Result.success(null)

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.errorRes)
        assertNull(state.entry)
    }

    @Test
    fun `un fallo de la BD se muestra solo como mensaje generico`() = runTest {
        repository.entryResult = Result.failure(IllegalStateException("blob corrupto: detalle interno"))

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `reintentar tras un fallo recupera el registro`() = runTest {
        repository.entryResult = Result.failure(IllegalStateException("fallo transitorio"))
        val viewModel = viewModel()
        advanceUntilIdle()

        repository.entryResult = Result.success(
            JournalEntry(7, LocalDate.of(2026, 6, 28), EntryKind.PHYSICAL, "x")
        )
        viewModel.onRetry()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorRes)
        assertEquals(7L, viewModel.uiState.value.entry?.id)
    }
}

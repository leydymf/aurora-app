package com.auroraclima.app.records.ui

import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Listado del historial (RF-34): expone los registros tal como los ordena el
 * repositorio (cronológico inverso), se refresca al volver, y todo fallo se
 * muestra solo como mensaje genérico (P-24/P-36, guía §3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJournalRepository()

    @Test
    fun `expone los registros en el orden del repositorio`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(2, "reciente"), entry(1, "antiguo")))
        val viewModel = JournalListViewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorRes)
        assertEquals(listOf("reciente", "antiguo"), state.entries.map { it.detail })
    }

    @Test
    fun `un fallo del repositorio se muestra solo como mensaje generico`() = runTest {
        repository.entriesResult = Result.failure(IllegalStateException("blob corrupto: detalle interno"))
        val viewModel = JournalListViewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.errorRes)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun `refrescar tras un fallo recupera el listado`() = runTest {
        repository.entriesResult = Result.failure(IllegalStateException("fallo transitorio"))
        val viewModel = JournalListViewModel(repository)
        viewModel.refresh()
        advanceUntilIdle()

        repository.entriesResult = Result.success(listOf(entry(1, "nuevo")))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals(1, state.entries.size)
        assertEquals(2, repository.entriesCalls)
    }

    private fun entry(id: Long, detail: String) = JournalEntry(
        id = id,
        occurredOn = LocalDate.of(2026, 7, 2),
        kind = EntryKind.PSYCHOLOGICAL,
        detail = detail
    )
}

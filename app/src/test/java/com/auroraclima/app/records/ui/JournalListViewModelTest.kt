package com.auroraclima.app.records.ui

import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.FakeSyncKeyManager
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
    private val attachments = FakeJournalAttachmentRepository()
    private val keyManager = FakeSyncKeyManager()

    private fun viewModel() = JournalListViewModel(repository, attachments, keyManager)

    @Test
    fun `expone los registros en el orden del repositorio`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(2, "reciente"), entry(1, "antiguo")))
        val viewModel = viewModel()

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
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.errorRes)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun `refrescar tras un fallo recupera el listado`() = runTest {
        repository.entriesResult = Result.failure(IllegalStateException("fallo transitorio"))
        val viewModel = viewModel()
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

    @Test
    fun `expone los contadores de adjuntos por registro`() = runTest {
        // RF-33 (P18): «· N ADJUNTOS» junto a la fecha de cada fila.
        repository.entriesResult = Result.success(listOf(entry(1, "x")))
        attachments.countsResult = Result.success(mapOf(1L to 2))
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(mapOf(1L to 2), viewModel.uiState.value.attachmentCounts)
    }

    @Test
    fun `un fallo de los contadores no rompe el listado`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "x")))
        attachments.countsResult = Result.failure(IllegalStateException("bd"))
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals(1, state.entries.size)
        assertTrue(state.attachmentCounts.isEmpty())
    }

    @Test
    fun `el aviso de respaldo reiniciado se muestra una vez y se puede cerrar`() = runTest {
        // RF-39: contraseña restablecida sin la anterior → mensaje genérico.
        keyManager.restartNotice = true

        val viewModel = viewModel()

        assertTrue(viewModel.uiState.value.showsBackupNotice)
        viewModel.onBackupNoticeDismissed()
        assertFalse(viewModel.uiState.value.showsBackupNotice)
        // Consumido: un ViewModel nuevo (otra visita) ya no lo muestra.
        assertFalse(viewModel().uiState.value.showsBackupNotice)
    }

    @Test
    fun `sin reinicio no hay aviso`() = runTest {
        assertFalse(viewModel().uiState.value.showsBackupNotice)
    }

    private fun entry(id: Long, detail: String) = JournalEntry(
        id = id,
        occurredOn = LocalDate.of(2026, 7, 2),
        kind = EntryKind.PSYCHOLOGICAL,
        detail = detail
    )
}

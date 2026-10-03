package com.auroraclima.app.records.ui

import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.catalog.FakeCategoryCatalog
import com.auroraclima.app.core.security.FakeSyncKeyManager
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.usecase.LoadEntryKindsUseCase
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
    private val catalog = FakeCategoryCatalog()

    private fun viewModel() = JournalListViewModel(repository, attachments, LoadEntryKindsUseCase(catalog), keyManager)

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

    @Test
    fun `resuelve el tipo de cada fila con el catalogo publicado`() = runTest {
        // P-68: nombre y visual vivos para los publicados; último nombre
        // conocido y sin visual para un tema retirado; neutro sin instantánea.
        repository.entriesResult = Result.success(
            listOf(
                entry(1, "a"),
                entry(2, "b", kindId = "t09", kindLabel = "Retirado"),
                entry(3, "c", kindId = "", kindLabel = "")
            )
        )
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val published = state.kindOf(state.entries[0])
        assertTrue(published.isPublished)
        assertEquals("Segunda", published.label)
        assertEquals("lobes", published.icon)
        val retired = state.kindOf(state.entries[1])
        assertFalse(retired.isPublished)
        assertEquals("Retirado", retired.label)
        assertNull(state.kindOf(state.entries[2]).label)
    }

    @Test
    fun `un fallo del catalogo no rompe el listado y las filas caen a su instantanea`() = runTest {
        catalog.result = Result.failure(IllegalStateException("sin red"))
        repository.entriesResult = Result.success(listOf(entry(1, "a", kindLabel = "Segunda (guardada)")))
        val viewModel = viewModel()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals(1, state.entries.size)
        val kind = state.kindOf(state.entries[0])
        assertFalse(kind.isPublished)
        assertEquals("Segunda (guardada)", kind.label)
    }

    private fun entry(id: Long, detail: String, kindId: String = "t02", kindLabel: String = "Segunda") = JournalEntry(
        id = id,
        occurredOn = LocalDate.of(2026, 7, 2),
        kindId = kindId,
        kindLabel = kindLabel,
        detail = detail
    )
}

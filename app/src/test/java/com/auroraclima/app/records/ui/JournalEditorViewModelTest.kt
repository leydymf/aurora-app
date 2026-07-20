package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.usecase.AddJournalEntryUseCase
import com.auroraclima.app.records.domain.usecase.UpdateJournalEntryUseCase
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
 * Creación (RF-32) y edición (RF-35) de un registro: validación en línea sin
 * pérdida de lo diligenciado (criterio 2), guardado protegido con error solo
 * genérico ante fallo del repositorio (P-36, P-24) y, en modo edición,
 * prellenado y re-sincronización del mismo id.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJournalRepository()
    private val sync = FakeJournalSyncRepository()

    private fun viewModel(entryId: Long? = null) = JournalEditorViewModel(
        SavedStateHandle(entryId?.let { mapOf(ENTRY_ID_ARG to it.toString()) }.orEmpty()),
        AddJournalEntryUseCase(repository, sync),
        UpdateJournalEntryUseCase(repository, sync),
        repository
    )

    private val stored = JournalEntry(
        id = 7,
        occurredOn = LocalDate.of(2026, 6, 28),
        kind = EntryKind.ECONOMIC,
        detail = "tomó el dinero del mercado"
    )

    @Test
    fun `guardar sin campos obligatorios señala ambos sin perder la fecha`() = runTest {
        val viewModel = viewModel()
        viewModel.onDaySelected(LocalDate.of(2026, 7, 2))

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.journal_kind_missing, state.kindErrorRes)
        assertEquals(R.string.journal_detail_missing, state.detailErrorRes)
        assertEquals(LocalDate.of(2026, 7, 2), state.occurredOn)
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `guardar sin tipo conserva el texto ya escrito`() = runTest {
        // RF-32 criterio 2: señalar lo que falta jamás borra lo diligenciado.
        val viewModel = viewModel()
        viewModel.onDetailChange("me quitó el dinero del mercado")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.journal_kind_missing, state.kindErrorRes)
        assertNull(state.detailErrorRes)
        assertEquals("me quitó el dinero del mercado", state.detail)
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `elegir el tipo despeja su error en linea`() = runTest {
        val viewModel = viewModel()
        viewModel.onDetailChange("texto")
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onKindSelected(EntryKind.ECONOMIC)

        assertNull(viewModel.uiState.value.kindErrorRes)
    }

    @Test
    fun `con todo diligenciado guarda recortado y emite el evento`() = runTest {
        val viewModel = viewModel()
        viewModel.onDaySelected(LocalDate.of(2026, 6, 28))
        viewModel.onKindSelected(EntryKind.ECONOMIC)
        viewModel.onDetailChange("  tomó el dinero del mercado  ")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, repository.added.size)
        val saved = repository.added[0]
        assertEquals(LocalDate.of(2026, 6, 28), saved.occurredOn)
        assertEquals(EntryKind.ECONOMIC, saved.kind)
        assertEquals("tomó el dinero del mercado", saved.detail)
        // RF-37: el registro guardado también se sincroniza (recortado, mismo contenido).
        assertEquals(1, sync.pushed.size)
        assertEquals("tomó el dinero del mercado", sync.pushed[0].detail)
        // replay = 1: el evento queda en la caché para la suscripción de la UI.
        assertEquals(listOf(Unit), viewModel.savedEvents.replayCache)
    }

    @Test
    fun `un fallo de la sync no rompe el guardado ni asoma error`() = runTest {
        // RF-37/§3: la nube es mejor esfuerzo; su fallo es silencioso para la UI.
        sync.pushResult = Result.failure(IllegalStateException("sin sesión"))
        val viewModel = viewModel()
        viewModel.onKindSelected(EntryKind.PHYSICAL)
        viewModel.onDetailChange("lo que pasó")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.saveErrorRes)
        assertEquals(1, repository.added.size)
        assertEquals(listOf(Unit), viewModel.savedEvents.replayCache)
    }

    @Test
    fun `sin guardar no hay evento emitido`() = runTest {
        val viewModel = viewModel()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertTrue(viewModel.savedEvents.replayCache.isEmpty())
    }

    @Test
    fun `en modo edicion prellena el formulario con el registro guardado`() = runTest {
        repository.entryResult = Result.success(stored)

        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditing)
        assertFalse(state.isLoading)
        assertEquals(stored.occurredOn, state.occurredOn)
        assertEquals(stored.kind, state.kind)
        assertEquals(stored.detail, state.detail)
    }

    @Test
    fun `en modo edicion guardar actualiza el registro y re-sincroniza el mismo id`() = runTest {
        repository.entryResult = Result.success(stored)
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        viewModel.onKindSelected(EntryKind.PSYCHOLOGICAL)
        viewModel.onDetailChange("  el relato corregido  ")
        viewModel.onSubmit()
        advanceUntilIdle()

        // Actualiza — no crea un registro nuevo (RF-35 criterio 1).
        assertTrue(repository.added.isEmpty())
        assertEquals(1, repository.updated.size)
        val saved = repository.updated[0]
        assertEquals(7L, saved.id)
        assertEquals(EntryKind.PSYCHOLOGICAL, saved.kind)
        assertEquals("el relato corregido", saved.detail)
        // El cambio se refleja en la sincronización, al MISMO documento.
        assertEquals(1, sync.pushed.size)
        assertEquals(7L, sync.pushed[0].id)
        assertEquals("el relato corregido", sync.pushed[0].detail)
        assertEquals(listOf(Unit), viewModel.savedEvents.replayCache)
    }

    @Test
    fun `en modo edicion un fallo de carga muestra el error generico y reintenta`() = runTest {
        repository.entryResult = Result.failure(IllegalStateException("blob corrupto: detalle interno"))
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.loadErrorRes)

        repository.entryResult = Result.success(stored)
        viewModel.onRetryLoad()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.loadErrorRes)
        assertEquals(stored.detail, state.detail)
    }

    @Test
    fun `un argumento de navegacion malformado no lanza y muestra el error generico`() = runTest {
        // Auditoría C3 H-1: `toLongOrNull` — un arg corrupto jamás debe crashear
        // ni dejar el string crudo en un stacktrace de logcat.
        val viewModel = JournalEditorViewModel(
            SavedStateHandle(mapOf(ENTRY_ID_ARG to "no-numerico")),
            AddJournalEntryUseCase(repository, sync),
            UpdateJournalEntryUseCase(repository, sync),
            repository
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEditing)
        assertEquals(R.string.common_error_generic, state.loadErrorRes)
    }

    @Test
    fun `en modo edicion un id que ya no existe muestra el error generico`() = runTest {
        repository.entryResult = Result.success(null)

        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.loadErrorRes)
    }

    @Test
    fun `un fallo del repositorio muestra solo el error generico y no pierde nada`() = runTest {
        // El detalle de la excepción (rutas, passphrase, SQL) jamás llega a la UI (P-24/P-36).
        repository.addResult = Result.failure(IllegalStateException("blob corrupto: detalle interno"))
        val viewModel = viewModel()
        viewModel.onKindSelected(EntryKind.PHYSICAL)
        viewModel.onDetailChange("lo que pasó")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.saveErrorRes)
        assertFalse(state.isSaving)
        assertEquals("lo que pasó", state.detail)
        assertEquals(EntryKind.PHYSICAL, state.kind)
    }
}

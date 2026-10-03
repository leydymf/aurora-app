package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.catalog.FakeCategoryCatalog
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.usecase.DeleteJournalEntryUseCase
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
 * Detalle de un registro (RF-34): carga por id; un id que ya no existe y un
 * fallo de la BD cifrada convergen en el mismo error genérico (P-24/P-36).
 * Eliminación (RF-36): confirmación en dos pasos, borrado local + propagación
 * y error solo genérico si el borrado local falla.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJournalRepository()
    private val attachments = FakeJournalAttachmentRepository()
    private val sync = FakeJournalSyncRepository()
    private val catalog = FakeCategoryCatalog()

    private fun viewModel(id: Long = 7) = JournalDetailViewModel(
        SavedStateHandle(mapOf(ENTRY_ID_ARG to id.toString())),
        repository,
        attachments,
        DeleteJournalEntryUseCase(repository, attachments, sync),
        LoadEntryKindsUseCase(catalog)
    )

    private val entry = JournalEntry(
        id = 7,
        occurredOn = LocalDate.of(2026, 6, 28),
        kindId = "t04",
        kindLabel = "Cuarta",
        detail = "tomó el dinero del mercado"
    )

    @Test
    fun `carga el registro y lo expone completo`() = runTest {
        repository.entryResult = Result.success(entry)

        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals(entry, state.entry)
        // P-68: el tipo se resuelve contra el catálogo publicado.
        assertTrue(state.kind?.isPublished == true)
        assertEquals("Cuarta", state.kind?.label)
        assertEquals("card", state.kind?.icon)
    }

    @Test
    fun `un tipo retirado se muestra con su ultimo nombre y un fallo del catalogo no rompe el detalle`() = runTest {
        catalog.result = Result.failure(IllegalStateException("sin red"))
        repository.entryResult = Result.success(entry.copy(kindId = "t09", kindLabel = "Retirado"))

        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertFalse(state.kind?.isPublished == true)
        assertEquals("Retirado", state.kind?.label)
    }

    @Test
    fun `carga los adjuntos del registro para la seccion del detalle`() = runTest {
        // RF-33 criterio 2: visibles en el detalle, solo dentro de la app.
        repository.entryResult = Result.success(entry)
        attachments.attachmentsResult = Result.success(
            listOf(
                JournalAttachment(4, AttachmentKind.IMAGE),
                JournalAttachment(5, AttachmentKind.AUDIO)
            )
        )

        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.attachments.size)
        assertTrue(state.hasAudio)
    }

    @Test
    fun `un fallo al leer adjuntos no rompe el detalle`() = runTest {
        repository.entryResult = Result.success(entry)
        attachments.attachmentsResult = Result.failure(IllegalStateException("bd"))

        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals(entry, state.entry)
        assertTrue(state.attachments.isEmpty())
    }

    @Test
    fun `un id inexistente muestra el error generico`() = runTest {
        repository.entryResult = Result.success(null)

        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.errorRes)
        assertNull(state.entry)
    }

    @Test
    fun `un fallo de la BD se muestra solo como mensaje generico`() = runTest {
        repository.entryResult = Result.failure(IllegalStateException("blob corrupto: detalle interno"))

        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `reintentar tras un fallo recupera el registro`() = runTest {
        repository.entryResult = Result.failure(IllegalStateException("fallo transitorio"))
        val viewModel = viewModel()
        viewModel.refresh()
        advanceUntilIdle()

        repository.entryResult = Result.success(
            JournalEntry(7, LocalDate.of(2026, 6, 28), "t01", "Primera", "x")
        )
        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorRes)
        assertEquals(7L, viewModel.uiState.value.entry?.id)
    }

    @Test
    fun `un argumento de navegacion malformado no lanza y muestra el error generico`() = runTest {
        // Auditoría C3 H-1: `toLongOrNull` — un arg corrupto jamás debe crashear
        // ni dejar el string crudo en un stacktrace de logcat.
        val viewModel = JournalDetailViewModel(
            SavedStateHandle(mapOf(ENTRY_ID_ARG to "no-numerico")),
            repository,
            attachments,
            DeleteJournalEntryUseCase(repository, attachments, sync),
            LoadEntryKindsUseCase(catalog)
        )
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
        // Confirmar sin id tampoco hace nada.
        viewModel.onConfirmDelete()
        advanceUntilIdle()
        assertTrue(repository.deleted.isEmpty())
    }

    @Test
    fun `eliminar exige el segundo paso y puede cancelarse sin tocar nada`() = runTest {
        // RF-36 criterio 1: pedir eliminar solo abre la confirmación.
        val viewModel = viewModel()
        viewModel.onRequestDelete()

        assertTrue(viewModel.uiState.value.isConfirmingDelete)
        assertTrue(repository.deleted.isEmpty())

        viewModel.onDismissDelete()

        assertFalse(viewModel.uiState.value.isConfirmingDelete)
        assertTrue(repository.deleted.isEmpty())
        assertTrue(sync.removed.isEmpty())
    }

    @Test
    fun `confirmar la eliminacion borra local, propaga a la nube y emite el evento`() = runTest {
        val viewModel = viewModel()
        viewModel.onRequestDelete()

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        // RF-36 criterio 2: desaparece del dispositivo y la propagación va al mismo id.
        assertEquals(listOf(7L), repository.deleted)
        assertEquals(listOf(7L), sync.removed)
        assertEquals(listOf(Unit), viewModel.deletedEvents.replayCache)
    }

    @Test
    fun `un fallo del borrado local muestra solo el error generico y no emite`() = runTest {
        repository.deleteResult = Result.failure(IllegalStateException("blob corrupto: detalle interno"))
        val viewModel = viewModel()
        viewModel.onRequestDelete()

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.deleteErrorRes)
        assertFalse(state.isDeleting)
        assertTrue(state.isConfirmingDelete)
        assertTrue(viewModel.deletedEvents.replayCache.isEmpty())
        // El borrado local falló: la nube no se toca (el registro sigue existiendo).
        assertTrue(sync.removed.isEmpty())
    }

    @Test
    fun `un fallo de la propagacion remota no impide la eliminacion`() = runTest {
        // §3: la nube es mejor esfuerzo; sin red el delete queda en la cola durable.
        sync.removeResult = Result.failure(IllegalStateException("sin red"))
        val viewModel = viewModel()
        viewModel.onRequestDelete()

        viewModel.onConfirmDelete()
        advanceUntilIdle()

        assertEquals(listOf(7L), repository.deleted)
        assertEquals(listOf(Unit), viewModel.deletedEvents.replayCache)
        assertNull(viewModel.uiState.value.deleteErrorRes)
    }
}

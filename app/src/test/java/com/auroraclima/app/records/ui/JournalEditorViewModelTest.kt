package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.usecase.AddJournalEntryUseCase
import com.auroraclima.app.records.domain.usecase.UpdateJournalEntryUseCase
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
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
    private val attachments = FakeJournalAttachmentRepository()
    private val sync = FakeJournalSyncRepository()

    private fun viewModel(entryId: Long? = null) = JournalEditorViewModel(
        SavedStateHandle(entryId?.let { mapOf(ENTRY_ID_ARG to it.toString()) }.orEmpty()),
        AddJournalEntryUseCase(repository, attachments, sync),
        UpdateJournalEntryUseCase(repository, sync),
        repository,
        attachments
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
        // RF-37: el registro guardado también se sincroniza con su id local
        // (el contenido lo lee la implementación desde la fila, RF-39).
        assertEquals(listOf(1L), sync.pushed)
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
        assertEquals(listOf(7L), sync.pushed)
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
            AddJournalEntryUseCase(repository, attachments, sync),
            UpdateJournalEntryUseCase(repository, sync),
            repository,
            attachments
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
    fun `al abrir barre huerfanos de creaciones abandonadas`() = runTest {
        viewModel()
        advanceUntilIdle()

        assertEquals(1, attachments.sweepCalls)
    }

    @Test
    fun `una imagen elegida se importa en preparacion al crear`() = runTest {
        // RF-33: sin registro guardado aún, el dueño es el marcador de preparación.
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onImagePicked("content://picked/1")
        advanceUntilIdle()

        assertEquals(listOf("content://picked/1" to AttachmentPolicy.STAGED_OWNER), attachments.imported)
    }

    @Test
    fun `en edicion la imagen se asocia directo al registro`() = runTest {
        repository.entryResult = Result.success(stored)
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        viewModel.onImagePicked("content://picked/1")
        advanceUntilIdle()

        assertEquals(listOf("content://picked/1" to 7L), attachments.imported)
    }

    @Test
    fun `cancelar el selector no importa nada`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onImagePicked(null)
        viewModel.onDocumentPicked(null)
        advanceUntilIdle()

        assertTrue(attachments.imported.isEmpty())
    }

    @Test
    fun `guardar al crear reclama los adjuntos importados`() = runTest {
        attachments.attachmentsResult = Result.success(
            listOf(JournalAttachment(4, AttachmentKind.IMAGE), JournalAttachment(9, AttachmentKind.DOCUMENT))
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onKindSelected(EntryKind.PHYSICAL)
        viewModel.onDetailChange("lo que pasó")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf(listOf(4L, 9L) to 1L), attachments.claimed)
    }

    @Test
    fun `pedir camara arma el destino y emite el evento de lanzamiento`() = runTest {
        attachments.beginCaptureResult = Result.success("content://capture/9")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onRequestCapture()
        advanceUntilIdle()

        assertEquals("content://capture/9", viewModel.captureEvents.first())
    }

    @Test
    fun `el evento de captura es de consumo unico`() = runTest {
        // Auditoría E1 H-1: una recreación de la Activity (rotación) vuelve a
        // colectar el flujo; un replay relanzaría la cámara — y re-armaría la
        // ida-y-vuelta — sin gesto de la usuaria. Consumido una vez, no hay más.
        attachments.beginCaptureResult = Result.success("content://capture/9")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onRequestCapture()
        advanceUntilIdle()

        assertEquals("content://capture/9", viewModel.captureEvents.first())
        // Segunda suscripción (la recomposición tras recrear): nada que entregar.
        assertNull(withTimeoutOrNull(1_000) { viewModel.captureEvents.first() })
    }

    @Test
    fun `la captura confirmada se importa y la cancelada se descarta`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCaptureResult(saved = true)
        advanceUntilIdle()
        assertEquals(listOf(AttachmentPolicy.STAGED_OWNER), attachments.capturesImported)

        viewModel.onCaptureResult(saved = false)
        advanceUntilIdle()
        assertEquals(1, attachments.discardCalls)
    }

    @Test
    fun `un fallo de importacion muestra solo el mensaje generico de adjuntos`() = runTest {
        attachments.importResult = Result.failure(IllegalStateException("tipo no permitido: detalle interno"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onImagePicked("content://picked/1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.journal_media_error, state.mediaErrorRes)
        assertFalse(state.isImporting)
    }

    @Test
    fun `al maximo de adjuntos rechaza con el mensaje de tope sin importar`() = runTest {
        attachments.attachmentsResult = Result.success(
            (1..AttachmentPolicy.MAX_PER_ENTRY).map { JournalAttachment(it.toLong(), AttachmentKind.IMAGE) }
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onImagePicked("content://picked/extra")
        advanceUntilIdle()

        assertEquals(R.string.journal_media_full, viewModel.uiState.value.mediaErrorRes)
        assertTrue(attachments.imported.isEmpty())
        assertFalse(viewModel.uiState.value.canAttach)
    }

    @Test
    fun `quitar un adjunto lo elimina y refresca la lista`() = runTest {
        attachments.attachmentsResult = Result.success(listOf(JournalAttachment(4, AttachmentKind.IMAGE)))
        val viewModel = viewModel()
        advanceUntilIdle()

        attachments.attachmentsResult = Result.success(emptyList())
        viewModel.onRemoveAttachment(4)
        advanceUntilIdle()

        assertEquals(listOf(4L), attachments.deleted)
        assertTrue(viewModel.uiState.value.attachments.isEmpty())
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

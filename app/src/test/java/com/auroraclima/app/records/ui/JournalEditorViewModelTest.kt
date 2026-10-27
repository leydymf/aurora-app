package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.catalog.FakeCategoryCatalog
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.AttachmentNotSavedException
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.ResolvedKind
import com.auroraclima.app.records.domain.model.UNSPECIFIED_KIND_ID
import com.auroraclima.app.records.domain.usecase.AddJournalEntryUseCase
import com.auroraclima.app.records.domain.usecase.LoadEntryKindsUseCase
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
    private val mediaSync = FakeJournalMediaSyncRepository()
    private val catalog = FakeCategoryCatalog()

    private fun viewModel(entryId: Long? = null) = JournalEditorViewModel(
        SavedStateHandle(entryId?.let { mapOf(ENTRY_ID_ARG to it.toString()) }.orEmpty()),
        AddJournalEntryUseCase(repository, attachments, sync, mediaSync),
        UpdateJournalEntryUseCase(repository, sync),
        LoadEntryKindsUseCase(catalog),
        repository,
        attachments
    )

    private val stored = JournalEntry(
        id = 7,
        occurredOn = LocalDate.of(2026, 6, 28),
        kindId = "t04",
        kindLabel = "Cuarta",
        detail = "tomó el dinero del mercado"
    )

    /** Chip publicado tal como lo ofrece el selector tras cargar el catálogo. */
    private fun published(id: String, label: String, icon: String, accent: String) =
        ResolvedKind(id = id, label = label, icon = icon, accent = accent, isPublished = true)

    private val fourth = published("t04", "Cuarta", "card", "sand")
    private val second = published("t02", "Segunda", "lobes", "lilac")
    private val first = published("t01", "Primera", "figure", "blue")

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

        viewModel.onKindSelected(fourth)

        assertNull(viewModel.uiState.value.kindErrorRes)
    }

    @Test
    fun `con todo diligenciado guarda recortado y emite el evento`() = runTest {
        val viewModel = viewModel()
        viewModel.onDaySelected(LocalDate.of(2026, 6, 28))
        viewModel.onKindSelected(fourth)
        viewModel.onDetailChange("  tomó el dinero del mercado  ")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, repository.added.size)
        val saved = repository.added[0]
        assertEquals(LocalDate.of(2026, 6, 28), saved.occurredOn)
        assertEquals("t04", saved.kindId)
        // Instantánea del nombre publicado al guardar (último nombre conocido, P-68).
        assertEquals("Cuarta", saved.kindLabel)
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
        viewModel.onKindSelected(first)
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
        assertEquals(stored.kindId, state.kindId)
        assertEquals(stored.detail, state.detail)
    }

    @Test
    fun `en modo edicion guardar actualiza el registro y re-sincroniza el mismo id`() = runTest {
        repository.entryResult = Result.success(stored)
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        viewModel.onKindSelected(second)
        viewModel.onDetailChange("  el relato corregido  ")
        viewModel.onSubmit()
        advanceUntilIdle()

        // Actualiza — no crea un registro nuevo (RF-35 criterio 1).
        assertTrue(repository.added.isEmpty())
        assertEquals(1, repository.updated.size)
        val saved = repository.updated[0]
        assertEquals(7L, saved.id)
        assertEquals("t02", saved.kindId)
        assertEquals("Segunda", saved.kindLabel)
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
            AddJournalEntryUseCase(repository, attachments, sync, mediaSync),
            UpdateJournalEntryUseCase(repository, sync),
            LoadEntryKindsUseCase(catalog),
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
    fun `cancelar cualquier selector deja el editor exactamente como estaba`() = runTest {
        // H-5: volver del selector sin elegir (también en un teléfono sin archivos)
        // no borra lo escrito, no marca error y no deja una importación a medias.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onKindSelected(first)
        viewModel.onDetailChange("lo que pasó")
        val before = viewModel.uiState.value

        viewModel.onImagePicked(null)
        viewModel.onDocumentPicked(null)
        viewModel.onCaptureResult(saved = false)
        advanceUntilIdle()

        assertEquals(before, viewModel.uiState.value)
        assertFalse(viewModel.uiState.value.isImporting)
        assertNull(viewModel.uiState.value.mediaErrorRes)
        assertEquals("lo que pasó", viewModel.uiState.value.detail)
    }

    @Test
    fun `la primera vez el selector pide un aviso y no se abre hasta aceptarlo`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPickerRequested(PickerSource.DOCUMENT)

        assertEquals(PickerSource.DOCUMENT, viewModel.uiState.value.pendingPicker)
        assertNull(withTimeoutOrNull(1_000) { viewModel.pickerEvents.first() })

        viewModel.onPickerTipConfirmed()

        assertEquals(PickerSource.DOCUMENT, viewModel.pickerEvents.first())
        assertNull(viewModel.uiState.value.pendingPicker)
        assertTrue(viewModel.uiState.value.hasSeenPickerTip)
    }

    @Test
    fun `con el aviso ya aceptado el selector se abre directo`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPickerRequested(PickerSource.GALLERY)
        viewModel.onPickerTipConfirmed()
        assertEquals(PickerSource.GALLERY, viewModel.pickerEvents.first())

        viewModel.onPickerRequested(PickerSource.DOCUMENT)

        assertNull(viewModel.uiState.value.pendingPicker)
        assertEquals(PickerSource.DOCUMENT, viewModel.pickerEvents.first())
    }

    @Test
    fun `ahora no cierra el aviso sin abrir nada y lo vuelve a mostrar la proxima vez`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPickerRequested(PickerSource.DOCUMENT)

        viewModel.onPickerTipDismissed()

        assertNull(viewModel.uiState.value.pendingPicker)
        assertFalse(viewModel.uiState.value.hasSeenPickerTip)
        assertNull(withTimeoutOrNull(1_000) { viewModel.pickerEvents.first() })
        viewModel.onPickerRequested(PickerSource.DOCUMENT)
        assertEquals(PickerSource.DOCUMENT, viewModel.uiState.value.pendingPicker)
    }

    @Test
    fun `sin cupo el selector no se abre ni pide aviso`() = runTest {
        attachments.attachmentsResult = Result.success(
            List(AttachmentPolicy.MAX_PER_ENTRY) { JournalAttachment(it + 1L, AttachmentKind.IMAGE) }
        )
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPickerRequested(PickerSource.GALLERY)

        assertNull(viewModel.uiState.value.pendingPicker)
        assertEquals(R.string.journal_media_full, viewModel.uiState.value.mediaErrorRes)
    }

    @Test
    fun `guardar al crear reclama los adjuntos importados`() = runTest {
        attachments.attachmentsResult = Result.success(
            listOf(JournalAttachment(4, AttachmentKind.IMAGE), JournalAttachment(9, AttachmentKind.DOCUMENT))
        )
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onKindSelected(first)
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
    fun `si el adjunto no quedo integro avisa que no se guardo y deja seguir en el editor`() = runTest {
        attachments.importResult = Result.failure(AttachmentNotSavedException())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onDetailChange("lo escrito")

        viewModel.onDocumentPicked("content://picked/2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.journal_media_save_error, state.mediaErrorRes)
        assertFalse(state.isImporting)
        assertTrue("no queda ningún adjunto a medias", state.attachments.isEmpty())
        assertEquals("lo escrito sigue ahí", "lo escrito", state.detail)

        // El aviso no bloquea: el siguiente intento se hace como siempre.
        attachments.importResult = null
        viewModel.onDocumentPicked("content://picked/3")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.mediaErrorRes)
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
        viewModel.onKindSelected(first)
        viewModel.onDetailChange("lo que pasó")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.saveErrorRes)
        assertFalse(state.isSaving)
        assertEquals("lo que pasó", state.detail)
        assertEquals("t01", state.kindId)
    }

    // ---- Catálogo de tipos (RF-32 refinado 2026-10-03, cierre P-68) ----

    @Test
    fun `los chips son los temas publicados en su orden con su visual`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingKinds)
        assertFalse(state.kindsUnavailable)
        assertEquals(listOf("t01", "t02", "t03", "t04"), state.kindOptions.map { it.id })
        assertEquals(listOf("Primera", "Segunda", "Tercera", "Cuarta"), state.kindOptions.map { it.label })
        assertEquals(first, state.kindOptions[0])
        assertTrue(state.kindOptions.all { it.isPublished })
    }

    @Test
    fun `sin catalogo ofrece la opcion neutra y registrar sigue siendo posible`() = runTest {
        // RNF-08: sin red ni caché de contenido el formulario no se bloquea.
        catalog.result = Result.failure(IllegalStateException("sin red ni caché"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.kindsUnavailable)
        assertEquals(1, state.kindOptions.size)
        val neutral = state.kindOptions[0]
        assertEquals(UNSPECIFIED_KIND_ID, neutral.id)
        assertNull(neutral.label)
        assertFalse(neutral.isPublished)

        viewModel.onKindSelected(neutral)
        viewModel.onDetailChange("lo que pasó")
        viewModel.onSubmit()
        advanceUntilIdle()

        val saved = repository.added.single()
        assertEquals(UNSPECIFIED_KIND_ID, saved.kindId)
        assertEquals("", saved.kindLabel)
        assertEquals(listOf(Unit), viewModel.savedEvents.replayCache)
    }

    @Test
    fun `un catalogo vacio se trata como no disponible`() = runTest {
        catalog.result = Result.success(emptyList())
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.kindsUnavailable)
        assertEquals(listOf(UNSPECIFIED_KIND_ID), state.kindOptions.map { it.id })
    }

    @Test
    fun `reintentar el catalogo reemplaza la opcion neutra por los temas publicados`() = runTest {
        catalog.result = Result.failure(IllegalStateException("sin red"))
        val viewModel = viewModel()
        advanceUntilIdle()

        catalog.result = Result.success(FakeCategoryCatalog.defaultCategories)
        viewModel.onRetryKinds()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.kindsUnavailable)
        assertEquals(listOf("t01", "t02", "t03", "t04"), state.kindOptions.map { it.id })
        assertEquals(2, catalog.calls)
    }

    @Test
    fun `si la opcion neutra estaba elegida al crear y llega el catalogo, la eleccion se suelta`() = runTest {
        // El chip neutro desaparece con el catálogo: no debe quedar un «Sin
        // tipo» invisible seleccionado — la usuaria vuelve a elegir.
        catalog.result = Result.failure(IllegalStateException("sin red"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onKindSelected(viewModel.uiState.value.kindOptions.single())

        catalog.result = Result.success(FakeCategoryCatalog.defaultCategories)
        viewModel.onRetryKinds()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.kindId)
        assertEquals(listOf("t01", "t02", "t03", "t04"), state.kindOptions.map { it.id })
    }

    @Test
    fun `sin tipo elegido no se guarda aunque el catalogo no este disponible`() = runTest {
        // RF-32 criterio 2 se mantiene: la opción neutra hay que elegirla.
        catalog.result = Result.failure(IllegalStateException("sin red"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onDetailChange("texto")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(R.string.journal_kind_missing, viewModel.uiState.value.kindErrorRes)
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `en edicion un tipo retirado sigue siendo una opcion con su ultimo nombre`() = runTest {
        repository.entryResult = Result.success(stored.copy(kindId = "t09", kindLabel = "Nombre que tuvo"))
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("t09", state.kindId)
        val retired = state.kindOptions.last()
        assertEquals("t09", retired.id)
        assertEquals("Nombre que tuvo", retired.label)
        assertFalse(retired.isPublished)
        assertEquals(listOf("t01", "t02", "t03", "t04", "t09"), state.kindOptions.map { it.id })

        // Guardar sin tocar el tipo conserva id e instantánea.
        viewModel.onDetailChange("corregido")
        viewModel.onSubmit()
        advanceUntilIdle()
        assertEquals("t09" to "Nombre que tuvo", repository.updated.single().let { it.kindId to it.kindLabel })
    }

    @Test
    fun `en edicion un registro sin tipo ofrece la opcion neutra y permite clasificarlo`() = runTest {
        repository.entryResult = Result.success(stored.copy(kindId = UNSPECIFIED_KIND_ID, kindLabel = ""))
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        assertEquals(
            listOf("t01", "t02", "t03", "t04", UNSPECIFIED_KIND_ID),
            viewModel.uiState.value.kindOptions.map { it.id }
        )

        viewModel.onKindSelected(second)
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals("t02" to "Segunda", repository.updated.single().let { it.kindId to it.kindLabel })
    }

    @Test
    fun `al editar sin tocar el tipo la instantanea toma el nombre publicado vigente`() = runTest {
        // La administradora renombró el tema: al guardar, el registro
        // actualiza su último nombre conocido sin que la usuaria lo re-elija.
        repository.entryResult = Result.success(stored.copy(kindLabel = "Nombre viejo"))
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        viewModel.onDetailChange("corregido")
        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals("t04" to "Cuarta", repository.updated.single().let { it.kindId to it.kindLabel })
    }

    @Test
    fun `en edicion un tipo publicado no duplica el chip`() = runTest {
        repository.entryResult = Result.success(stored)
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        assertEquals(listOf("t01", "t02", "t03", "t04"), viewModel.uiState.value.kindOptions.map { it.id })
    }

    // Salir con cambios sin guardar (v0.3.2): la flecha y el gesto atrás preguntan.

    @Test
    fun `al crear sin tocar nada volver sale sin preguntar`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onBackRequested()

        assertFalse(viewModel.uiState.value.isLeavePromptVisible)
        assertEquals(Unit, withTimeoutOrNull(1_000) { viewModel.leaveEvents.first() })
    }

    @Test
    fun `con texto escrito volver pregunta y no sale`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onDetailChange("algo")

        viewModel.onBackRequested()

        assertTrue(viewModel.uiState.value.isLeavePromptVisible)
        assertNull(withTimeoutOrNull(1_000) { viewModel.leaveEvents.first() })
    }

    @Test
    fun `confirmar la salida sale y quedarse cierra la pregunta`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onKindSelected(fourth)
        viewModel.onBackRequested()

        viewModel.onLeaveDismissed()
        assertFalse(viewModel.uiState.value.isLeavePromptVisible)
        assertNull(withTimeoutOrNull(1_000) { viewModel.leaveEvents.first() })

        viewModel.onBackRequested()
        viewModel.onLeaveConfirmed()
        assertFalse(viewModel.uiState.value.isLeavePromptVisible)
        assertEquals(Unit, withTimeoutOrNull(1_000) { viewModel.leaveEvents.first() })
    }

    @Test
    fun `al crear un adjunto importado cuenta como cambio`() = runTest {
        attachments.attachmentsResult = Result.success(listOf(JournalAttachment(4, AttachmentKind.IMAGE)))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `en edicion sin tocar nada no hay cambios aunque tenga adjuntos`() = runTest {
        repository.entryResult = Result.success(stored)
        attachments.attachmentsResult = Result.success(listOf(JournalAttachment(4, AttachmentKind.IMAGE)))
        val viewModel = viewModel(entryId = 7)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        viewModel.onDaySelected(LocalDate.of(2026, 6, 29))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        // Volver a lo guardado ya no es un cambio.
        viewModel.onDaySelected(stored.occurredOn)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun `dos toques seguidos a volver salen una sola vez`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onBackRequested()
        viewModel.onBackRequested()

        assertEquals(Unit, withTimeoutOrNull(1_000) { viewModel.leaveEvents.first() })
        assertNull(withTimeoutOrNull(1_000) { viewModel.leaveEvents.first() })
    }

    @Test
    fun `mientras se guarda volver no hace nada`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onKindSelected(fourth)
        viewModel.onDetailChange("lo que pasó")
        viewModel.onSubmit()
        // Sin avanzar el planificador: el guardado sigue en curso.
        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.onBackRequested()

        assertFalse(viewModel.uiState.value.isLeavePromptVisible)
        assertNull(withTimeoutOrNull(1_000) { viewModel.leaveEvents.first() })
    }
}

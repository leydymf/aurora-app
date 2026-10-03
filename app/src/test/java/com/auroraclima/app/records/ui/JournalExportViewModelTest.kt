package com.auroraclima.app.records.ui

import androidx.lifecycle.SavedStateHandle
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.catalog.FakeCategoryCatalog
import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.DayProvider
import com.auroraclima.app.records.domain.model.ExportPolicy
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.usecase.BuildExportDocumentUseCase
import com.auroraclima.app.records.domain.usecase.LoadEntryKindsUseCase
import com.auroraclima.app.records.domain.usecase.LoadExportSummaryUseCase
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
 * Pantalla de exportación (RF-45): resumen, interruptor apagado por defecto,
 * validación de la contraseña, salida por «crear documento» (sin temporal) o
 * por «compartir» (temporal que se barre al volver), y errores solo genéricos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalExportViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJournalRepository()
    private val attachments = FakeJournalAttachmentRepository()
    private val catalog = FakeCategoryCatalog()
    private val exportRepository = FakeJournalExportRepository()
    private val today = LocalDate.of(2026, 10, 3)

    private fun viewModel(ids: String = "2,1") = JournalExportViewModel(
        SavedStateHandle(mapOf(ENTRY_IDS_ARG to ids)),
        LoadExportSummaryUseCase(repository, attachments),
        LoadEntryKindsUseCase(catalog),
        BuildExportDocumentUseCase(repository, attachments, LoadEntryKindsUseCase(catalog)),
        exportRepository,
        DayProvider { today }
    )

    private fun seedEntries() {
        repository.entriesResult = Result.success(listOf(entry(2), entry(1)))
    }

    private suspend fun readyViewModel(ids: String = "2,1"): JournalExportViewModel {
        seedEntries()
        val viewModel = viewModel(ids)
        advanceUntilIdleNow()
        return viewModel
    }

    private fun advanceUntilIdleNow() = mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `carga el resumen con los ids de la ruta y el interruptor nace apagado`() = runTest {
        val viewModel = readyViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(2L, 1L), state.summary?.rows?.map { it.id })
        assertFalse(state.includeAttachments)
        assertEquals("Segunda", state.kindOf(state.summary!!.rows.first()).label)
        // Al abrir se barre cualquier temporal de una exportación anterior.
        assertEquals(1, exportRepository.clearCalls)
    }

    @Test
    fun `ids malformados o inexistentes muestran el error generico`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1)))
        val viewModel = viewModel("x,99")
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
        assertNull(viewModel.uiState.value.summary)
    }

    @Test
    fun `una contrasena corta o que no coincide no lanza nada`() = runTest {
        val viewModel = readyViewModel()

        viewModel.onPasswordChanged("corta")
        viewModel.onConfirmationChanged("corta")
        viewModel.onSaveRequested()
        advanceUntilIdle()
        assertEquals(R.string.journal_export_password_short, viewModel.uiState.value.passwordErrorRes)

        viewModel.onPasswordChanged("larga-y-valida-10")
        viewModel.onConfirmationChanged("larga-y-distinta-10")
        viewModel.onShareRequested()
        advanceUntilIdle()
        assertEquals(R.string.journal_export_password_mismatch, viewModel.uiState.value.passwordErrorRes)

        assertNull(withTimeoutOrNull(500) { viewModel.launchEvents.first() })
        assertTrue(exportRepository.exported.isEmpty())
        assertTrue(exportRepository.staged.isEmpty())
    }

    @Test
    fun `guardar pide crear el documento con el nombre neutro y escribe directo al uri`() = runTest {
        val viewModel = readyViewModel()
        viewModel.onPasswordChanged("clave-segura-1")
        viewModel.onConfirmationChanged("clave-segura-1")

        viewModel.onSaveRequested()
        advanceUntilIdle()
        assertEquals(ExportLaunch.Save("documento_2026-10-03.pdf"), viewModel.launchEvents.first())

        viewModel.onSaveTarget("content://docs/tree/1")
        advanceUntilIdle()

        val (uri, document, password) = exportRepository.exported.single()
        assertEquals("content://docs/tree/1", uri)
        assertEquals("clave-segura-1", password)
        assertEquals(listOf(2L, 1L).map { "relato $it" }, document.entries.map { it.detail })
        assertEquals("Segunda", document.entries.first().kindLabel)
        assertTrue(exportRepository.staged.isEmpty())
        val state = viewModel.uiState.value
        assertTrue(state.isSaved)
        assertFalse(state.isExporting)
        // La contraseña no se queda en el estado tras exportar.
        assertEquals("", state.password)
        assertEquals("", state.confirmation)
    }

    @Test
    fun `cancelar el selector de guardado no genera nada`() = runTest {
        val viewModel = readyViewModel()
        viewModel.onPasswordChanged("clave-segura-1")
        viewModel.onConfirmationChanged("clave-segura-1")
        viewModel.onSaveRequested()
        advanceUntilIdle()

        viewModel.onSaveTarget(null)
        advanceUntilIdle()

        assertTrue(exportRepository.exported.isEmpty())
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `incluir adjuntos los lee todos y lo refleja en el documento`() = runTest {
        attachments.attachmentsResult = Result.success(
            listOf(JournalAttachment(1, AttachmentKind.IMAGE), JournalAttachment(2, AttachmentKind.DOCUMENT))
        )
        attachments.exportableById[1] = AttachmentBytes(AttachmentKind.IMAGE, "image/jpeg", byteArrayOf(9))
        attachments.exportableById[2] = AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", byteArrayOf(8))
        val viewModel = readyViewModel("1")
        assertEquals(2, viewModel.uiState.value.summary?.attachmentCount)

        viewModel.onIncludeAttachmentsChanged(true)
        viewModel.onPasswordChanged("clave-segura-1")
        viewModel.onConfirmationChanged("clave-segura-1")
        viewModel.onSaveTarget("content://docs/tree/2")
        advanceUntilIdle()

        val document = exportRepository.exported.single().second
        assertTrue(document.includesAttachments)
        assertEquals(
            listOf(AttachmentKind.IMAGE, AttachmentKind.DOCUMENT),
            document.entries.single().attachments.map { it.kind }
        )
        assertTrue(document.entries.single().omittedAttachments.isEmpty())
    }

    @Test
    fun `con adjuntos demasiado pesados el interruptor no se enciende`() = runTest {
        attachments.attachmentsResult = Result.success(
            listOf(JournalAttachment(1, AttachmentKind.DOCUMENT, ExportPolicy.MAX_TOTAL_ATTACHMENT_BYTES + 1))
        )
        val viewModel = readyViewModel("1")
        assertFalse(viewModel.uiState.value.summary!!.canIncludeAttachments)

        viewModel.onIncludeAttachmentsChanged(true)

        assertFalse(viewModel.uiState.value.includeAttachments)
    }

    @Test
    fun `un error de memoria al exportar termina en el mensaje generico y no en una caida`() = runTest {
        exportRepository.throwOnExport = OutOfMemoryError("sin memoria")
        val viewModel = readyViewModel()
        viewModel.onPasswordChanged("clave-segura-1")
        viewModel.onConfirmationChanged("clave-segura-1")

        viewModel.onSaveTarget("content://docs/tree/4")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.errorRes)
        assertFalse(state.isExporting)
        assertFalse(state.isSaved)
    }

    @Test
    fun `compartir escribe el temporal, lanza el selector y al volver lo barre`() = runTest {
        val viewModel = readyViewModel()
        viewModel.onPasswordChanged("clave-segura-1")
        viewModel.onConfirmationChanged("clave-segura-1")

        viewModel.onShareRequested()
        advanceUntilIdle()

        assertEquals(1, exportRepository.staged.size)
        assertEquals(ExportLaunch.Share(exportRepository.stageResult.getOrThrow()), viewModel.launchEvents.first())
        assertFalse(viewModel.uiState.value.isSaved)
        val clearsBefore = exportRepository.clearCalls

        viewModel.onShareReturned()
        advanceUntilIdle()
        assertEquals(clearsBefore + 1, exportRepository.clearCalls)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("", viewModel.uiState.value.confirmation)
    }

    @Test
    fun `el lanzamiento es de consumo unico`() = runTest {
        val viewModel = readyViewModel()
        viewModel.onPasswordChanged("clave-segura-1")
        viewModel.onConfirmationChanged("clave-segura-1")
        viewModel.onSaveRequested()
        advanceUntilIdle()

        assertEquals(ExportLaunch.Save("documento_2026-10-03.pdf"), viewModel.launchEvents.first())
        // Segunda suscripción (recreación de la Activity): nada que relanzar.
        assertNull(withTimeoutOrNull(500) { viewModel.launchEvents.first() })
    }

    @Test
    fun `un fallo al escribir muestra solo el mensaje generico y no marca guardado`() = runTest {
        exportRepository.exportResult = Result.failure(IllegalStateException("detalle interno del proveedor"))
        val viewModel = readyViewModel()
        viewModel.onPasswordChanged("clave-segura-1")
        viewModel.onConfirmationChanged("clave-segura-1")

        viewModel.onSaveTarget("content://docs/tree/3")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.errorRes)
        assertFalse(state.isSaved)
        assertFalse(state.isExporting) // El documento que el selector ya había creado no queda vacío en la carpeta.
        assertEquals(listOf("content://docs/tree/3"), exportRepository.discarded)
    }

    @Test
    fun `el estado nunca imprime la contrasena`() = runTest {
        val viewModel = readyViewModel()
        viewModel.onPasswordChanged("clave-reservada-123")

        assertFalse(viewModel.uiState.value.toString().contains("clave-reservada-123"))
    }

    @Test
    fun `si el sistema no abre el selector se avisa y se barre el temporal`() = runTest {
        val viewModel = readyViewModel()
        val clearsBefore = exportRepository.clearCalls

        viewModel.onLaunchFailed()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
        assertEquals(clearsBefore + 1, exportRepository.clearCalls)
    }

    private fun entry(id: Long) = JournalEntry(
        id = id,
        occurredOn = LocalDate.of(2026, 7, id.toInt()),
        kindId = "t02",
        kindLabel = "Instantánea",
        detail = "relato $id"
    )
}

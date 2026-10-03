package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.ui.FakeJournalAttachmentRepository
import com.auroraclima.app.records.ui.FakeJournalRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resumen previo a exportar (RF-45 criterio 2): filas en orden y conteo de imágenes sin descifrar. */
class LoadExportSummaryUseCaseTest {
    private val repository = FakeJournalRepository()
    private val attachments = FakeJournalAttachmentRepository()

    @Test
    fun `resume los registros elegidos en el orden del listado y cuenta solo las imagenes`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(3), entry(2), entry(1)))
        attachments.attachmentsResult = Result.success(
            listOf(JournalAttachment(1, AttachmentKind.IMAGE), JournalAttachment(2, AttachmentKind.AUDIO))
        )

        val summary = LoadExportSummaryUseCase(repository, attachments)(listOf(1, 3)).getOrThrow()

        assertEquals(listOf(3L, 1L), summary.rows.map { it.id })
        assertEquals(2, summary.entryCount)
        assertEquals(2, summary.imageCount)
        assertEquals("Segunda", summary.rows.first().kindLabel)
    }

    @Test
    fun `sin ids validos falla`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1)))

        assertTrue(LoadExportSummaryUseCase(repository, attachments)(listOf(7)).isFailure)
        assertTrue(LoadExportSummaryUseCase(repository, attachments)(emptyList()).isFailure)
    }

    private fun entry(id: Long) = JournalEntry(id, LocalDate.of(2026, 7, 2), "t02", "Segunda", "relato")
}

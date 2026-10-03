package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.core.catalog.FakeCategoryCatalog
import com.auroraclima.app.records.domain.model.AttachmentContent
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.ui.FakeJournalAttachmentRepository
import com.auroraclima.app.records.ui.FakeJournalRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Armado del contenido del PDF (RF-45 criterio 3): fecha, tipo con el nombre
 * vivo del catálogo (P-68), relato, imágenes solo si se pidieron y adjuntos
 * no imagen citados sin descifrarse.
 */
class BuildExportDocumentUseCaseTest {
    private val repository = FakeJournalRepository()
    private val attachments = CountingAttachmentRepository()
    private val catalog = FakeCategoryCatalog()
    private val today = LocalDate.of(2026, 10, 3)

    private fun useCase() = BuildExportDocumentUseCase(repository, attachments, LoadEntryKindsUseCase(catalog))

    @Test
    fun `usa el nombre vivo del catalogo y conserva el orden del listado`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(2, "t02", "Vieja"), entry(1, "t01", "Vieja")))

        // Los ids llegan en cualquier orden; el PDF sigue el del listado (reciente primero).
        val document = useCase()(listOf(1, 2), includeImages = false, today = today).getOrThrow()

        assertEquals(listOf("Segunda", "Primera"), document.entries.map { it.kindLabel })
        assertEquals(today, document.createdOn)
    }

    @Test
    fun `un tema retirado cae a la instantanea y sin instantanea queda sin tipo`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t99", "Retirado"), entry(2, "", "")))

        val document = useCase()(listOf(1, 2), includeImages = false, today = today).getOrThrow()

        assertEquals("Retirado", document.entries[0].kindLabel)
        assertNull(document.entries[1].kindLabel)
    }

    @Test
    fun `sin catalogo el nombre sale de la instantanea`() = runTest {
        catalog.result = Result.failure(IllegalStateException("sin red"))
        repository.entriesResult = Result.success(listOf(entry(1, "t02", "Guardado")))

        val document = useCase()(listOf(1), includeImages = false, today = today).getOrThrow()

        assertEquals("Guardado", document.entries.single().kindLabel)
    }

    @Test
    fun `sin incluir imagenes no se descifra nada y todos los adjuntos se citan`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))
        attachments.attachmentsResult = Result.success(
            listOf(
                attachment(1, AttachmentKind.IMAGE),
                attachment(2, AttachmentKind.DOCUMENT),
                attachment(3, AttachmentKind.AUDIO)
            )
        )

        val document = useCase()(listOf(1), includeImages = false, today = today).getOrThrow()

        val entry = document.entries.single()
        assertTrue(entry.images.isEmpty())
        assertEquals(
            listOf(AttachmentKind.IMAGE, AttachmentKind.DOCUMENT, AttachmentKind.AUDIO),
            entry.omittedAttachments
        )
        assertEquals(0, attachments.contentCalls)
    }

    @Test
    fun `al incluir imagenes solo se descifran las de esa familia`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))
        attachments.attachmentsResult = Result.success(
            listOf(
                attachment(1, AttachmentKind.IMAGE),
                attachment(2, AttachmentKind.DOCUMENT),
                attachment(3, AttachmentKind.IMAGE)
            )
        )
        attachments.contentResult = Result.success(AttachmentContent.Image(byteArrayOf(1, 2, 3)))

        val document = useCase()(listOf(1), includeImages = true, today = today).getOrThrow()

        val entry = document.entries.single()
        assertEquals(2, entry.images.size)
        assertEquals(listOf(AttachmentKind.DOCUMENT), entry.omittedAttachments)
        // Jamás se pide el contenido de un documento: eso crearía una copia descifrada en caché.
        assertEquals(listOf(1L, 3L), attachments.contentIds)
        assertTrue(document.includesImages)
    }

    @Test
    fun `los ids inexistentes se omiten y sin ninguno valido falla`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))

        assertEquals(1, useCase()(listOf(1, 99), includeImages = false, today = today).getOrThrow().entries.size)
        assertTrue(useCase()(listOf(99), includeImages = false, today = today).isFailure)
    }

    @Test
    fun `un fallo al leer el historial o los adjuntos se propaga como fallo`() = runTest {
        repository.entriesResult = Result.failure(IllegalStateException("bd"))
        assertTrue(useCase()(listOf(1), includeImages = false, today = today).isFailure)

        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))
        attachments.attachmentsResult = Result.failure(IllegalStateException("almacén"))
        assertTrue(useCase()(listOf(1), includeImages = false, today = today).isFailure)
    }

    private fun entry(id: Long, kindId: String, kindLabel: String) = JournalEntry(
        id = id,
        occurredOn = LocalDate.of(2026, 7, 2),
        kindId = kindId,
        kindLabel = kindLabel,
        detail = "relato $id"
    )

    private fun attachment(id: Long, kind: AttachmentKind) = JournalAttachment(id, kind)

    /** El fake base no cuenta llamadas a `content`: aquí es el criterio central. */
    private class CountingAttachmentRepository : FakeJournalAttachmentRepository() {
        val contentIds = mutableListOf<Long>()
        val contentCalls: Int
            get() = contentIds.size

        override suspend fun content(id: Long): Result<AttachmentContent?> {
            contentIds += id
            return super.content(id)
        }
    }
}

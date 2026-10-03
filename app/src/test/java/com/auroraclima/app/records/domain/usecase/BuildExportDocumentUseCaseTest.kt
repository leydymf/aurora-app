package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.core.catalog.FakeCategoryCatalog
import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentContent
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.ExportPolicy
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
 * vivo del catálogo (P-68), relato, y los adjuntos de TODAS las familias solo
 * si se pidieron — leídos por `exportable` (bytes en memoria), nunca por
 * `content` (que crea una copia temporal para el visor).
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
        val document = useCase()(listOf(1, 2), includeAttachments = false, today = today).getOrThrow()

        assertEquals(listOf("Segunda", "Primera"), document.entries.map { it.kindLabel })
        assertEquals(today, document.createdOn)
    }

    @Test
    fun `un tema retirado cae a la instantanea y sin instantanea queda sin tipo`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t99", "Retirado"), entry(2, "", "")))

        val document = useCase()(listOf(1, 2), includeAttachments = false, today = today).getOrThrow()

        assertEquals("Retirado", document.entries[0].kindLabel)
        assertNull(document.entries[1].kindLabel)
    }

    @Test
    fun `sin catalogo el nombre sale de la instantanea`() = runTest {
        catalog.result = Result.failure(IllegalStateException("sin red"))
        repository.entriesResult = Result.success(listOf(entry(1, "t02", "Guardado")))

        val document = useCase()(listOf(1), includeAttachments = false, today = today).getOrThrow()

        assertEquals("Guardado", document.entries.single().kindLabel)
    }

    @Test
    fun `sin incluir adjuntos no se descifra nada y todos se citan por familia`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))
        attachments.attachmentsResult = Result.success(
            listOf(
                attachment(1, AttachmentKind.IMAGE),
                attachment(2, AttachmentKind.DOCUMENT),
                attachment(3, AttachmentKind.AUDIO)
            )
        )

        val document = useCase()(listOf(1), includeAttachments = false, today = today).getOrThrow()

        val entry = document.entries.single()
        assertTrue(entry.attachments.isEmpty())
        assertEquals(
            listOf(AttachmentKind.IMAGE, AttachmentKind.DOCUMENT, AttachmentKind.AUDIO),
            entry.omittedAttachments
        )
        assertTrue(attachments.exportableIds.isEmpty())
        assertEquals(0, attachments.contentCalls)
    }

    @Test
    fun `al incluir adjuntos se leen todas las familias con sus bytes y su MIME`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))
        attachments.attachmentsResult = Result.success(
            listOf(
                attachment(1, AttachmentKind.IMAGE),
                attachment(2, AttachmentKind.DOCUMENT),
                attachment(3, AttachmentKind.AUDIO)
            )
        )
        attachments.exportableById[1] = AttachmentBytes(AttachmentKind.IMAGE, "image/jpeg", byteArrayOf(1))
        attachments.exportableById[2] = AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", byteArrayOf(2))
        attachments.exportableById[3] = AttachmentBytes(AttachmentKind.AUDIO, "audio/wav", byteArrayOf(3))

        val document = useCase()(listOf(1), includeAttachments = true, today = today).getOrThrow()

        val entry = document.entries.single()
        assertEquals(
            listOf(AttachmentKind.IMAGE, AttachmentKind.DOCUMENT, AttachmentKind.AUDIO),
            entry.attachments.map { it.kind }
        )
        assertEquals(listOf("image/jpeg", "application/pdf", "audio/wav"), entry.attachments.map { it.mime })
        assertTrue(entry.omittedAttachments.isEmpty())
        // Nunca por `content`: eso dejaría una copia descifrada en caché.
        assertEquals(0, attachments.contentCalls)
        assertEquals(listOf(1L, 2L, 3L), attachments.exportableIds)
        assertTrue(document.includesAttachments)
    }

    @Test
    fun `un adjunto que desaparecio entre la lista y la lectura se omite sin fallar`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))
        attachments.attachmentsResult = Result.success(listOf(attachment(7, AttachmentKind.IMAGE)))

        val document = useCase()(listOf(1), includeAttachments = true, today = today).getOrThrow()

        assertTrue(document.entries.single().attachments.isEmpty())
    }

    @Test
    fun `por encima del tope de bytes no se descifra nada y la exportacion falla`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))
        attachments.attachmentsResult = Result.success(
            listOf(JournalAttachment(1, AttachmentKind.DOCUMENT, ExportPolicy.MAX_TOTAL_ATTACHMENT_BYTES + 1))
        )

        assertTrue(useCase()(listOf(1), includeAttachments = true, today = today).isFailure)
        assertTrue(attachments.exportableIds.isEmpty())
        // Sin incluir adjuntos el tope no aplica.
        assertTrue(useCase()(listOf(1), includeAttachments = false, today = today).isSuccess)
    }

    @Test
    fun `los ids inexistentes se omiten y sin ninguno valido falla`() = runTest {
        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))

        assertEquals(1, useCase()(listOf(1, 99), includeAttachments = false, today = today).getOrThrow().entries.size)
        assertTrue(useCase()(listOf(99), includeAttachments = false, today = today).isFailure)
    }

    @Test
    fun `un fallo al leer el historial o los adjuntos se propaga como fallo`() = runTest {
        repository.entriesResult = Result.failure(IllegalStateException("bd"))
        assertTrue(useCase()(listOf(1), includeAttachments = false, today = today).isFailure)

        repository.entriesResult = Result.success(listOf(entry(1, "t01", "x")))
        attachments.attachmentsResult = Result.failure(IllegalStateException("almacén"))
        assertTrue(useCase()(listOf(1), includeAttachments = false, today = today).isFailure)

        attachments.attachmentsResult = Result.success(listOf(attachment(1, AttachmentKind.AUDIO)))
        attachments.exportableResult = Result.failure(IllegalStateException("llave"))
        assertTrue(useCase()(listOf(1), includeAttachments = true, today = today).isFailure)
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
        var contentCalls = 0
            private set

        override suspend fun content(id: Long): Result<AttachmentContent?> {
            contentCalls++
            return super.content(id)
        }
    }
}

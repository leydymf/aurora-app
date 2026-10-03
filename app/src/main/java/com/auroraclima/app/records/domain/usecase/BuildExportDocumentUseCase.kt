package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.AttachmentContent
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.resolveKind
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Arma el contenido del PDF (RF-45 criterio 3) a partir de los ids elegidos:
 * combina historial, catálogo de tipos y adjuntos (§3: caso de uso al cruzar
 * más de un repositorio). El nombre del tipo es el VIVO del catálogo —
 * exactamente lo que muestra «Mi registro» (P-68) —, con la instantánea como
 * respaldo si el tema fue retirado y null si no hay nada que mostrar.
 *
 * Imágenes: se descifran SOLO si la usuaria pidió incluirlas y SOLO las de
 * familia IMAGE — pedir el contenido de un documento crearía una copia
 * descifrada temporal en caché (visor de PDF), que aquí no hace falta. Los
 * documentos y audios se citan por familia y número: el original nunca
 * conservó nombre (RNF-04), así que no hay nada más que decir de ellos.
 *
 * Los ids que ya no existen se omiten; sin ninguno válido, falla.
 */
class BuildExportDocumentUseCase @Inject constructor(
    private val repository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository,
    private val loadEntryKinds: LoadEntryKindsUseCase
) {
    suspend operator fun invoke(ids: List<Long>, includeImages: Boolean, today: LocalDate): Result<ExportDocument> {
        val entries = repository.entries().getOrElse { return Result.failure(it) }
        val wanted = ids.toSet()
        val chosen = entries.filter { it.id in wanted }
        if (chosen.isEmpty()) return Result.failure(IllegalArgumentException("nothing to export"))
        val kinds = loadEntryKinds().getOrElse { emptyList() }
        val exported = chosen.map { entry ->
            buildEntry(entry, kinds, includeImages).getOrElse { return Result.failure(it) }
        }
        return Result.success(ExportDocument(createdOn = today, entries = exported, includesImages = includeImages))
    }

    private suspend fun buildEntry(
        entry: JournalEntry,
        kinds: List<EntryKind>,
        includeImages: Boolean
    ): Result<ExportEntry> {
        val attachments = attachmentRepository.attachments(entry.id).getOrElse { return Result.failure(it) }
        val images = if (includeImages) loadImages(attachments).getOrElse { return Result.failure(it) } else emptyList()
        val omitted = attachments.map { it.kind }.filter { it != AttachmentKind.IMAGE || !includeImages }
        return Result.success(
            ExportEntry(
                occurredOn = entry.occurredOn,
                kindLabel = resolveKind(entry.kindId, entry.kindLabel, kinds).label,
                detail = entry.detail,
                images = images,
                omittedAttachments = omitted
            )
        )
    }

    private suspend fun loadImages(attachments: List<JournalAttachment>): Result<List<ByteArray>> {
        val images = mutableListOf<ByteArray>()
        for (attachment in attachments.filter { it.kind == AttachmentKind.IMAGE }) {
            val content = attachmentRepository.content(attachment.id).getOrElse { return Result.failure(it) }
            if (content is AttachmentContent.Image) images += content.bytes
        }
        return Result.success(images)
    }
}

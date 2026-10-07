package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.auroraclima.app.records.domain.model.ExportPolicy
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
 * Adjuntos: se descifran SOLO si la usuaria pidió incluirlos, y siempre por
 * [JournalAttachmentRepository.exportable] — bytes en memoria, sin la copia
 * temporal en caché que crea el visor de PDF. Con el interruptor apagado se
 * citan por familia y número: el original nunca conservó nombre (RNF-04).
 *
 * Los ids que ya no existen se omiten; sin ninguno válido, falla.
 */
class BuildExportDocumentUseCase @Inject constructor(
    private val repository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository,
    private val loadEntryKinds: LoadEntryKindsUseCase
) {
    suspend operator fun invoke(
        ids: List<Long>,
        includeAttachments: Boolean,
        today: LocalDate
    ): Result<ExportDocument> {
        val entries = repository.entries().getOrElse { return Result.failure(it) }
        val wanted = ids.toSet()
        val chosen = entries.filter { it.id in wanted }
        if (chosen.isEmpty()) return Result.failure(IllegalArgumentException("nothing to export"))
        if (includeAttachments) {
            val total = totalAttachmentBytes(chosen).getOrElse { return Result.failure(it) }
            if (!ExportPolicy.fitsInOneFile(total)) {
                return Result.failure(IllegalArgumentException("attachments too large"))
            }
        }
        val kinds = loadEntryKinds().getOrElse { emptyList() }
        val exported = chosen.map { entry ->
            buildEntry(entry, kinds, includeAttachments).getOrElse { return Result.failure(it) }
        }
        return Result.success(
            ExportDocument(createdOn = today, entries = exported, includesAttachments = includeAttachments)
        )
    }

    private suspend fun buildEntry(
        entry: JournalEntry,
        kinds: List<EntryKind>,
        includeAttachments: Boolean
    ): Result<ExportEntry> {
        val attachments = attachmentRepository.attachments(entry.id).getOrElse { return Result.failure(it) }
        val included = if (includeAttachments) {
            load(attachments).getOrElse { return Result.failure(it) }
        } else {
            emptyList()
        }
        val omitted = if (includeAttachments) emptyList() else attachments.map { it.kind }
        return Result.success(
            ExportEntry(
                occurredOn = entry.occurredOn,
                kindLabel = resolveKind(entry.kindId, entry.kindLabel, kinds).label,
                detail = entry.detail,
                attachments = included,
                omittedAttachments = omitted
            )
        )
    }

    /** Defensa del tope (A-1): la UI ya no ofrece el interruptor por encima, pero nunca se descifra de más. */
    private suspend fun totalAttachmentBytes(entries: List<JournalEntry>): Result<Long> {
        var total = 0L
        for (entry in entries) {
            val attachments = attachmentRepository.attachments(entry.id).getOrElse { return Result.failure(it) }
            total += attachments.sumOf { it.sizeBytes }
        }
        return Result.success(total)
    }

    private suspend fun load(attachments: List<JournalAttachment>): Result<List<AttachmentBytes>> {
        val loaded = mutableListOf<AttachmentBytes>()
        for (attachment in attachments) {
            val bytes = attachmentRepository.exportable(attachment.id).getOrElse { return Result.failure(it) }
            if (bytes != null) loaded += bytes
        }
        return Result.success(loaded)
    }
}

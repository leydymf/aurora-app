package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.ExportSummary
import com.auroraclima.app.records.domain.model.ExportSummaryRow
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import javax.inject.Inject

/**
 * Resumen de lo seleccionado antes de exportar (RF-45 criterio 2): filas
 * «fecha · tipo» en el orden del listado y cuántas imágenes podrían incluirse.
 * No descifra ningún adjunto: cuenta familias sobre la lista. El nombre del
 * tipo lo resuelve la UI con el catálogo que ya muestra «Mi registro».
 */
class LoadExportSummaryUseCase @Inject constructor(
    private val repository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository
) {
    suspend operator fun invoke(ids: List<Long>): Result<ExportSummary> {
        val entries = repository.entries().getOrElse { return Result.failure(it) }
        val wanted = ids.toSet()
        val chosen = entries.filter { it.id in wanted }
        if (chosen.isEmpty()) return Result.failure(IllegalArgumentException("nothing to export"))
        var imageCount = 0
        for (entry in chosen) {
            val attachments = attachmentRepository.attachments(entry.id).getOrElse { return Result.failure(it) }
            imageCount += attachments.count { it.kind == AttachmentKind.IMAGE }
        }
        val rows = chosen.map { ExportSummaryRow(it.id, it.occurredOn, it.kindId, it.kindLabel) }
        return Result.success(ExportSummary(rows = rows, imageCount = imageCount))
    }
}

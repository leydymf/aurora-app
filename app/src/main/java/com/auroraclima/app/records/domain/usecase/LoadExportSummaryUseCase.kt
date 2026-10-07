package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.ExportSummary
import com.auroraclima.app.records.domain.model.ExportSummaryRow
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import javax.inject.Inject

/**
 * Resumen de lo seleccionado antes de exportar (RF-45 criterio 2): filas
 * «fecha · tipo» en el orden del listado y cuántos adjuntos podrían incluirse.
 * No descifra ningún adjunto: cuenta sobre la lista. El nombre del
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
        var attachmentCount = 0
        var attachmentBytes = 0L
        for (entry in chosen) {
            val attachments = attachmentRepository.attachments(entry.id).getOrElse { return Result.failure(it) }
            attachmentCount += attachments.size
            attachmentBytes += attachments.sumOf { it.sizeBytes }
        }
        val rows = chosen.map { ExportSummaryRow(it.id, it.occurredOn, it.kindId, it.kindLabel) }
        return Result.success(ExportSummary(rows, attachmentCount, attachmentBytes))
    }
}

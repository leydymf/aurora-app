package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import javax.inject.Inject

/**
 * Edita un registro existente (RF-35) y re-sincroniza cifrado con la nube (RF-37).
 *
 * Mismo contrato que [AddJournalEntryUseCase] (§3): el guardado local es la
 * fuente de verdad y su [Result] es el único que ve la UI; la re-subida cifra
 * de nuevo el contenido y sobreescribe el MISMO documento (docId = id local,
 * idempotente por diseño del ADR RF-37) descartando su resultado — sin red, la
 * mutación queda en la cola durable de Firestore y sube sola al reconectar.
 */
class UpdateJournalEntryUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
    private val syncRepository: JournalSyncRepository
) {
    suspend operator fun invoke(entry: JournalEntry): Result<Unit> {
        journalRepository.update(entry).getOrElse { return Result.failure(it) }
        // Mejor esfuerzo: el cambio ya está a salvo localmente. Su fallo se
        // ignora — la edición dejó la fila pendiente y el reconciliador la
        // retoma en el siguiente login (RF-39).
        syncRepository.push(entry.id)
        return Result.success(Unit)
    }
}

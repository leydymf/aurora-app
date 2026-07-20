package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import javax.inject.Inject

/**
 * Elimina un registro del dispositivo y de la nube (RF-36), tras la doble
 * confirmación que exige la UI (criterio 1).
 *
 * El borrado local manda (§3): si falla, nada se toca y la UI ve el error
 * genérico. La propagación remota apunta al mismo docId determinista (ADR
 * RF-37) y es silenciosa: sin red, el `delete` queda en la cola durable de
 * Firestore y se aplica solo al reconectar — el criterio 2 se cumple sin
 * outbox propio ni error revelador.
 */
class DeleteJournalEntryUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
    private val syncRepository: JournalSyncRepository
) {
    suspend operator fun invoke(entryId: Long): Result<Unit> {
        journalRepository.delete(entryId).getOrElse { return Result.failure(it) }
        // Mejor esfuerzo: localmente ya no existe. El resultado remoto se ignora.
        syncRepository.remove(entryId)
        return Result.success(Unit)
    }
}

package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import javax.inject.Inject

/**
 * Elimina un registro del dispositivo y de la nube (RF-36), tras la doble
 * confirmación que exige la UI (criterio 1).
 *
 * El borrado local manda (§3): si falla, nada se toca y la UI ve el error
 * genérico. Con el registro borrado caen sus evidencias (criterio 2: fila y
 * archivo cifrado de cada adjunto); si ese paso fallara, quedan huérfanas —
 * ilegibles sin la BD que las referenciaba — y el barrido las limpia después.
 * La propagación remota apunta al mismo docId determinista (ADR RF-37) y es
 * silenciosa: sin red, el `delete` queda en la cola durable de Firestore y se
 * aplica solo al reconectar. RF-39 (cambio de política D0 §5.3): el borrado de
 * cada evidencia también propaga a la nube — lo hace el propio repositorio de
 * adjuntos al eliminar las filas, con el mismo mejor esfuerzo encolado.
 */
class DeleteJournalEntryUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository,
    private val syncRepository: JournalSyncRepository
) {
    suspend operator fun invoke(entryId: Long): Result<Unit> {
        journalRepository.delete(entryId).getOrElse { return Result.failure(it) }
        // Con el dueño borrado, las evidencias se van con él (RF-36 criterio 2).
        attachmentRepository.deleteForOwner(entryId)
        // Mejor esfuerzo: localmente ya no existe. El resultado remoto se ignora.
        syncRepository.remove(entryId)
        return Result.success(Unit)
    }
}

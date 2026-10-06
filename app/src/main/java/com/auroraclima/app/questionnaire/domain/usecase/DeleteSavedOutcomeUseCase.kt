package com.auroraclima.app.questionnaire.domain.usecase

import com.auroraclima.app.questionnaire.domain.repository.OutcomeSyncRepository
import com.auroraclima.app.questionnaire.domain.repository.SavedOutcomeRepository
import javax.inject.Inject

/**
 * Elimina un resultado guardado del dispositivo y de la nube (RF-31, patrón
 * RF-36), tras la doble confirmación que exige la UI. El borrado local manda
 * (§3): si falla, nada se toca y la UI ve el error genérico. La propagación
 * remota usa el nombre de documento que devuelve el borrado local y es
 * silenciosa: sin red, el `delete` queda en la cola durable de Firestore y se
 * aplica al reconectar — con la constancia local puesta para que mientras tanto
 * la reconciliación no lo vuelva a bajar (ADR 2026-10-06).
 */
class DeleteSavedOutcomeUseCase @Inject constructor(
    private val repository: SavedOutcomeRepository,
    private val syncRepository: OutcomeSyncRepository
) {
    suspend operator fun invoke(outcomeId: Long): Result<Unit> {
        val docId = repository.delete(outcomeId).getOrElse { return Result.failure(it) }
        // Mejor esfuerzo: localmente ya no existe. El resultado remoto se ignora.
        if (docId.isNotEmpty()) syncRepository.remove(docId)
        return Result.success(Unit)
    }
}

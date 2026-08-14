package com.auroraclima.app.questionnaire.domain.usecase

import com.auroraclima.app.questionnaire.domain.model.GuidanceResult
import com.auroraclima.app.questionnaire.domain.model.NewSavedOutcome
import com.auroraclima.app.questionnaire.domain.repository.OutcomeSyncRepository
import com.auroraclima.app.questionnaire.domain.repository.SavedOutcomeRepository
import javax.inject.Inject

/**
 * Guardado opcional del resultado (RF-31), SOLO tras la confirmación expresa en
 * la hoja segura (criterio 2) — ningún otro camino invoca esto: terminar,
 * abandonar y la salida rápida siguen descartando (RF-28, criterio 1 de RF-31).
 *
 * Del resultado se toma el MÍNIMO consultable (RNF-04): orden y título del
 * nivel y etiquetas de tipos reconocidos; las respuestas y el puntaje jamás se
 * persisten. El guardado local manda (§3); la subida cifrada es de mejor
 * esfuerzo y silenciosa — sin red queda pendiente y el reconciliador la retoma.
 */
class SaveGuidanceOutcomeUseCase @Inject constructor(
    private val repository: SavedOutcomeRepository,
    private val syncRepository: OutcomeSyncRepository
) {
    suspend operator fun invoke(result: GuidanceResult): Result<Long> {
        val id = repository.save(
            NewSavedOutcome(
                rank = result.level.order,
                label = result.level.title,
                tags = result.dimensions.map { it.label }
            )
        ).getOrElse { return Result.failure(it) }
        // Mejor esfuerzo: localmente ya quedó guardado. El resultado se ignora.
        syncRepository.push(id)
        return Result.success(id)
    }
}

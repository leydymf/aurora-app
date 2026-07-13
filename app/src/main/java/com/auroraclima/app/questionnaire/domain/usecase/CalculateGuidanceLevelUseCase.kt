package com.auroraclima.app.questionnaire.domain.usecase

import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.GuidanceResult
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.ScaleOption
import javax.inject.Inject

/**
 * Motor del nivel orientativo (RF-29): respuestas → nivel + rutas
 * recomendadas. Kotlin puro, sin Android (ARQUITECTURA §2).
 *
 * Las respuestas llegan como parámetro y viven SOLO en memoria: este caso de
 * uso no persiste nada ni existe API para hacerlo (RF-27 criterio 2; RF-31
 * fuera de alcance). Reglas, en el orden en que se aplican:
 *
 * 1. Puntaje: Σ (peso de la pregunta × valor de la opción elegida). Una
 *    pregunta sin responder —o con una opción desconocida— aporta 0.
 * 2. Nivel base: el de mayor `minScore` ≤ puntaje (umbral inclusivo). A igual
 *    `minScore`, rige el de mayor `order`: ante configuración ambigua se
 *    sobre-orienta, nunca al revés. Si ningún umbral alcanza (configuración
 *    sin nivel de arranque), cae al nivel de menor severidad.
 * 3. Piso de pregunta crítica: elegir la opción de mayor valor de la escala
 *    en una pregunta con `alertLevelId` garantiza al menos ese nivel. El
 *    piso eleva el nivel base; jamás lo baja.
 */
class CalculateGuidanceLevelUseCase @Inject constructor() {
    /** [answers]: id de pregunta → id de la opción de escala elegida. */
    operator fun invoke(questionnaire: Questionnaire, answers: Map<String, String>): GuidanceResult {
        val optionsById = questionnaire.scale.associateBy(ScaleOption::id)
        val maxValue = questionnaire.scale.maxOf(ScaleOption::value)
        val levelsById = questionnaire.levels.associateBy(GuidanceLevel::id)

        var score = 0
        var answeredCount = 0
        var floor: GuidanceLevel? = null
        for (question in questionnaire.questions) {
            val option = answers[question.id]?.let(optionsById::get) ?: continue
            answeredCount++
            score += question.weight * option.value
            if (option.value == maxValue) {
                val alert = question.alertLevelId?.let(levelsById::get) ?: continue
                floor = maxOfBySeverity(floor, alert)
            }
        }

        val bySeverity = questionnaire.levels.sortedWith(SEVERITY)
        val base = bySeverity.lastOrNull { it.minScore <= score } ?: bySeverity.first()
        val level = maxOfBySeverity(base, floor) ?: base
        return GuidanceResult(level, score, answeredCount, questionnaire.questions.size)
    }

    private fun maxOfBySeverity(a: GuidanceLevel?, b: GuidanceLevel?): GuidanceLevel? = when {
        a == null -> b
        b == null -> a
        b.order > a.order -> b
        else -> a
    }

    private companion object {
        /** Orden de severidad: umbral y, a igual umbral, el `order` del nivel. */
        val SEVERITY = compareBy(GuidanceLevel::minScore, GuidanceLevel::order)
    }
}

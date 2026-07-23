package com.auroraclima.app.questionnaire.domain.usecase

import com.auroraclima.app.questionnaire.domain.model.Dimension
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.GuidanceResult
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.Scale
import javax.inject.Inject

/**
 * Motor del cuestionario orientador (RF-29). Implementa la forma general del
 * pseudocódigo determinístico §5.4 del documento validado, en dos vías
 * independientes que se presentan juntas en el resultado. Kotlin puro, sin
 * Android (ARQUITECTURA §2).
 *
 * Las respuestas llegan como parámetro y viven SOLO en memoria: este caso de
 * uso no persiste nada ni existe API para hacerlo (RF-27 criterio 2; RF-31
 * fuera de alcance). Una pregunta sin responder —o con una opción desconocida—
 * aporta 0 y no cuenta.
 *
 * Track 1 — reconocimiento de tipos (§5.2): una dimensión se enciende si
 * cualquiera de sus preguntas alcanza su `minValue` (=1), con independencia del
 * puntaje.
 *
 * Track 2 — nivel de riesgo (§5.3): nivel base = el de mayor `minScore` ≤
 * puntaje (a igual `minScore` gana el de mayor `order`); las banderas críticas
 * (de umbral y de conteo) SOLO elevan el nivel. Nivel final = max(base,
 * banderas), según el orden de severidad.
 */
class CalculateGuidanceLevelUseCase @Inject constructor() {
    /** [answers]: id de pregunta → id de la opción de escala elegida. */
    operator fun invoke(questionnaire: Questionnaire, answers: Map<String, String>): GuidanceResult {
        val valueByQuestion = resolveValues(questionnaire, answers)

        val dimensions = recognizedTypes(questionnaire, valueByQuestion)
        val (score, answeredCount) = scoreAndAnswered(questionnaire, valueByQuestion)
        val questionCount = questionnaire.questions.count { !it.context }

        val levelsById = questionnaire.levels.associateBy(GuidanceLevel::id)
        val bySeverity = questionnaire.levels.sortedWith(SEVERITY)
        val base = bySeverity.lastOrNull { it.minScore <= score } ?: bySeverity.first()
        val flagged = flaggedLevel(questionnaire, valueByQuestion, levelsById)
        val level = maxOfBySeverity(base, flagged) ?: base

        return GuidanceResult(level, dimensions, score, answeredCount, questionCount)
    }

    /** Valor (0..N) de la opción elegida por pregunta, resolviendo cada una contra su escala. */
    private fun resolveValues(questionnaire: Questionnaire, answers: Map<String, String>): Map<String, Int> {
        val questionsById = questionnaire.questions.associateBy(Question::id)
        val scalesById = questionnaire.scales.associateBy(Scale::id)
        val values = HashMap<String, Int>(answers.size)
        for ((questionId, optionId) in answers) {
            val question = questionsById[questionId] ?: continue
            val scale = scalesById[question.scaleId] ?: continue
            val value = scale.options.firstOrNull { it.id == optionId }?.value ?: continue
            values[questionId] = value
        }
        return values
    }

    /** Track 1: dimensiones encendidas, ordenadas por severidad para la línea de reconocimiento. */
    private fun recognizedTypes(questionnaire: Questionnaire, values: Map<String, Int>): List<Dimension> =
        questionnaire.dimensions
            .sortedBy(Dimension::order)
            .filter { dimension ->
                dimension.questionIds.any { (values[it] ?: 0) >= dimension.minValue }
            }

    /** Puntaje total y número de preguntas puntuables respondidas (la de contexto no cuenta). */
    private fun scoreAndAnswered(questionnaire: Questionnaire, values: Map<String, Int>): Pair<Int, Int> {
        var score = 0
        var answered = 0
        for (question in questionnaire.questions) {
            if (question.context) continue
            val value = values[question.id] ?: continue
            answered++
            score += question.weight * value
        }
        return score to answered
    }

    /** Track 2, capa de banderas: el nivel mínimo que imponen las banderas activas (nunca baja). */
    private fun flaggedLevel(
        questionnaire: Questionnaire,
        values: Map<String, Int>,
        levelsById: Map<String, GuidanceLevel>
    ): GuidanceLevel? {
        var flagged: GuidanceLevel? = null
        for (flag in questionnaire.thresholdFlags) {
            if ((values[flag.questionId] ?: 0) >= flag.minValue) {
                flagged = maxOfBySeverity(flagged, levelsById[flag.levelId])
            }
        }
        for (flag in questionnaire.countFlags) {
            val active = flag.questionIds.count { (values[it] ?: 0) >= flag.eachMinValue }
            if (active >= flag.countAtLeast) {
                flagged = maxOfBySeverity(flagged, levelsById[flag.levelId])
            }
        }
        return flagged
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

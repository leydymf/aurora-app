package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.remote.FormDto
import com.auroraclima.app.questionnaire.data.remote.LevelDto
import com.auroraclima.app.questionnaire.data.remote.QuestionDto
import com.auroraclima.app.questionnaire.data.remote.ScaleOptionDto
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.ScaleOption

/** Versión de esquema más alta que esta app sabe interpretar (ARQUITECTURA §6). */
internal const val SUPPORTED_SCHEMA_VERSION = 1L

internal fun Long?.isSupportedSchema(): Boolean = this != null && this in 1..SUPPORTED_SCHEMA_VERSION

/**
 * Todo o nada: un documento con CUALQUIER pieza inválida (metadatos, escala,
 * pregunta o nivel) se descarta completo — espejo del criterio 2 de RF-43.
 * Omitir solo la pieza inválida, como hace content, sesgaría el puntaje:
 * los umbrales están calibrados para el conjunto exacto de preguntas.
 * Única tolerancia: un `alertLevelId` sin resolver se ignora (la pregunta
 * vale, el piso no), igual que las referencias huérfanas de RF-42.
 */
internal fun FormDto.toDomain(): Questionnaire? {
    val version = version?.toInt() ?: return null
    val title = title ?: return null
    val intro = intro ?: return null
    val notice = notice ?: return null
    if (scale.isEmpty() || questions.isEmpty() || levels.isEmpty()) return null
    val mappedScale = scale.map { it.toDomain() ?: return null }.sortedBy { it.order }
    val mappedLevels = levels.map { it.toDomain() ?: return null }.sortedBy { it.order }
    val levelIds = mappedLevels.map(GuidanceLevel::id).toSet()
    val mappedQuestions = questions.map { it.toDomain(levelIds) ?: return null }.sortedBy { it.order }
    return Questionnaire(id, version, title, intro, notice, mappedScale, mappedQuestions, mappedLevels)
}

private fun ScaleOptionDto.toDomain(): ScaleOption? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val label = label ?: return null
    val value = value?.toInt() ?: return null
    return ScaleOption(id, order, label, value)
}

private fun QuestionDto.toDomain(levelIds: Set<String>): Question? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val text = text ?: return null
    val weight = weight?.toInt() ?: return null
    return Question(id, order, text, weight, alertLevelId?.takeIf(levelIds::contains))
}

private fun LevelDto.toDomain(): GuidanceLevel? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val minScore = minScore?.toInt() ?: return null
    val title = title ?: return null
    val summary = summary ?: return null
    return GuidanceLevel(id, order, minScore, title, summary, advice, routeIds)
}

package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.remote.BlockDto
import com.auroraclima.app.questionnaire.data.remote.CountFlagDto
import com.auroraclima.app.questionnaire.data.remote.DimensionDto
import com.auroraclima.app.questionnaire.data.remote.FormDto
import com.auroraclima.app.questionnaire.data.remote.LevelDto
import com.auroraclima.app.questionnaire.data.remote.MunicipalityDto
import com.auroraclima.app.questionnaire.data.remote.QuestionDto
import com.auroraclima.app.questionnaire.data.remote.RecognitionDto
import com.auroraclima.app.questionnaire.data.remote.ScaleDto
import com.auroraclima.app.questionnaire.data.remote.ScaleOptionDto
import com.auroraclima.app.questionnaire.data.remote.ThresholdFlagDto
import com.auroraclima.app.questionnaire.domain.model.Block
import com.auroraclima.app.questionnaire.domain.model.CountFlag
import com.auroraclima.app.questionnaire.domain.model.Dimension
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.Municipality
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.Recognition
import com.auroraclima.app.questionnaire.domain.model.Scale
import com.auroraclima.app.questionnaire.domain.model.ScaleOption
import com.auroraclima.app.questionnaire.domain.model.ThresholdFlag

/** Versión de esquema más alta que esta app sabe interpretar (ARQUITECTURA §6). */
internal const val SUPPORTED_SCHEMA_VERSION = 2L

internal fun Long?.isSupportedSchema(): Boolean = this != null && this in 2..SUPPORTED_SCHEMA_VERSION

/**
 * Todo o nada: un documento con CUALQUIER pieza estructural inválida (metadatos,
 * escala, bloque, pregunta, dimensión, municipio o nivel) se descarta completo
 * — espejo del criterio 2 de RF-43. Omitir solo la pieza inválida, como hace
 * content, sesgaría el puntaje: los umbrales están calibrados para el conjunto
 * exacto de preguntas.
 *
 * Tolerancias (mismo criterio que las referencias huérfanas de RF-42): una
 * pregunta debe resolver su bloque y su escala (sin ellos no se puede pintar ni
 * puntuar), pero las referencias de dimensiones y banderas a preguntas o
 * niveles inexistentes NO tumban el documento — el motor las trata como valor 0
 * o nivel ausente. El panel es quien impide publicarlas (coherence.ts).
 */
internal fun FormDto.toDomain(): Questionnaire? {
    val version = version?.toInt() ?: return null
    val title = title ?: return null
    val intro = intro ?: return null
    val notice = notice ?: return null
    val recognition = recognition?.toDomain() ?: return null
    if (scales.isEmpty() || blocks.isEmpty() || questions.isEmpty() ||
        dimensions.isEmpty() || municipalities.isEmpty() || levels.isEmpty()
    ) {
        return null
    }
    val mappedScales = scales.map { it.toDomain() ?: return null }
    val scaleIds = mappedScales.map(Scale::id).toSet()
    val mappedBlocks = blocks.map { it.toDomain() ?: return null }.sortedBy { it.order }
    val blockIds = mappedBlocks.map(Block::id).toSet()
    val mappedLevels = levels.map { it.toDomain() ?: return null }.sortedBy { it.order }
    val mappedQuestions = questions.map { it.toDomain(blockIds, scaleIds) ?: return null }.sortedBy { it.order }
    val mappedDimensions = dimensions.map { it.toDomain() ?: return null }.sortedBy { it.order }
    val mappedMunicipalities = municipalities.map { it.toDomain() ?: return null }.sortedBy { it.order }
    val mappedThreshold = thresholdFlags.map { it.toDomain() ?: return null }
    val mappedCount = countFlags.map { it.toDomain() ?: return null }
    return Questionnaire(
        id = id,
        version = version,
        title = title,
        intro = intro,
        notice = notice,
        scales = mappedScales,
        blocks = mappedBlocks,
        questions = mappedQuestions,
        dimensions = mappedDimensions,
        recognition = recognition,
        thresholdFlags = mappedThreshold,
        countFlags = mappedCount,
        municipalities = mappedMunicipalities,
        levels = mappedLevels
    )
}

private fun RecognitionDto.toDomain(): Recognition? {
    val prefix = prefix ?: return null
    val empty = empty ?: return null
    return Recognition(prefix, empty)
}

private fun ScaleDto.toDomain(): Scale? {
    val id = id ?: return null
    if (options.isEmpty()) return null
    val mappedOptions = options.map { it.toDomain() ?: return null }.sortedBy { it.order }
    return Scale(id, mappedOptions)
}

private fun ScaleOptionDto.toDomain(): ScaleOption? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val label = label ?: return null
    val value = value?.toInt() ?: return null
    return ScaleOption(id, order, label, value)
}

private fun BlockDto.toDomain(): Block? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val title = title ?: return null
    return Block(id, order, title)
}

private fun QuestionDto.toDomain(blockIds: Set<String>, scaleIds: Set<String>): Question? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val blockId = blockId?.takeIf(blockIds::contains) ?: return null
    val scaleId = scaleId?.takeIf(scaleIds::contains) ?: return null
    val text = text ?: return null
    val weight = weight?.toInt() ?: return null
    return Question(id, order, blockId, scaleId, text, weight, context)
}

private fun DimensionDto.toDomain(): Dimension? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val label = label ?: return null
    val minValue = minValue?.toInt() ?: return null
    return Dimension(id, order, label, questionIds, minValue)
}

private fun ThresholdFlagDto.toDomain(): ThresholdFlag? {
    val questionId = questionId ?: return null
    val minValue = minValue?.toInt() ?: return null
    val levelId = levelId ?: return null
    return ThresholdFlag(questionId, minValue, levelId)
}

private fun CountFlagDto.toDomain(): CountFlag? {
    val eachMinValue = eachMinValue?.toInt() ?: return null
    val countAtLeast = countAtLeast?.toInt() ?: return null
    val levelId = levelId ?: return null
    return CountFlag(questionIds, eachMinValue, countAtLeast, levelId)
}

private fun MunicipalityDto.toDomain(): Municipality? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val label = label ?: return null
    return Municipality(id, order, label)
}

private fun LevelDto.toDomain(): GuidanceLevel? {
    val id = id ?: return null
    val order = order?.toInt() ?: return null
    val minScore = minScore?.toInt() ?: return null
    val title = title ?: return null
    val summary = summary ?: return null
    return GuidanceLevel(id, order, minScore, title, summary, advice, lineIds, agencyIds, municipalAgencyIds)
}

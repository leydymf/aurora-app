package com.auroraclima.app.questionnaire.data.remote

/**
 * Documento crudo de la fuente remota, tolerante a campos ausentes.
 * `schemaVersion` gobierna la compatibilidad (ARQUITECTURA §6). A diferencia
 * del contenido informativo, aquí el mapper descarta el documento COMPLETO si
 * cualquier pieza es inválida: el instrumento está calibrado en conjunto y
 * omitir una sola pregunta sesgaría el puntaje frente a los umbrales.
 *
 * Espejo del instrumento validado (documento §4/§5, esquema v2): varias
 * escalas, dos bloques, preguntas con bloque y escala, dimensiones (Track 1),
 * banderas críticas (§5.3) y accesos por municipio en cada nivel (§6).
 */
data class FormDto(
    val id: String,
    val schemaVersion: Long?,
    val version: Long?,
    val title: String?,
    val intro: String?,
    val notice: String?,
    val scales: List<ScaleDto>,
    val blocks: List<BlockDto>,
    val questions: List<QuestionDto>,
    val dimensions: List<DimensionDto>,
    val recognition: RecognitionDto?,
    val thresholdFlags: List<ThresholdFlagDto>,
    val countFlags: List<CountFlagDto>,
    val municipalities: List<MunicipalityDto>,
    val levels: List<LevelDto>
)

data class ScaleDto(
    val id: String?,
    val options: List<ScaleOptionDto>
)

data class ScaleOptionDto(
    val id: String?,
    val order: Long?,
    val label: String?,
    val value: Long?
)

data class BlockDto(
    val id: String?,
    val order: Long?,
    val title: String?
)

data class QuestionDto(
    val id: String?,
    val order: Long?,
    val blockId: String?,
    val scaleId: String?,
    val text: String?,
    val weight: Long?,
    val context: Boolean
)

data class DimensionDto(
    val id: String?,
    val order: Long?,
    val label: String?,
    val questionIds: List<String>,
    val minValue: Long?
)

data class RecognitionDto(
    val prefix: String?,
    val empty: String?
)

data class ThresholdFlagDto(
    val questionId: String?,
    val minValue: Long?,
    val levelId: String?
)

data class CountFlagDto(
    val questionIds: List<String>,
    val eachMinValue: Long?,
    val countAtLeast: Long?,
    val levelId: String?
)

data class MunicipalityDto(
    val id: String?,
    val order: Long?,
    val label: String?
)

data class LevelDto(
    val id: String?,
    val order: Long?,
    val minScore: Long?,
    val title: String?,
    val summary: String?,
    val advice: List<String>,
    val lineIds: List<String>,
    val agencyIds: List<String>,
    val municipalAgencyIds: Map<String, List<String>>
)

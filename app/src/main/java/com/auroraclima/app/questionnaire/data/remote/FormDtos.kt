package com.auroraclima.app.questionnaire.data.remote

/**
 * Documento crudo de la fuente remota, tolerante a campos ausentes.
 * `schemaVersion` gobierna la compatibilidad (ARQUITECTURA §6). A diferencia
 * del contenido informativo, aquí el mapper descarta el documento COMPLETO
 * si cualquier pieza es inválida: el instrumento está calibrado en conjunto
 * y omitir una sola pregunta sesgaría el puntaje frente a los umbrales.
 */
data class FormDto(
    val id: String,
    val schemaVersion: Long?,
    val version: Long?,
    val title: String?,
    val intro: String?,
    val notice: String?,
    val scale: List<ScaleOptionDto>,
    val questions: List<QuestionDto>,
    val levels: List<LevelDto>
)

data class ScaleOptionDto(
    val id: String?,
    val order: Long?,
    val label: String?,
    val value: Long?
)

data class QuestionDto(
    val id: String?,
    val order: Long?,
    val text: String?,
    val weight: Long?,
    val alertLevelId: String?
)

data class LevelDto(
    val id: String?,
    val order: Long?,
    val minScore: Long?,
    val title: String?,
    val summary: String?,
    val advice: List<String>,
    val routeIds: List<String>
)

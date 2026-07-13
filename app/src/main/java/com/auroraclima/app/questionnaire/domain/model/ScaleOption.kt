package com.auroraclima.app.questionnaire.domain.model

/**
 * Una opción de la escala de respuesta («Nunca / Alguna vez / Con frecuencia»).
 * [value] es el factor que multiplica el peso de la pregunta en el puntaje.
 */
data class ScaleOption(
    val id: String,
    val order: Int,
    val label: String,
    val value: Int
)

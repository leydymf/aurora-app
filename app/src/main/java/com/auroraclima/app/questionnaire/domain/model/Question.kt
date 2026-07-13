package com.auroraclima.app.questionnaire.domain.model

/**
 * Una pregunta del cuestionario. Su aporte al puntaje es
 * `weight × value` de la opción elegida.
 *
 * [alertLevelId] marca una pregunta crítica: si la usuaria elige la opción
 * de mayor valor de la escala, el resultado tiene al menos ese nivel sin
 * importar el puntaje total — así tratan los instrumentos de valoración
 * los ítems que elevan el riesgo por sí solos (amenazas, escalada).
 */
data class Question(
    val id: String,
    val order: Int,
    val text: String,
    val weight: Int,
    val alertLevelId: String? = null
)

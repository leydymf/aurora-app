package com.auroraclima.app.questionnaire.domain.model

/**
 * Una pregunta del cuestionario. Pertenece a un bloque ([blockId]) y responde
 * con las opciones de su escala ([scaleId]) — el instrumento validado combina
 * la escala de frecuencia con la de percepción de riesgo y la de vínculo.
 *
 * Su aporte al puntaje es `weight × value` de la opción elegida. La pregunta
 * de contexto P0 ([context] = true) tiene [weight] 0 y NO participa del puntaje
 * ni del reconocimiento de tipos ni de las banderas: solo orienta (documento
 * validado §4.1). El reconocimiento (dimensiones) y las banderas críticas se
 * definen por separado ([Dimension], [ThresholdFlag], [CountFlag]).
 */
data class Question(
    val id: String,
    val order: Int,
    val blockId: String,
    val scaleId: String,
    val text: String,
    val weight: Int,
    val context: Boolean = false
)

package com.auroraclima.app.questionnaire.domain.model

/**
 * Banderas críticas del Track 2 (documento validado §5.3): conductas de tan
 * alto poder predictivo que, por sí solas, elevan el nivel con independencia
 * del puntaje. Las banderas SOLO elevan el nivel; nunca lo bajan. El motor
 * implementa la forma general del pseudocódigo §5.4 y estas reglas viajan en
 * el documento administrable (RF-43): el panel las valida y la usuaria del
 * panel puede ajustarlas sin recompilar.
 */

/**
 * Si la respuesta a [questionId] alcanza [minValue], el nivel es al menos
 * [levelId]. El instrumento usa dos umbrales por conducta (p. ej.
 * estrangulamiento ≥1 → Grave; ≥2 → Extremo) con dos banderas sobre la misma
 * pregunta.
 */
data class ThresholdFlag(
    val questionId: String,
    val minValue: Int,
    val levelId: String
)

/**
 * Si al menos [countAtLeast] de las preguntas de [questionIds] alcanzan cada
 * una [eachMinValue], el nivel es al menos [levelId]. Codifica la regla «dos o
 * más banderas críticas activas → Extremo» (§5.3).
 */
data class CountFlag(
    val questionIds: List<String>,
    val eachMinValue: Int,
    val countAtLeast: Int,
    val levelId: String
)

package com.auroraclima.app.questionnaire.domain.model

/**
 * Un conjunto de opciones de respuesta con nombre. El instrumento validado usa
 * varias escalas a la vez: la de frecuencia 0–3 (P1–P10), la de percepción de
 * riesgo de P11 (mismos valores 0–3, etiquetas distintas) y la de vínculo de la
 * pregunta de contexto P0 (categórica, sin puntaje). Cada pregunta referencia
 * la suya por [id] (documento validado, secciones 3 y 4).
 */
data class Scale(
    val id: String,
    val options: List<ScaleOption>
) {
    init {
        require(options.isNotEmpty()) { "Una escala no puede quedarse sin opciones" }
    }
}

package com.auroraclima.app.questionnaire.domain.model

/**
 * Una dimensión del Track 1 — reconocimiento de tipos (documento validado
 * §5.2). Se «enciende» si cualquiera de sus preguntas alcanza [minValue] (=1),
 * con independencia del puntaje: así una usuaria con un patrón económico o
 * psicológico intenso siempre verá nombrado ese tipo en su resultado, aunque su
 * nivel de riesgo sea bajo.
 *
 * [label] es el fragmento que arma la línea de reconocimiento del resultado; su
 * texto literal llega del documento administrable, nunca del APK (RNF-05).
 */
data class Dimension(
    val id: String,
    val order: Int,
    val label: String,
    val questionIds: List<String>,
    val minValue: Int
)

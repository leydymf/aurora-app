package com.auroraclima.app.questionnaire.domain.model

/**
 * Textos literales de la línea de reconocimiento de tipos (documento validado
 * §6.1), que precede al nivel en el resultado. [prefix] encabeza la lista de
 * dimensiones encendidas («Por tus respuestas, aparecen señales de …») y
 * [empty] es el texto cuando no se enciende ninguna. Viven en el documento
 * administrable (RF-43) y nunca en el APK (RNF-05).
 */
data class Recognition(
    val prefix: String,
    val empty: String
)

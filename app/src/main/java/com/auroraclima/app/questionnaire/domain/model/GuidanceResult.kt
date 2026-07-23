package com.auroraclima.app.questionnaire.domain.model

/**
 * Resultado del cálculo (RF-29), en las dos vías del instrumento validado
 * (§5): [dimensions] son los tipos reconocidos (Track 1) y
 * [level] el nivel orientativo de riesgo (Track 2). Vive SOLO en memoria: hoy
 * no existe API alguna para persistirlo (el guardado opcional de RF-31 está
 * fuera de alcance).
 *
 * [dimensions] llega ordenada por severidad de la dimensión para armar la
 * línea de reconocimiento; vacía si no se encendió ninguna.
 *
 * [answeredCount] permite a la UI matizar el resultado cuando la usuaria
 * respondió pocas preguntas: el nivel bajo por falta de respuestas no debe
 * leerse como ausencia de riesgo. Cuenta solo las preguntas puntuables
 * ([questionCount] excluye la de contexto P0).
 */
data class GuidanceResult(
    val level: GuidanceLevel,
    val dimensions: List<Dimension>,
    val score: Int,
    val answeredCount: Int,
    val questionCount: Int
)

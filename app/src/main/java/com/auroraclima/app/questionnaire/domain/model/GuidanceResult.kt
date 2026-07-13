package com.auroraclima.app.questionnaire.domain.model

/**
 * Resultado del cálculo del nivel orientativo (RF-29). Vive SOLO en memoria:
 * hoy no existe API alguna para persistirlo (el guardado opcional de RF-31
 * está fuera de alcance). Las rutas recomendadas vienen en [level].routeIds.
 *
 * [answeredCount] permite a la UI matizar el resultado cuando la usuaria
 * respondió pocas preguntas (o ninguna): el nivel bajo por falta de
 * respuestas no debe leerse como ausencia de riesgo.
 */
data class GuidanceResult(
    val level: GuidanceLevel,
    val score: Int,
    val answeredCount: Int,
    val questionCount: Int
)

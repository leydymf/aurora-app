package com.auroraclima.app.questionnaire.domain.model

/**
 * Un nivel orientativo (RF-29; niveles de referencia INMLCF). El resultado
 * es el nivel de mayor [minScore] menor o igual al puntaje total; a igual
 * [minScore] rige el de mayor [order] — ante configuración ambigua se
 * sobre-orienta, nunca al revés.
 *
 * [routeIds] apunta a las rutas de atención pertinentes (feature content,
 * RF-29 criterio 2); la referencia se resuelve por navegación (§2).
 */
data class GuidanceLevel(
    val id: String,
    val order: Int,
    val minScore: Int,
    val title: String,
    val summary: String,
    val advice: List<String>,
    val routeIds: List<String>
)

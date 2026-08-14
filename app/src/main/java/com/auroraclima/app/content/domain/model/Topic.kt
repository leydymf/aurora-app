package com.auroraclima.app.content.domain.model

/**
 * Tema del módulo informativo (RF-21): descripción, señales y ejemplos.
 * [icon] y [accent] (esquema v2) seleccionan dentro de la biblioteca cerrada
 * de la capa de presentación; nulos o desconocidos degradan al mapeo fijo por id.
 */
data class Topic(
    val id: String,
    val order: Int,
    val title: String,
    val summary: String,
    val signals: List<String>,
    val examples: List<String>,
    val icon: String? = null,
    val accent: String? = null
)

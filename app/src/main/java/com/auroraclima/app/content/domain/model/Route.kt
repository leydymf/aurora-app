package com.auroraclima.app.content.domain.model

/**
 * Sector de la ruta de atención (RF-22) con sus entidades ya resueltas.
 * [icon] y [accent] (esquema v2) seleccionan dentro de la biblioteca cerrada
 * de la capa de presentación; nulos o desconocidos degradan al mapeo fijo por id.
 */
data class Route(
    val id: String,
    val order: Int,
    val title: String,
    val summary: String,
    val agencies: List<Agency>,
    val icon: String? = null,
    val accent: String? = null
)

package com.auroraclima.app.content.domain.model

/** Sector de la ruta de atención (RF-22) con sus entidades ya resueltas. */
data class Route(
    val id: String,
    val order: Int,
    val title: String,
    val summary: String,
    val agencies: List<Agency>
)

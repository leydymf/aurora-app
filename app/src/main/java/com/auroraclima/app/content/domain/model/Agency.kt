package com.auroraclima.app.content.domain.model

/** Entidad de una ruta de atención (RF-22): qué hace, cuándo acudir y cómo contactarla. */
data class Agency(
    val id: String,
    val name: String,
    val role: String,
    val whenToUse: String,
    val phones: List<String>,
    val address: String?,
    val hours: String?
)

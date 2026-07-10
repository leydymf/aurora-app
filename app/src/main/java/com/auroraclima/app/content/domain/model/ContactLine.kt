package com.auroraclima.app.content.domain.model

/** Línea de atención telefónica (RF-23): el número se abre en el marcador del teléfono. */
data class ContactLine(
    val id: String,
    val order: Int,
    val name: String,
    val number: String,
    val description: String,
    val hours: String?
)

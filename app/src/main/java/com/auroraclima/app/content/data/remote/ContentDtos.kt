package com.auroraclima.app.content.data.remote

/**
 * Documentos crudos de la fuente remota, tolerantes a campos ausentes.
 * `schemaVersion` gobierna la compatibilidad con versiones futuras del
 * contenido administrable (ARQUITECTURA §6): la app ignora documentos
 * con una versión que no sabe interpretar.
 */
data class TopicDto(
    val id: String,
    val schemaVersion: Long?,
    val order: Long?,
    val title: String?,
    val summary: String?,
    val signals: List<String>,
    val examples: List<String>
)

data class RouteDto(
    val id: String,
    val schemaVersion: Long?,
    val order: Long?,
    val title: String?,
    val summary: String?,
    val agencyIds: List<String>
)

data class AgencyDto(
    val id: String,
    val schemaVersion: Long?,
    val name: String?,
    val role: String?,
    val whenToUse: String?,
    val phones: List<String>,
    val address: String?,
    val hours: String?
)

data class LineDto(
    val id: String,
    val schemaVersion: Long?,
    val order: Long?,
    val name: String?,
    val number: String?,
    val description: String?,
    val hours: String?
)

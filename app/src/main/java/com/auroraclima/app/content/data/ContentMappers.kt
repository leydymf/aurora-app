package com.auroraclima.app.content.data

import com.auroraclima.app.content.data.remote.AgencyDto
import com.auroraclima.app.content.data.remote.LineDto
import com.auroraclima.app.content.data.remote.RouteDto
import com.auroraclima.app.content.data.remote.TopicDto
import com.auroraclima.app.content.domain.model.Agency
import com.auroraclima.app.content.domain.model.ContactLine
import com.auroraclima.app.content.domain.model.Route
import com.auroraclima.app.content.domain.model.Topic

/**
 * Versión de esquema más alta que esta app sabe interpretar (ARQUITECTURA §6).
 * v2 (ADR 2026-08-14): topics y routes ganan `icon`/`accent` opcionales que
 * seleccionan dentro de la biblioteca cerrada de presentación.
 */
internal const val SUPPORTED_SCHEMA_VERSION = 2L

internal fun Long?.isSupportedSchema(): Boolean = this != null && this in 1..SUPPORTED_SCHEMA_VERSION

// Los documentos sin sus campos obligatorios se omiten: un dato incompleto
// publicado por error no debe tumbar el módulo completo (base de RF-25).

internal fun TopicDto.toDomain(): Topic? {
    val order = order?.toInt() ?: return null
    val title = title ?: return null
    val summary = summary ?: return null
    return Topic(id, order, title, summary, signals, examples, icon, accent)
}

internal fun RouteDto.toDomain(agenciesById: Map<String, Agency>): Route? {
    val order = order?.toInt() ?: return null
    val title = title ?: return null
    val summary = summary ?: return null
    // Las referencias sin resolver se omiten: quitar una entidad desde el
    // futuro panel no rompe las rutas que la mencionaban (RF-42).
    val agencies = agencyIds.mapNotNull(agenciesById::get)
    return Route(id, order, title, summary, agencies, icon, accent)
}

internal fun AgencyDto.toDomain(): Agency? {
    val name = name ?: return null
    val role = role ?: return null
    val whenToUse = whenToUse ?: return null
    return Agency(id, name, role, whenToUse, phones, address, hours)
}

internal fun LineDto.toDomain(): ContactLine? {
    val order = order?.toInt() ?: return null
    val name = name ?: return null
    val number = number ?: return null
    val description = description ?: return null
    return ContactLine(id, order, name, number, description, hours)
}

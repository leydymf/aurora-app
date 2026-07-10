package com.auroraclima.app.content.domain.repository

import com.auroraclima.app.content.domain.model.ContactLine
import com.auroraclima.app.content.domain.model.Route
import com.auroraclima.app.content.domain.model.Topic

/**
 * Acceso al contenido informativo administrable (RF-21, RF-22, RF-23).
 * Las operaciones devuelven [Result]; las excepciones no cruzan a la UI
 * (ARQUITECTURA §3). La disponibilidad sin conexión (RF-25, RF-26) la
 * aporta la caché persistente de la fuente de datos.
 */
interface ContentRepository {
    /** Temas informativos ordenados (RF-21). */
    suspend fun getTopics(): Result<List<Topic>>

    /** Sectores de la ruta de atención con sus entidades resueltas (RF-22). */
    suspend fun getRoutes(): Result<List<Route>>

    /** Líneas de atención vigentes (RF-23). */
    suspend fun getContactLines(): Result<List<ContactLine>>
}

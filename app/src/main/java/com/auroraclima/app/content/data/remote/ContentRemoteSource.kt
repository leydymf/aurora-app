package com.auroraclima.app.content.data.remote

/**
 * Fuente remota del contenido informativo (mismo rol que `AuthService` en
 * accounts): el repositorio se prueba con un fake sin conocer el SDK.
 * Cada operación lanza en fallo; el repositorio traduce a [Result].
 */
interface ContentRemoteSource {
    suspend fun fetchTopics(): List<TopicDto>

    suspend fun fetchRoutes(): List<RouteDto>

    suspend fun fetchAgencies(): List<AgencyDto>

    suspend fun fetchLines(): List<LineDto>
}

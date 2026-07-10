package com.auroraclima.app.content.data

import com.auroraclima.app.content.data.remote.ContentRemoteSource
import com.auroraclima.app.content.domain.model.ContactLine
import com.auroraclima.app.content.domain.model.Route
import com.auroraclima.app.content.domain.model.Topic
import com.auroraclima.app.content.domain.repository.ContentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repositorio de contenido sobre la fuente remota (RF-21, RF-22, RF-23).
 * Filtra esquemas no soportados (ARQUITECTURA §6), resuelve las referencias
 * de las rutas y traduce todo fallo a [Result]: ninguna excepción cruza a la
 * UI (§3) y nada se registra en logs (regla 4 de CLAUDE.md).
 */
@Singleton
class FirestoreContentRepository @Inject constructor(
    private val remote: ContentRemoteSource
) : ContentRepository {
    override suspend fun getTopics(): Result<List<Topic>> = fetchSafely {
        remote.fetchTopics()
            .filter { it.schemaVersion.isSupportedSchema() }
            .mapNotNull { it.toDomain() }
            .sortedBy { it.order }
    }

    override suspend fun getRoutes(): Result<List<Route>> = fetchSafely {
        val agenciesById = remote.fetchAgencies()
            .filter { it.schemaVersion.isSupportedSchema() }
            .mapNotNull { it.toDomain() }
            .associateBy { it.id }
        remote.fetchRoutes()
            .filter { it.schemaVersion.isSupportedSchema() }
            .mapNotNull { it.toDomain(agenciesById) }
            .sortedBy { it.order }
    }

    override suspend fun getContactLines(): Result<List<ContactLine>> = fetchSafely {
        remote.fetchLines()
            .filter { it.schemaVersion.isSupportedSchema() }
            .mapNotNull { it.toDomain() }
            .sortedBy { it.order }
    }

    private suspend fun <T> fetchSafely(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

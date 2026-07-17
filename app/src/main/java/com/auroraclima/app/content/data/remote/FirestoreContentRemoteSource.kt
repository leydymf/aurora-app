package com.auroraclima.app.content.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Envoltorio fino sobre Firestore: único lugar del feature que conoce las
 * clases del SDK. Sin conexión, `get()` cae solo a la caché persistente
 * (RF-25, RF-26). El puente Task→coroutine se hace a mano para no sumar
 * kotlinx-coroutines-play-services (criterios A2-1).
 */
@Singleton
class FirestoreContentRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore
) : ContentRemoteSource {
    override suspend fun fetchTopics(): List<TopicDto> =
        awaitCollection(TOPICS).documents.map { it.data.orEmpty().toTopicDto(it.id) }

    override suspend fun fetchRoutes(): List<RouteDto> =
        awaitCollection(ROUTES).documents.map { it.data.orEmpty().toRouteDto(it.id) }

    override suspend fun fetchAgencies(): List<AgencyDto> =
        awaitCollection(AGENCIES).documents.map { it.data.orEmpty().toAgencyDto(it.id) }

    override suspend fun fetchLines(): List<LineDto> =
        awaitCollection(LINES).documents.map { it.data.orEmpty().toLineDto(it.id) }

    private suspend fun awaitCollection(name: String): QuerySnapshot = suspendCancellableCoroutine { continuation ->
        firestore.collection(name).get()
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }

    private companion object {
        // Nombres neutros: viajan en el tráfico y quedan en la caché local (RNF-05).
        const val TOPICS = "topics"
        const val ROUTES = "routes"
        const val AGENCIES = "agencies"
        const val LINES = "lines"
    }
}

// Parsing con casts seguros: un campo con tipo inesperado queda nulo, igual
// que un campo ausente, y el mapper omite solo ese documento (RF-25) — los
// getters tipados del SDK lanzarían y tumbarían el módulo completo.

internal fun Map<String, Any?>.toTopicDto(id: String) = TopicDto(
    id = id,
    schemaVersion = num(FIELD_SCHEMA_VERSION),
    order = num(FIELD_ORDER),
    title = str(FIELD_TITLE),
    summary = str(FIELD_SUMMARY),
    signals = strList("signals"),
    examples = strList("examples")
)

internal fun Map<String, Any?>.toRouteDto(id: String) = RouteDto(
    id = id,
    schemaVersion = num(FIELD_SCHEMA_VERSION),
    order = num(FIELD_ORDER),
    title = str(FIELD_TITLE),
    summary = str(FIELD_SUMMARY),
    agencyIds = strList("agencyIds")
)

internal fun Map<String, Any?>.toAgencyDto(id: String) = AgencyDto(
    id = id,
    schemaVersion = num(FIELD_SCHEMA_VERSION),
    name = str(FIELD_NAME),
    role = str("role"),
    whenToUse = str("whenToUse"),
    phones = strList("phones"),
    address = str("address"),
    hours = str(FIELD_HOURS)
)

internal fun Map<String, Any?>.toLineDto(id: String) = LineDto(
    id = id,
    schemaVersion = num(FIELD_SCHEMA_VERSION),
    order = num(FIELD_ORDER),
    name = str(FIELD_NAME),
    number = str("number"),
    description = str("description"),
    hours = str(FIELD_HOURS)
)

private fun Map<String, Any?>.str(field: String): String? = this[field] as? String

// Solo valores enteros: truncar un decimal alteraría órdenes o versiones
// sin descartar el documento — un decimal se trata como campo corrupto.
private fun Map<String, Any?>.num(field: String): Long? =
    (this[field] as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toLong()

private fun Map<String, Any?>.strList(field: String): List<String> =
    (this[field] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

private const val FIELD_SCHEMA_VERSION = "schemaVersion"
private const val FIELD_ORDER = "order"
private const val FIELD_TITLE = "title"
private const val FIELD_SUMMARY = "summary"
private const val FIELD_NAME = "name"
private const val FIELD_HOURS = "hours"

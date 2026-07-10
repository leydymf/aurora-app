package com.auroraclima.app.content.data.remote

import com.google.firebase.firestore.DocumentSnapshot
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
    override suspend fun fetchTopics(): List<TopicDto> = awaitCollection(TOPICS).documents.map { it.toTopicDto() }

    override suspend fun fetchRoutes(): List<RouteDto> = awaitCollection(ROUTES).documents.map { it.toRouteDto() }

    override suspend fun fetchAgencies(): List<AgencyDto> = awaitCollection(AGENCIES).documents.map { it.toAgencyDto() }

    override suspend fun fetchLines(): List<LineDto> = awaitCollection(LINES).documents.map { it.toLineDto() }

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

private fun DocumentSnapshot.toTopicDto() = TopicDto(
    id = id,
    schemaVersion = getLong(FIELD_SCHEMA_VERSION),
    order = getLong(FIELD_ORDER),
    title = getString(FIELD_TITLE),
    summary = getString(FIELD_SUMMARY),
    signals = stringList("signals"),
    examples = stringList("examples")
)

private fun DocumentSnapshot.toRouteDto() = RouteDto(
    id = id,
    schemaVersion = getLong(FIELD_SCHEMA_VERSION),
    order = getLong(FIELD_ORDER),
    title = getString(FIELD_TITLE),
    summary = getString(FIELD_SUMMARY),
    agencyIds = stringList("agencyIds")
)

private fun DocumentSnapshot.toAgencyDto() = AgencyDto(
    id = id,
    schemaVersion = getLong(FIELD_SCHEMA_VERSION),
    name = getString(FIELD_NAME),
    role = getString("role"),
    whenToUse = getString("whenToUse"),
    phones = stringList("phones"),
    address = getString("address"),
    hours = getString(FIELD_HOURS)
)

private fun DocumentSnapshot.toLineDto() = LineDto(
    id = id,
    schemaVersion = getLong(FIELD_SCHEMA_VERSION),
    order = getLong(FIELD_ORDER),
    name = getString(FIELD_NAME),
    number = getString("number"),
    description = getString("description"),
    hours = getString(FIELD_HOURS)
)

private fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>)?.filterIsInstance<String>() ?: emptyList()

private const val FIELD_SCHEMA_VERSION = "schemaVersion"
private const val FIELD_ORDER = "order"
private const val FIELD_TITLE = "title"
private const val FIELD_SUMMARY = "summary"
private const val FIELD_NAME = "name"
private const val FIELD_HOURS = "hours"

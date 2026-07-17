package com.auroraclima.app.questionnaire.data.remote

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
 * (mismo contrato RF-26 del módulo de información). El puente Task→coroutine
 * se hace a mano para no sumar kotlinx-coroutines-play-services (A2-1).
 */
@Singleton
class FirestoreQuestionnaireRemoteSource @Inject constructor(
    private val firestore: FirebaseFirestore
) : QuestionnaireRemoteSource {
    override suspend fun fetchForms(): List<FormDto> =
        awaitCollection(FORMS).documents.map { it.data.orEmpty().toFormDto(it.id) }

    private suspend fun awaitCollection(name: String): QuerySnapshot = suspendCancellableCoroutine { continuation ->
        firestore.collection(name).get()
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }

    private companion object {
        // Nombre neutro: viaja en el tráfico y queda en la caché local (RNF-05).
        const val FORMS = "forms"
    }
}

// Parsing con casts seguros: un campo con tipo inesperado queda nulo, igual
// que un campo ausente, y el mapper todo-o-nada descarta solo ese documento —
// entre los restantes rige el válido de mayor versión (RF-25). Los getters
// tipados del SDK lanzarían y tumbarían el módulo completo.
internal fun Map<String, Any?>.toFormDto(id: String) = FormDto(
    id = id,
    schemaVersion = num("schemaVersion"),
    version = num("version"),
    title = str("title"),
    intro = str("intro"),
    notice = str("notice"),
    scale = entryList("scale").map { it.toScaleOptionDto() },
    questions = entryList("questions").map { it.toQuestionDto() },
    levels = entryList("levels").map { it.toLevelDto() }
)

// Una entrada que no sea mapa se convierte en DTO vacío: el mapper la
// rechazará y descartará el documento entero (estrictez deliberada).
private fun Map<String, Any?>.entryList(field: String): List<Map<*, *>> =
    (this[field] as? List<*>)?.map { it as? Map<*, *> ?: emptyMap<Any, Any>() } ?: emptyList()

private fun Map<*, *>.toScaleOptionDto() = ScaleOptionDto(
    id = str("id"),
    order = num("order"),
    label = str("label"),
    value = num("value")
)

private fun Map<*, *>.toQuestionDto() = QuestionDto(
    id = str("id"),
    order = num("order"),
    text = str("text"),
    weight = num("weight"),
    alertLevelId = str("alertLevelId")
)

private fun Map<*, *>.toLevelDto() = LevelDto(
    id = str("id"),
    order = num("order"),
    minScore = num("minScore"),
    title = str("title"),
    summary = str("summary"),
    advice = strList("advice"),
    routeIds = strList("routeIds")
)

private fun Map<*, *>.str(key: String): String? = this[key] as? String

// Solo valores enteros: truncar un decimal alteraría pesos o umbrales
// calibrados sin descartar el documento — un decimal se trata como corrupto.
private fun Map<*, *>.num(key: String): Long? =
    (this[key] as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toLong()

private fun Map<*, *>.strList(key: String): List<String> =
    (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

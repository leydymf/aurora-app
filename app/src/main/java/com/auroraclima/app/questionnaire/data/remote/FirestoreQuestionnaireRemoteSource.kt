package com.auroraclima.app.questionnaire.data.remote

import com.auroraclima.app.core.network.num
import com.auroraclima.app.core.network.str
import com.auroraclima.app.core.network.strList
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
    scales = entryList("scales").map { it.toScaleDto() },
    blocks = entryList("blocks").map { it.toBlockDto() },
    questions = entryList("questions").map { it.toQuestionDto() },
    dimensions = entryList("dimensions").map { it.toDimensionDto() },
    recognition = (this["recognition"] as? Map<*, *>)?.toRecognitionDto(),
    thresholdFlags = entryList("thresholdFlags").map { it.toThresholdFlagDto() },
    countFlags = entryList("countFlags").map { it.toCountFlagDto() },
    municipalities = entryList("municipalities").map { it.toMunicipalityDto() },
    levels = entryList("levels").map { it.toLevelDto() }
)

// Una entrada que no sea mapa se convierte en DTO vacío: el mapper la
// rechazará y descartará el documento entero (estrictez deliberada).
private fun Map<*, *>.entryList(field: String): List<Map<*, *>> =
    (this[field] as? List<*>)?.map { it as? Map<*, *> ?: emptyMap<Any, Any>() } ?: emptyList()

private fun Map<*, *>.toScaleDto() = ScaleDto(
    id = str("id"),
    options = entryList("options").map { it.toScaleOptionDto() }
)

private fun Map<*, *>.toScaleOptionDto() = ScaleOptionDto(
    id = str("id"),
    order = num("order"),
    label = str("label"),
    value = num("value")
)

private fun Map<*, *>.toBlockDto() = BlockDto(
    id = str("id"),
    order = num("order"),
    title = str("title")
)

private fun Map<*, *>.toQuestionDto() = QuestionDto(
    id = str("id"),
    order = num("order"),
    blockId = str("blockId"),
    scaleId = str("scaleId"),
    text = str("text"),
    weight = num("weight"),
    context = bool("context")
)

private fun Map<*, *>.toDimensionDto() = DimensionDto(
    id = str("id"),
    order = num("order"),
    label = str("label"),
    questionIds = strList("questionIds"),
    minValue = num("minValue")
)

private fun Map<*, *>.toRecognitionDto() = RecognitionDto(
    prefix = str("prefix"),
    empty = str("empty")
)

private fun Map<*, *>.toThresholdFlagDto() = ThresholdFlagDto(
    questionId = str("questionId"),
    minValue = num("minValue"),
    levelId = str("levelId")
)

private fun Map<*, *>.toCountFlagDto() = CountFlagDto(
    questionIds = strList("questionIds"),
    eachMinValue = num("eachMinValue"),
    countAtLeast = num("countAtLeast"),
    levelId = str("levelId")
)

private fun Map<*, *>.toMunicipalityDto() = MunicipalityDto(
    id = str("id"),
    order = num("order"),
    label = str("label")
)

private fun Map<*, *>.toLevelDto() = LevelDto(
    id = str("id"),
    order = num("order"),
    minScore = num("minScore"),
    title = str("title"),
    summary = str("summary"),
    advice = strList("advice"),
    lineIds = strList("lineIds"),
    agencyIds = strList("agencyIds"),
    municipalAgencyIds = strListMap("municipalAgencyIds")
)

private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: false

// Mapa municipio → ids de entidades (accesos por municipio, §6). Una clave sin
// lista válida queda con lista vacía; el mapper y el resolvedor de accesos
// toleran ids sin resolver (mismo criterio que las referencias huérfanas RF-42).
private fun Map<*, *>.strListMap(key: String): Map<String, List<String>> {
    val raw = this[key] as? Map<*, *> ?: return emptyMap()
    val result = LinkedHashMap<String, List<String>>()
    for ((rawKey, rawValue) in raw) {
        val municipality = rawKey as? String ?: continue
        result[municipality] = (rawValue as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    }
    return result
}

package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.local.OutcomeRow
import com.auroraclima.app.questionnaire.domain.model.NewSavedOutcome
import com.auroraclima.app.questionnaire.domain.model.SavedOutcome
import com.auroraclima.app.questionnaire.domain.repository.SavedOutcomeRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray

/**
 * Resultados guardados en el almacén cifrado del cuestionario (RF-31). Las
 * etiquetas viajan como arreglo JSON dentro de la fila; una fila con `tags`
 * ilegible no tumba el listado — se lee sin etiquetas (mismo criterio de
 * tolerancia que el catálogo del historial).
 */
@Singleton
class LocalSavedOutcomeRepository @Inject constructor(
    private val storeAccess: GuidanceStoreAccess
) : SavedOutcomeRepository {
    override suspend fun save(outcome: NewSavedOutcome): Result<Long> = storeAccess.withDatabase { db ->
        db.outcomeDao().insert(
            OutcomeRow(
                savedAt = System.currentTimeMillis(),
                rank = outcome.rank,
                label = outcome.label,
                tags = encodeTags(outcome.tags)
            )
        )
    }

    override suspend fun outcomes(): Result<List<SavedOutcome>> =
        storeAccess.withDatabase { db -> db.outcomeDao().outcomes().map { it.toDomain() } }

    override suspend fun outcome(id: Long): Result<SavedOutcome?> =
        storeAccess.withDatabase { db -> db.outcomeDao().outcomeById(id)?.toDomain() }

    override suspend fun delete(id: Long): Result<Unit> =
        storeAccess.withDatabase { db -> db.outcomeDao().deleteById(id) }

    private fun OutcomeRow.toDomain() = SavedOutcome(
        id = id,
        savedAt = Instant.ofEpochMilli(savedAt),
        rank = rank,
        label = label,
        tags = decodeTags(tags)
    )

    companion object {
        fun encodeTags(tags: List<String>): String {
            val json = JSONArray()
            tags.forEach { json.put(it) }
            return json.toString()
        }

        fun decodeTags(encoded: String): List<String> = try {
            val json = JSONArray(encoded)
            List(json.length()) { json.getString(it) }
        } catch (unreadable: Exception) {
            emptyList()
        }
    }
}

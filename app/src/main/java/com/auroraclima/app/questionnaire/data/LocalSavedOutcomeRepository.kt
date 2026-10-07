package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.core.security.OpaqueIdSource
import com.auroraclima.app.questionnaire.data.local.OutcomeRow
import com.auroraclima.app.questionnaire.data.local.RemovedOutcomeRow
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
    private val storeAccess: GuidanceStoreAccess,
    private val opaqueIds: OpaqueIdSource
) : SavedOutcomeRepository {
    override suspend fun save(outcome: NewSavedOutcome): Result<Long> = storeAccess.withDatabase { db ->
        db.outcomeDao().insert(
            OutcomeRow(
                docId = opaqueIds.next(),
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

    /**
     * Borra y devuelve el nombre del documento en la nube para que el caso de
     * uso propague el borrado; de paso deja la constancia que impide que la
     * reconciliación lo baje otra vez mientras el `delete` sigue en la cola.
     */
    override suspend fun delete(id: Long): Result<String> = storeAccess.withDatabase { db ->
        val row = db.outcomeDao().outcomeById(id)
        db.outcomeDao().deleteById(id)
        if (row == null || row.docId.isEmpty()) return@withDatabase ""
        db.removedOutcomeDao().insert(
            RemovedOutcomeRow(docId = row.docId, at = System.currentTimeMillis())
        )
        row.docId
    }

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

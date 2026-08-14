package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.core.security.SyncKeyManager
import com.auroraclima.app.questionnaire.data.local.OutcomeRow
import com.auroraclima.app.questionnaire.data.remote.EncryptedOutcomeDto
import com.auroraclima.app.questionnaire.data.remote.OutcomeSyncRemoteSource
import com.auroraclima.app.questionnaire.domain.repository.OutcomeSyncRepository
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sincronización cifrada de los resultados guardados (RF-31 + RF-39), calcada
 * del contrato del historial: la fila local es la fuente de verdad, se sella EN
 * EL CLIENTE con [PayloadCipher] (la DEK recuperable del sobre doble) y el DTO
 * que sale jamás contiene texto claro. El docId es el id local (idempotencia de
 * re-subida y borrado); tras entregar la escritura queda la constancia
 * `synced_gen`. La restauración solo repone sobre un almacén vacío y omite en
 * silencio lo que no abre (blob de otra generación o esquema desconocido).
 */
@Singleton
class FirestoreOutcomeSyncRepository @Inject constructor(
    private val remote: OutcomeSyncRemoteSource,
    private val cipher: PayloadCipher,
    private val storeAccess: GuidanceStoreAccess,
    private val keyManager: SyncKeyManager
) : OutcomeSyncRepository {
    override suspend fun push(outcomeId: Long): Result<Unit> = networkResult {
        // Generación ANTES de sellar: la constancia debe corresponder a la DEK
        // usada. Sin DEK establecida se lanza y el guardado queda pendiente.
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        val row = storeAccess.withDatabase { db -> db.outcomeDao().outcomeById(outcomeId) }
            .getOrThrow() ?: throw IllegalStateException()
        val sealed = cipher.seal(row.serialize())
        remote.upload(
            documentId = row.id.toString(),
            document = EncryptedOutcomeDto(
                ciphertext = encode(sealed.ciphertext),
                iv = encode(sealed.iv),
                schemaVersion = PAYLOAD_SCHEMA_VERSION
            )
        )
        storeAccess.withDatabase { db -> db.outcomeDao().markSynced(row.id, generation) }.getOrThrow()
    }

    override suspend fun remove(outcomeId: Long): Result<Unit> = networkResult {
        remote.delete(outcomeId.toString())
    }

    override suspend fun restore(): Result<Unit> = networkResult {
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        val isEmpty = storeAccess.withDatabase { db -> db.outcomeDao().count() == 0L }.getOrThrow()
        if (!isEmpty) return@networkResult
        val documents = remote.downloadAll()
        storeAccess.withDatabase { db ->
            documents.forEach { (documentId, dto) ->
                val row = dto.toRow(documentId, generation) ?: return@forEach
                db.outcomeDao().insert(row)
            }
        }.getOrThrow()
    }

    /** Deshace [serialize]: el guardado se repone con su MISMO id y su fecha. */
    private fun EncryptedOutcomeDto.toRow(documentId: String, generation: Long): OutcomeRow? {
        if (schemaVersion != PAYLOAD_SCHEMA_VERSION) return null
        val id = documentId.toLongOrNull() ?: return null
        return try {
            val payload = cipher.open(
                SealedPayload(ciphertext = decode(ciphertext), iv = decode(iv))
            )
            val json = JSONObject(payload.toString(Charsets.UTF_8))
            OutcomeRow(
                id = id,
                savedAt = json.getLong(FIELD_SAVED_AT),
                rank = json.getInt(FIELD_RANK),
                label = json.getString(FIELD_LABEL),
                tags = json.getJSONArray(FIELD_TAGS).toString(),
                // Lo restaurado YA está en la nube con esta DEK: no se re-sube.
                syncedGeneration = generation
            )
        } catch (unreadable: Exception) {
            null
        }
    }

    /** JSON compacto del guardado. Va cifrado: los nombres pueden ser claros (RF-39). */
    private fun OutcomeRow.serialize(): ByteArray {
        val json = JSONObject()
            .put(FIELD_SAVED_AT, savedAt)
            .put(FIELD_RANK, rank)
            .put(FIELD_LABEL, label)
            .put(FIELD_TAGS, JSONArray(tags))
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    companion object {
        /** v1: los `outcomes` nacen ya con el sobre doble de RF-39. */
        const val PAYLOAD_SCHEMA_VERSION = 1L

        const val FIELD_SAVED_AT = "at"
        const val FIELD_RANK = "rank"
        const val FIELD_LABEL = "label"
        const val FIELD_TAGS = "tags"
    }
}

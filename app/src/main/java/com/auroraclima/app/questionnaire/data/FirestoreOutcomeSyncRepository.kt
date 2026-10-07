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
 * que sale jamás contiene texto claro. El nombre del documento es el `doc_id` propio de la fila (ADR 2026-10-06); los guardados anteriores conservan su id local (idempotencia de
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
            documentId = row.remoteName(),
            document = EncryptedOutcomeDto(
                ciphertext = encode(sealed.ciphertext),
                iv = encode(sealed.iv),
                schemaVersion = PAYLOAD_SCHEMA_VERSION
            )
        )
        storeAccess.withDatabase { db -> db.outcomeDao().markSynced(row.id, generation) }.getOrThrow()
    }

    override suspend fun remove(docId: String): Result<Unit> = networkResult {
        remote.delete(docId)
    }

    /**
     * Hace converger los resultados guardados con la nube (ADR 2026-10-06).
     * Un resultado nunca se edita, así que solo hay dos casos: apareció uno
     * guardado en el otro dispositivo (se inserta, salvo que aquí haya
     * constancia de que se eliminó) o desapareció uno que aquí ya había subido
     * (se elimina también aquí).
     */
    override suspend fun reconcile(): Result<Unit> = networkResult {
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        // La poda, antes de tocar la red (auditoría 2026-10-06 A-7).
        storeAccess.withDatabase { db ->
            db.removedOutcomeDao().deleteOlderThan(System.currentTimeMillis() - TOMBSTONE_LIFETIME_MS)
        }
        val documents = remote.downloadAll()
        val remoteNames = documents.map { (documentId, _) -> documentId }.toSet()
        storeAccess.withDatabase { db ->
            val discarded = db.removedOutcomeDao().docIds().toSet()
            documents.forEach { (documentId, dto) ->
                val incoming = dto?.toRow(documentId, generation)
                val local = db.outcomeDao().outcomeByDocId(documentId)
                // Mismo criterio que el historial: «visto en el servidor» solo
                // cuenta si el documento abre y, si ya hay fila con ese nombre,
                // es LA MISMA. Los nombres anteriores a la migración eran el id
                // local, así que dos teléfonos pudieron dar el mismo a
                // resultados distintos; marcar sin verificar dejaría que
                // eliminar el «1» en uno borrase otro resultado en el otro.
                // `savedAt` es aquí lo que `createdAt` allá: no cambia nunca.
                if (incoming != null && (local == null || incoming.savedAt == local.savedAt)) {
                    db.outcomeDao().markCloudSeen(documentId, generation)
                }
                if (incoming == null || documentId in discarded || local != null) return@forEach
                db.outcomeDao().insert(incoming.copy(cloudSeenGeneration = generation))
            }
            // Solo se borra lo que una lectura del servidor vio alguna vez: subir
            // únicamente encola (RF-38) y no prueba que el servidor lo tenga.
            // Visto por una lectura del servidor EN ESTA generación: tras
            // reiniciar el respaldo (RF-39) el servidor queda purgado y nada
            // puede borrarse solo.
            db.outcomeDao().seenInCloud(generation)
                .filter { it.docId.isNotEmpty() && it.docId !in remoteNames }
                .forEach { db.outcomeDao().deleteById(it.id) }
            // Solo las constancias ya asentadas se retiran (misma gracia que el
            // historial): una recién puesta debe sobrevivir a que la cola
            // durable aplique su borrado.
            db.removedOutcomeDao().docIdsSettled(System.currentTimeMillis() - TOMBSTONE_GRACE_MS)
                .filterNot { it in remoteNames }
                .forEach { db.removedOutcomeDao().delete(it) }
        }.getOrThrow()
    }

    override suspend fun restore(): Result<Unit> = networkResult {
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        val isEmpty = storeAccess.withDatabase { db -> db.outcomeDao().count() == 0L }.getOrThrow()
        if (!isEmpty) return@networkResult
        val documents = remote.downloadAll()
        storeAccess.withDatabase { db ->
            documents.forEach { (documentId, dto) ->
                val row = dto?.toRow(documentId, generation) ?: return@forEach
                db.outcomeDao().insert(row)
            }
        }.getOrThrow()
    }

    /**
     * Nombre del documento en la nube; los guardados anteriores a la migración
     * v2 conservan su id numérico, que es con el que ya están publicados.
     */
    private fun OutcomeRow.remoteName(): String = docId.ifEmpty { id.toString() }

    /**
     * Deshace [serialize]: el guardado se repone con su fecha. El id local lo
     * asigna la base (ADR 2026-10-06) y el nombre remoto se conserva aparte.
     */
    private fun EncryptedOutcomeDto.toRow(documentId: String, generation: Long): OutcomeRow? {
        if (schemaVersion != PAYLOAD_SCHEMA_VERSION) return null
        if (documentId.isEmpty()) return null
        return try {
            val payload = cipher.open(
                SealedPayload(ciphertext = decode(ciphertext), iv = decode(iv))
            )
            val json = JSONObject(payload.toString(Charsets.UTF_8))
            OutcomeRow(
                docId = documentId,
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

        /** Las constancias de borrado se podan pasados tres meses (ADR 2026-10-06). */
        private const val TOMBSTONE_LIFETIME_MS = 90L * 24 * 60 * 60 * 1000

        /** Una constancia recién puesta no se retira todavía (misma gracia que el historial). */
        private const val TOMBSTONE_GRACE_MS = 60L * 60 * 1000

        const val FIELD_SAVED_AT = "at"
        const val FIELD_RANK = "rank"
        const val FIELD_LABEL = "label"
        const val FIELD_TAGS = "tags"
    }
}

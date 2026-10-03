package com.auroraclima.app.records.data

import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.core.security.SyncKeyManager
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.data.remote.EncryptedEntryDto
import com.auroraclima.app.records.data.remote.JournalSyncRemoteSource
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Sincronización cifrada del historial (RF-37, RF-39). Lee la fila local
 * (fuente de verdad), la cifra EN EL CLIENTE con [PayloadCipher] — la DEK
 * recuperable del sobre doble — y solo entonces la entrega a Firestore: el DTO
 * que sale nunca contiene el contenido en claro. Todo fallo se traduce a
 * [Result] con `networkResult` (§3) — nada del SDK ni del contenido cruza a la
 * UI ni a los logs (regla 4).
 *
 * El id del documento es el id local del registro: mapeo determinista
 * local→nube que hace idempotentes la re-subida (RF-35, la migración RF-39) y
 * el borrado (RF-36). Tras entregar la escritura (la cola durable del SDK la
 * garantiza), la fila queda marcada con la generación de la DEK usada: es la
 * constancia que exime al reconciliador de re-subirla.
 *
 * El payload v2 suma `at` (`created_at`): la restauración en un dispositivo
 * nuevo debe reconstruir también el desempate del orden (RF-34 criterio 1).
 * El payload v3 (ADR 2026-10-03, P-68) lleva `kind` como id del tema publicado
 * y suma `label` (último nombre conocido); la restauración sigue abriendo los
 * v2 y traduce sus códigos legados con el mismo mapa que la migración local.
 */
@Singleton
class FirestoreJournalSyncRepository @Inject constructor(
    private val remote: JournalSyncRemoteSource,
    private val cipher: PayloadCipher,
    private val storeAccess: JournalStoreAccess,
    private val keyManager: SyncKeyManager
) : JournalSyncRepository {
    override suspend fun push(entryId: Long): Result<Unit> = networkResult {
        // Generación ANTES de sellar: la constancia debe corresponder a la DEK
        // usada. Sin DEK establecida se lanza y el registro queda pendiente.
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        val row = storeAccess.withDatabase { db -> db.journalDao().entryById(entryId) }
            .getOrThrow() ?: throw IllegalStateException()
        val sealed = cipher.seal(row.serialize())
        remote.upload(
            documentId = row.id.toString(),
            document = EncryptedEntryDto(
                ciphertext = encode(sealed.ciphertext),
                iv = encode(sealed.iv),
                schemaVersion = PAYLOAD_SCHEMA_VERSION
            )
        )
        storeAccess.withDatabase { db -> db.journalDao().markSynced(row.id, generation) }.getOrThrow()
    }

    override suspend fun remove(entryId: Long): Result<Unit> = networkResult {
        remote.delete(entryId.toString())
    }

    override suspend fun restore(): Result<Unit> = networkResult {
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        val isEmpty = storeAccess.withDatabase { db -> db.journalDao().count() == 0L }.getOrThrow()
        if (!isEmpty) return@networkResult
        val documents = remote.downloadAll()
        storeAccess.withDatabase { db ->
            documents.forEach { (documentId, dto) ->
                // Lo que no abre o no corresponde al formato se omite: un blob
                // v1 (llave del dispositivo viejo) o de una DEK anterior es
                // ilegible aquí por diseño y no debe tumbar la restauración.
                val row = dto.toRow(documentId, generation) ?: return@forEach
                db.journalDao().insert(row)
            }
        }.getOrThrow()
    }

    /**
     * Deshace [serialize]: el registro se repone con su MISMO id y su orden
     * (RF-34). Un payload v2 (dispositivo anterior a P-68) trae el código del
     * catálogo fijo: se traduce al id del tema; el restaurado queda con la
     * generación vigente igual que un v3 porque su copia remota sí abre.
     */
    private fun EncryptedEntryDto.toRow(documentId: String, generation: Long): JournalEntryRow? {
        if (schemaVersion !in READABLE_PAYLOAD_SCHEMAS) return null
        val id = documentId.toLongOrNull() ?: return null
        return try {
            val payload = cipher.open(
                SealedPayload(ciphertext = decode(ciphertext), iv = decode(iv))
            )
            val json = JSONObject(payload.toString(Charsets.UTF_8))
            val isLegacy = schemaVersion == LEGACY_PAYLOAD_SCHEMA_VERSION
            val kind = json.getString(FIELD_KIND)
            JournalEntryRow(
                id = id,
                day = json.getLong(FIELD_DAY),
                kind = if (isLegacy) kind.toTopicIdIfLegacy() else kind,
                label = if (isLegacy) "" else json.optString(FIELD_LABEL),
                detail = json.getString(FIELD_DETAIL),
                createdAt = json.optLong(FIELD_CREATED_AT),
                // Lo restaurado YA está en la nube con esta DEK: no se re-sube.
                syncedGeneration = generation
            )
        } catch (unreadable: Exception) {
            null
        }
    }

    /** JSON compacto del registro. Va cifrado, así que los nombres pueden ser claros (restaurar, RF-39). */
    private fun JournalEntryRow.serialize(): ByteArray {
        val json = JSONObject()
            .put(FIELD_DAY, day)
            .put(FIELD_KIND, kind)
            .put(FIELD_LABEL, label)
            .put(FIELD_DETAIL, detail)
            .put(FIELD_CREATED_AT, createdAt)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    companion object {
        /** v3 (P-68): `kind` es el id del tema publicado y `label` su último nombre conocido. */
        const val PAYLOAD_SCHEMA_VERSION = 3L

        /** v2 (RF-39): sellado con la DEK recuperable, `at` en el payload y `kind` del catálogo fijo. */
        const val LEGACY_PAYLOAD_SCHEMA_VERSION = 2L

        /** Lo que esta app sabe abrir al restaurar; v1 (llave del dispositivo viejo) sigue fuera. */
        private val READABLE_PAYLOAD_SCHEMAS = setOf(LEGACY_PAYLOAD_SCHEMA_VERSION, PAYLOAD_SCHEMA_VERSION)

        const val FIELD_DAY = "day"
        const val FIELD_KIND = "kind"
        const val FIELD_LABEL = "label"
        const val FIELD_DETAIL = "detail"
        const val FIELD_CREATED_AT = "at"
    }
}

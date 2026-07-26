package com.auroraclima.app.records.data

import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.core.security.PayloadCipher
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

    /** JSON compacto del registro. Va cifrado, así que los nombres pueden ser claros (restaurar, RF-39). */
    private fun JournalEntryRow.serialize(): ByteArray {
        val json = JSONObject()
            .put(FIELD_DAY, day)
            .put(FIELD_KIND, kind)
            .put(FIELD_DETAIL, detail)
            .put(FIELD_CREATED_AT, createdAt)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    companion object {
        /** v2 (RF-39): sellado con la DEK recuperable y `at` dentro del payload. */
        const val PAYLOAD_SCHEMA_VERSION = 2L

        const val FIELD_DAY = "day"
        const val FIELD_KIND = "kind"
        const val FIELD_DETAIL = "detail"
        const val FIELD_CREATED_AT = "at"
    }
}

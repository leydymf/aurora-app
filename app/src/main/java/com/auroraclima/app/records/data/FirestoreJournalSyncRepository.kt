package com.auroraclima.app.records.data

import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.records.data.remote.EncryptedEntryDto
import com.auroraclima.app.records.data.remote.JournalSyncRemoteSource
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Sincronización cifrada del historial (RF-37). Serializa el registro, lo cifra
 * EN EL CLIENTE con [PayloadCipher] y solo entonces lo entrega a Firestore: el
 * DTO que sale nunca contiene el contenido en claro. Todo fallo se traduce a
 * [Result] con `networkResult` (§3) — nada del SDK ni del contenido cruza a la UI
 * ni a los logs (regla 4).
 *
 * El id del documento es el id local del registro (`entry.id`): mapeo determinista
 * local→nube sin tocar el esquema local, que hace idempotentes la re-subida
 * (RF-35) y el borrado (RF-36).
 */
@Singleton
class FirestoreJournalSyncRepository @Inject constructor(
    private val remote: JournalSyncRemoteSource,
    private val cipher: PayloadCipher
) : JournalSyncRepository {
    override suspend fun push(entry: JournalEntry): Result<Unit> = networkResult {
        val sealed = cipher.seal(entry.serialize())
        remote.upload(
            documentId = entry.id.toString(),
            document = EncryptedEntryDto(
                ciphertext = encode(sealed.ciphertext),
                iv = encode(sealed.iv),
                schemaVersion = PAYLOAD_SCHEMA_VERSION
            )
        )
    }

    override suspend fun remove(entryId: Long): Result<Unit> = networkResult {
        remote.delete(entryId.toString())
    }

    /** JSON compacto del registro. Va cifrado, así que los nombres pueden ser claros (restaurar a futuro). */
    private fun JournalEntry.serialize(): ByteArray {
        val json = JSONObject()
            .put(FIELD_DAY, occurredOn.toEpochDay())
            .put(FIELD_KIND, kind.storageCode)
            .put(FIELD_DETAIL, detail)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val PAYLOAD_SCHEMA_VERSION = 1L
        const val FIELD_DAY = "day"
        const val FIELD_KIND = "kind"
        const val FIELD_DETAIL = "detail"
    }
}

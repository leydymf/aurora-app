package com.auroraclima.app.records.data

import android.content.Context
import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.RecoverableMediaCipher
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.core.security.SyncKeyManager
import com.auroraclima.app.records.data.local.AttachmentRow
import com.auroraclima.app.records.data.local.RemovedRow
import com.auroraclima.app.records.data.remote.EncryptedMediaHeaderDto
import com.auroraclima.app.records.data.remote.FirestoreJournalMediaRemoteSource
import com.auroraclima.app.records.data.remote.JournalMediaRemoteSource
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.repository.JournalMediaSyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Sync de evidencias troceada (RF-39). La subida re-cifra POR STREAM: el
 * archivo local (sellado con la llave del dispositivo) se abre con
 * [AttachmentStore] y se vuelve a sellar con la DEK recuperable
 * ([RecoverableMediaCipher]) hacia un TEMPORAL SOLO-CIFRADO de la caché — un
 * adjunto jamás pasa completo por memoria (principio de RF-33) ni toca el
 * disco en claro; del temporal salen los trozos base64 de ≤[CHUNK_BYTES]. La
 * restauración es la inversa exacta. Los metadatos (dueño, tipo, fecha) viajan
 * sellados con [PayloadCipher] en la cabecera: en la nube no hay NADA legible
 * más que el conteo de trozos.
 */
@Singleton
class FirestoreJournalMediaSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val remote: JournalMediaRemoteSource,
    private val storeAccess: JournalStoreAccess,
    private val store: AttachmentStore,
    private val travelCipher: RecoverableMediaCipher,
    private val metaCipher: PayloadCipher,
    private val keyManager: SyncKeyManager
) : JournalMediaSyncRepository {
    override suspend fun push(attachmentId: Long): Result<Unit> = networkResult {
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        val row = storeAccess.withDatabase { db -> db.attachmentDao().byId(attachmentId) }
            .getOrThrow() ?: throw IllegalStateException()
        // Un adjunto en preparación no tiene registro dueño todavía: no sube.
        if (row.owner == AttachmentPolicy.STAGED_OWNER) return@networkResult
        // El dueño viaja por su NOMBRE EN LA NUBE, no por el id local: en otro
        // dispositivo ese id no significa nada (ADR 2026-10-06).
        val ownerDocId = storeAccess.withDatabase { db -> db.journalDao().entryById(row.owner)?.docId }
            .getOrThrow().orEmpty().ifEmpty { row.owner.toString() }
        val sealed = sealForTravel(row.ref)
        try {
            remote.upload(
                ref = row.ref,
                header = headerOf(row, ownerDocId, partCount = countParts(sealed.length())),
                parts = chunk(sealed)
            )
        } finally {
            sealed.delete()
        }
        storeAccess.withDatabase { db -> db.attachmentDao().markSynced(row.id, generation) }.getOrThrow()
    }

    override suspend fun restore(): Result<Unit> = networkResult {
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        val isEmpty = storeAccess.withDatabase { db -> db.attachmentDao().count() == 0L }.getOrThrow()
        if (!isEmpty) return@networkResult
        remote.downloadHeaders().forEach { (ref, header) ->
            if (header == null) return@forEach
            // Lo que no abre, no está completo o es de un formato futuro se
            // omite en silencio: no debe tumbar la restauración (§3, §6). La
            // cancelación SÍ se relanza (contrato de la casa, revisión D2 M-2).
            try {
                restoreOne(ref, header, generation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unreadable: Exception) {
                // Adjunto ilegible o a medias: se omite y se continúa.
            }
        }
    }

    /**
     * Hace converger los adjuntos con la nube (ADR 2026-10-06). Mismo criterio
     * que el de registros, con una diferencia: los adjuntos no se editan, así
     * que solo hay dos casos — apareció uno nuevo (lo adjuntó el otro teléfono
     * a un registro que aquí ya existe) o desapareció uno que aquí ya había
     * subido (lo eliminaron allá), y entonces se va con su archivo.
     */
    override suspend fun reconcile(): Result<Unit> = networkResult {
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        val headers = remote.downloadHeaders()
        val remoteRefs = headers.map { (ref, _) -> ref }.toSet()
        val discarded = storeAccess.withDatabase { db -> db.removedDao().docIds(RemovedRow.SCOPE_MEDIA) }
            .getOrThrow().toSet()
        headers.forEach { (ref, header) ->
            // Visto en el servidor: desde ahora su ausencia SÍ significa algo.
            storeAccess.withDatabase { db -> db.attachmentDao().markCloudSeen(ref, generation) }
            if (header == null || ref in discarded) return@forEach
            val known = storeAccess.withDatabase { db -> db.attachmentDao().byRef(ref) != null }.getOrThrow()
            if (known) return@forEach
            try {
                restoreOne(ref, header, generation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (unreadable: Exception) {
                // Adjunto ilegible o a medias: se omite y se continúa.
            }
        }
        storeAccess.withDatabase { db ->
            // Solo se borra lo que una lectura del servidor vio alguna vez: subir
            // únicamente encola (RF-38) y no prueba que el servidor lo tenga.
            // Visto por una lectura del servidor EN ESTA generación, por el
            // mismo motivo que en registros: tras reiniciar el respaldo (RF-39)
            // nada puede borrarse solo.
            db.attachmentDao().seenInCloud(generation)
                .filterNot { it.ref in remoteRefs }
                .forEach { row ->
                    db.attachmentDao().deleteById(row.id)
                    store.delete(row.ref)
                }
            // Misma gracia que el historial para las constancias recién puestas.
            db.removedDao()
                .docIdsSettled(RemovedRow.SCOPE_MEDIA, System.currentTimeMillis() - TOMBSTONE_GRACE_MS)
                .filterNot { it in remoteRefs }
                .forEach { db.removedDao().delete(it, RemovedRow.SCOPE_MEDIA) }
        }.getOrThrow()
    }

    /** Repone UN adjunto; lanza (y su llamador omite) ante cualquier pieza ilegible. */
    private suspend fun restoreOne(ref: String, header: EncryptedMediaHeaderDto, generation: Long) {
        if (header.schemaVersion !in READABLE_MEDIA_SCHEMAS) return
        // Confianza mínima en datos de la nube (auditoría D2 M-1): el docId se
        // usa como nombre de archivo — solo se acepta el formato exacto del
        // almacén (32 hex; veda ../ y cualquier rareza) — y el conteo de trozos
        // no autenticado queda acotado antes de tocar red o disco.
        if (!REF_PATTERN.matches(ref)) return
        if (header.partCount !in 1..FirestoreJournalMediaRemoteSource.MAX_BLIND_PARTS) return
        val meta = JSONObject(
            metaCipher.open(SealedPayload(decode(header.meta), decode(header.iv))).toString(Charsets.UTF_8)
        )
        // v1 traía el id local del dispositivo de origen; tras la migración v6
        // ese número ES el nombre remoto de aquel registro, así que la búsqueda
        // por nombre sirve para las dos versiones de la cabecera.
        val ownerDocId = meta.getString(FIELD_OWNER)
        storeAccess.withDatabase { db ->
            // Solo adjuntos cuyo registro dueño exista (la restauración de
            // registros ya corrió); una cabecera huérfana se omite.
            // SOLO por nombre remoto. Una cabecera v1 trae el id local del
            // dispositivo de origen, que tras la migración v6 ES el nombre
            // remoto de aquel registro, así que esta búsqueda lo cubre. Buscar
            // además por id local sería peligroso: en un dispositivo
            // restaurado los ids locales son nuevos y no guardan relación con
            // los nombres, así que la evidencia podría pegarse a OTRO registro
            // (auditoría 2026-10-06). Sin dueño, la cabecera se omite.
            val owner = db.journalDao().entryByDocId(ownerDocId)?.id ?: return@withDatabase
            val sealed = assembleParts(ref, header.partCount)
            try {
                store.write(ref) { output ->
                    sealed.inputStream().use { raw ->
                        travelCipher.opening(raw).use { it.copyTo(output) }
                    }
                }
            } finally {
                sealed.delete()
            }
            db.attachmentDao().insert(
                AttachmentRow(
                    owner = owner,
                    ref = ref,
                    kind = meta.getString(FIELD_KIND),
                    createdAt = meta.optLong(FIELD_CREATED_AT),
                    // Ya está en la nube con esta DEK: no se re-sube, y viene de
                    // una lectura del servidor, así que está visto.
                    syncedGeneration = generation,
                    cloudSeenGeneration = generation
                )
            )
        }.getOrThrow()
    }

    /**
     * Temporales del viaje (siempre SOLO-CIFRADOS) en un subdirectorio propio:
     * el cleaner de cierre de sesión lo barre — un resto por muerte de proceso
     * no sobrevive entre sesiones (revisión D2 M-5).
     */
    private fun newRelayFile(): File {
        val directory = File(context.cacheDir, RELAY_DIRECTORY).apply { mkdirs() }
        return File.createTempFile(TEMP_PREFIX, null, directory)
    }

    /** Archivo local → temporal sellado con la DEK (nunca en claro, nunca completo en memoria). */
    private fun sealForTravel(ref: String): File {
        val sealed = newRelayFile()
        try {
            sealed.outputStream().use { raw ->
                travelCipher.sealing(raw).use { out -> store.read(ref) { input -> input.copyTo(out) } }
            }
            return sealed
        } catch (error: Exception) {
            sealed.delete()
            throw error
        }
    }

    /** Trozos base64 descargados → temporal sellado listo para abrirse con la DEK. */
    private suspend fun assembleParts(ref: String, partCount: Int): File {
        val sealed = newRelayFile()
        try {
            sealed.outputStream().use { out ->
                repeat(partCount) { index ->
                    val part = remote.downloadPart(ref, index) ?: throw IllegalStateException()
                    out.write(decode(part))
                }
            }
            return sealed
        } catch (error: Exception) {
            sealed.delete()
            throw error
        }
    }

    private fun chunk(sealed: File): List<String> {
        val parts = mutableListOf<String>()
        sealed.inputStream().use { input ->
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                val read = readFully(input, buffer)
                if (read <= 0) break
                parts += Base64.getEncoder().encodeToString(buffer.copyOf(read))
                if (read < buffer.size) break
            }
        }
        return parts
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val count = input.read(buffer, offset, buffer.size - offset)
            if (count < 0) break
            offset += count
        }
        return offset
    }

    private fun headerOf(row: AttachmentRow, ownerDocId: String, partCount: Int): EncryptedMediaHeaderDto {
        val meta = JSONObject()
            .put(FIELD_OWNER, ownerDocId)
            .put(FIELD_KIND, row.kind)
            .put(FIELD_CREATED_AT, row.createdAt)
        val sealed = metaCipher.seal(meta.toString().toByteArray(Charsets.UTF_8))
        return EncryptedMediaHeaderDto(
            meta = Base64.getEncoder().encodeToString(sealed.ciphertext),
            iv = Base64.getEncoder().encodeToString(sealed.iv),
            partCount = partCount,
            schemaVersion = MEDIA_SCHEMA_VERSION
        )
    }

    private fun countParts(sizeBytes: Long): Int = ((sizeBytes + CHUNK_BYTES - 1) / CHUNK_BYTES).toInt()

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    companion object {
        /** v2 (ADR 2026-10-06): el dueño va por su nombre en la nube, no por el id local. */
        /** Una constancia recién puesta no se retira todavía (ADR 2026-10-06). */
        private const val TOMBSTONE_GRACE_MS = 60L * 60 * 1000

        const val MEDIA_SCHEMA_VERSION = 2L

        /** v1: el dueño era el id local del dispositivo de origen. */
        const val LEGACY_MEDIA_SCHEMA_VERSION = 1L

        private val READABLE_MEDIA_SCHEMAS = setOf(LEGACY_MEDIA_SCHEMA_VERSION, MEDIA_SCHEMA_VERSION)

        /** ≤640 KiB por trozo: en base64 (~854 KiB) queda bajo el límite de 1 MiB por documento. */
        const val CHUNK_BYTES = 640 * 1024

        // Nombres neutros; el prefijo exige ≥3 caracteres (createTempFile).
        const val RELAY_DIRECTORY = "media_relay"
        private const val TEMP_PREFIX = "mtx"

        /** Formato exacto de los nombres del almacén (auditoría D2 M-1). */
        private val REF_PATTERN = Regex("[0-9a-f]{32}")
        private const val FIELD_OWNER = "owner"
        private const val FIELD_KIND = "kind"
        private const val FIELD_CREATED_AT = "at"
    }
}

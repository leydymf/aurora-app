package com.auroraclima.app.records.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.auroraclima.app.records.data.local.AttachmentRow
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.remote.JournalMediaRemoteSource
import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentContent
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalMediaSyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adjuntos protegidos sobre la BD cifrada central y el almacén de archivos
 * cifrados (RF-33, RNF-01). Toda operación de importación deja UNA copia
 * cifrada propia: el original de galería/archivos no se toca (política D0
 * §5.2); a las imágenes se les eliminan TODOS los metadatos re-codificándolas
 * ([ImageImporter]) y los archivos existentes pasan lista blanca de MIME y
 * tope de tamaño ANTES y DURANTE la copia (un tamaño no declarado se cuenta
 * al vuelo y aborta al exceder, sin cargar nada completo en memoria).
 *
 * Los fallos (origen ilegible, tipo no permitido, tamaño excedido, BD cifrada)
 * son todos el MISMO `Result.failure` — la UI solo muestra el genérico (§3).
 */
@Singleton
class LocalJournalAttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storeAccess: JournalStoreAccess,
    private val store: AttachmentStore,
    private val imageImporter: ImageImporter,
    private val captureBuffer: CaptureBuffer,
    private val mediaRemote: JournalMediaRemoteSource,
    private val mediaSync: JournalMediaSyncRepository
) : JournalAttachmentRepository {
    override suspend fun importImage(uri: String, owner: Long): Result<JournalAttachment> =
        storeAccess.withDatabase { db ->
            requireCapacity(db, owner)
            val parsed = Uri.parse(uri)
            importSanitizedImage(db, owner) {
                context.contentResolver.openInputStream(parsed) ?: throw IOException()
            }
        }.alsoPushIfClaimed(owner)

    override suspend fun importDocument(uri: String, owner: Long): Result<JournalAttachment> =
        storeAccess.withDatabase { db ->
            requireCapacity(db, owner)
            val parsed = Uri.parse(uri)
            val resolver = context.contentResolver
            val mime = resolver.getType(parsed) ?: throw IOException()
            if (!AttachmentPolicy.isAllowedDocumentType(mime)) throw IOException()
            val kind = AttachmentKind.fromMime(mime) ?: throw IOException()
            val declaredSize = declaredSizeOf(parsed)
            if (declaredSize != null && !AttachmentPolicy.isWithinDocumentSize(declaredSize)) throw IOException()
            val ref = store.newRef()
            store.write(ref) { output ->
                val input = resolver.openInputStream(parsed) ?: throw IOException()
                input.use { copyCapped(it, output) }
            }
            val id = db.attachmentDao().insert(newRow(owner = owner, ref = ref, kind = mime))
            JournalAttachment(id = id, kind = kind)
        }.alsoPushIfClaimed(owner)

    override suspend fun beginCapture(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(captureBuffer.begin().toString())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun importCapture(owner: Long): Result<JournalAttachment> = storeAccess.withDatabase { db ->
        try {
            requireCapacity(db, owner)
            val pending = captureBuffer.pending() ?: throw IOException()
            importSanitizedImage(db, owner) { pending.inputStream() }
        } finally {
            // El temporal en claro no debe sobrevivir a la importación,
            // exitosa o no (política D0 §5.1.ii).
            captureBuffer.clear()
        }
    }.alsoPushIfClaimed(owner)

    override suspend fun discardCapture() {
        withContext(Dispatchers.IO) { runCatching { captureBuffer.clear() } }
    }

    override suspend fun attachments(owner: Long): Result<List<JournalAttachment>> = storeAccess.withDatabase { db ->
        db.attachmentDao().byOwner(owner).mapNotNull { it.toDomain() }
    }

    override suspend fun counts(): Result<Map<Long, Int>> = storeAccess.withDatabase { db ->
        db.attachmentDao().countsByOwner().associate { it.owner to it.total }
    }

    override suspend fun content(id: Long): Result<AttachmentContent?> = storeAccess.withDatabase { db ->
        val row = db.attachmentDao().byId(id) ?: return@withDatabase null
        when (AttachmentKind.fromMime(row.kind)) {
            AttachmentKind.IMAGE -> AttachmentContent.Image(store.read(row.ref) { it.readBytes() })
            AttachmentKind.DOCUMENT -> AttachmentContent.Document(store.newViewFile(row.ref).path)
            // El audio no tiene visor en el MVP (decisión de sesión, ADR RF-33).
            else -> null
        }
    }

    override suspend fun exportable(id: Long): Result<AttachmentBytes?> = storeAccess.withDatabase { db ->
        val row = db.attachmentDao().byId(id) ?: return@withDatabase null
        val kind = AttachmentKind.fromMime(row.kind) ?: return@withDatabase null
        AttachmentBytes(kind, row.kind, store.read(row.ref) { it.readBytes() })
    }

    override suspend fun claim(ids: List<Long>, owner: Long): Result<Unit> = storeAccess.withDatabase { db ->
        if (ids.isNotEmpty()) db.attachmentDao().claim(ids, owner)
    }

    override suspend fun delete(id: Long): Result<Unit> = storeAccess.withDatabase { db ->
        val row = db.attachmentDao().byId(id) ?: return@withDatabase
        db.attachmentDao().deleteById(id)
        store.delete(row.ref)
        removeRemoteCopy(row)
    }

    override suspend fun deleteForOwner(owner: Long): Result<Unit> = storeAccess.withDatabase { db ->
        val rows = db.attachmentDao().byOwner(owner)
        if (rows.isEmpty()) return@withDatabase
        db.attachmentDao().deleteByIds(rows.map { it.id })
        rows.forEach {
            store.delete(it.ref)
            removeRemoteCopy(it)
        }
    }

    override suspend fun sweepOrphans(): Result<Unit> = storeAccess.withDatabase { db ->
        val rows = db.attachmentDao().orphans()
        if (rows.isNotEmpty()) {
            db.attachmentDao().deleteByIds(rows.map { it.id })
            rows.forEach {
                store.delete(it.ref)
                // Un huérfano reclamado (resto de un borrado interrumpido) pudo
                // haber subido: su copia remota se va con él (RF-36 criterio 2).
                removeRemoteCopy(it)
            }
        }
        // Restos de visualización de un proceso muerto en la ventana de apertura.
        store.clearViewCache()
    }

    /**
     * Subida de mejor esfuerzo del adjunto importado YA reclamado (RF-39,
     * verificación D2 H1): en modo edición el import asigna el dueño de una
     * vez y NO pasa por el claim del guardado — sin este push, la evidencia
     * añadida al editar no subiría hasta el siguiente reingreso. En creación
     * (owner = 0) no hace nada: el caso de uso empuja tras el claim. Mismo
     * criterio de convergencia en el repositorio que [removeRemoteCopy].
     */
    private suspend fun Result<JournalAttachment>.alsoPushIfClaimed(owner: Long): Result<JournalAttachment> {
        if (owner == AttachmentPolicy.STAGED_OWNER) return this
        // Mejor esfuerzo silencioso (§3): el resultado del push se descarta —
        // la fila quedó pendiente y el reconciliador la retoma.
        getOrNull()?.let { attachment -> mediaSync.push(attachment.id) }
        return this
    }

    /**
     * Borrado remoto de mejor esfuerzo (RF-36 criterio 2, RF-39): los adjuntos
     * en preparación jamás subieron y se omiten; para los reclamados, el
     * `delete` queda en la cola durable del SDK y se aplica al reconectar —
     * ningún fallo revierte el borrado local ni asoma un error (§3).
     */
    private suspend fun removeRemoteCopy(row: AttachmentRow) {
        if (row.owner == AttachmentPolicy.STAGED_OWNER) return
        try {
            mediaRemote.delete(row.ref)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Exception) {
            // Silencioso: la purga de RF-20 barre cualquier resto.
        }
    }

    private suspend fun importSanitizedImage(
        db: JournalDatabase,
        owner: Long,
        open: () -> InputStream
    ): JournalAttachment {
        val ref = store.newRef()
        store.write(ref) { output -> imageImporter.sanitize(open, output) }
        val id = db.attachmentDao().insert(
            newRow(owner = owner, ref = ref, kind = AttachmentPolicy.IMPORTED_IMAGE_MIME)
        )
        return JournalAttachment(id = id, kind = AttachmentKind.IMAGE)
    }

    private suspend fun requireCapacity(db: JournalDatabase, owner: Long) {
        if (!AttachmentPolicy.hasCapacity(db.attachmentDao().byOwner(owner).size)) throw IOException()
    }

    private fun newRow(owner: Long, ref: String, kind: String) = AttachmentRow(
        owner = owner,
        ref = ref,
        kind = kind,
        createdAt = System.currentTimeMillis()
    )

    /** Copia por stream con tope: al exceder el máximo se aborta (el almacén borra el parcial). */
    private fun copyCapped(input: InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            if (copied > AttachmentPolicy.MAX_DOCUMENT_BYTES) throw IOException()
            output.write(buffer, 0, count)
        }
        if (copied == 0L) throw IOException()
    }

    private fun declaredSizeOf(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()

    private fun AttachmentRow.toDomain(): JournalAttachment? =
        AttachmentKind.fromMime(kind)?.let { JournalAttachment(id = id, kind = it, sizeBytes = store.sizeOf(ref)) }

    private companion object {
        const val COPY_BUFFER_BYTES = 8 * 1024
    }
}

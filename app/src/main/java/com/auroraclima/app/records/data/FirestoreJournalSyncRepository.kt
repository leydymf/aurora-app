package com.auroraclima.app.records.data

import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SealedPayload
import com.auroraclima.app.core.security.SyncKeyManager
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.data.local.RemovedRow
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
 * El nombre del documento es el `doc_id` PROPIO de la fila, no su id local
 * (ADR 2026-10-06): con el id local, dos dispositivos de la misma cuenta podían
 * dar el mismo nombre a registros distintos. Los registros anteriores a la
 * migración v6 conservan su id numérico, que es con el que ya están
 * publicados. Tras entregar la escritura (la cola durable del SDK la
 * garantiza), la fila queda marcada con la generación de la DEK usada y deja de
 * estar pendiente: es la constancia que exime al reconciliador de re-subirla.
 *
 * El payload v2 suma `at` (`created_at`): la restauración en un dispositivo
 * nuevo debe reconstruir también el desempate del orden (RF-34 criterio 1).
 * El payload v3 (ADR 2026-10-03, P-68) lleva `kind` como id del tema publicado
 * y suma `label` (último nombre conocido); el v4 (ADR 2026-10-06) suma `up`, la
 * fecha de edición que desempata entre dispositivos. La lectura sigue abriendo
 * v2 y v3, y traduce los códigos legados con el mismo mapa que la migración.
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
            documentId = row.remoteName(),
            document = EncryptedEntryDto(
                ciphertext = encode(sealed.ciphertext),
                iv = encode(sealed.iv),
                schemaVersion = PAYLOAD_SCHEMA_VERSION
            )
        )
        storeAccess.withDatabase { db ->
            // La fecha de la fila que se SELLÓ: si la usuaria editó mientras
            // tanto, la marca no se aplica y el registro sigue pendiente.
            db.journalDao().markSynced(row.id, generation, row.updatedAt)
        }.getOrThrow()
    }

    override suspend fun remove(docId: String): Result<Unit> = networkResult {
        remote.delete(docId)
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
                val row = dto?.toRow(documentId, generation) ?: return@forEach
                db.journalDao().insert(row)
            }
        }.getOrThrow()
    }

    /**
     * Hace converger este dispositivo con la nube (ADR 2026-10-06). A
     * diferencia de [restore], corre sobre un historial que YA tiene datos.
     *
     * Reglas, por documento:
     * - **Eliminado en la nube** y aquí con la constancia al día (subió con esta
     *   misma DEK) → se elimina también aquí, con sus adjuntos. Gana la
     *   eliminación aunque haya una edición local sin subir: si la usuaria
     *   borró ese registro en su otro teléfono, lo quiso fuera.
     * - **Nunca subido** (constancia desfasada: creado sin conexión, o la DEK
     *   rotó) → se conserva; el reconciliador de RF-39 lo sube.
     * - **Editado en otro dispositivo y más reciente** → se adopta aquí.
     * - **Creado en otro dispositivo** → se inserta, salvo que haya constancia
     *   de que aquí se eliminó y el borrado siga en la cola.
     *
     * Los adjuntos van aparte, DESPUÉS, porque necesitan a su registro dueño.
     */
    override suspend fun reconcile(): Result<List<Long>> = networkResult {
        val generation = keyManager.currentGeneration() ?: throw IllegalStateException()
        // La poda va ANTES de tocar la red: si no, un dispositivo que pase meses
        // sin conexión no podaría nunca (auditoría 2026-10-06 A-7).
        storeAccess.withDatabase { db ->
            db.removedDao().deleteOlderThan(System.currentTimeMillis() - TOMBSTONE_LIFETIME_MS)
        }
        val documents = remote.downloadAll()
        val remoteNames = documents.map { (documentId, _) -> documentId }.toSet()
        storeAccess.withDatabase { db ->
            // Todas las constancias vigentes: ninguna de ellas se vuelve a bajar.
            val discarded = db.removedDao().docIds(RemovedRow.SCOPE_ENTRY).toSet()
            // Solo las ya asentadas pueden retirarse: una recién puesta debe
            // sobrevivir a que la cola durable aplique su borrado (A-6).
            val settled = db.removedDao()
                .docIdsSettled(RemovedRow.SCOPE_ENTRY, System.currentTimeMillis() - TOMBSTONE_GRACE_MS)
                .toSet()
            applyIncoming(db, documents, discarded, generation)
            applyRemovals(db, remoteNames, settled, generation)
        }.getOrThrow()
    }

    /** Trae de la nube lo creado y lo editado allá, y deja constancia de lo visto. */
    private suspend fun applyIncoming(
        db: JournalDatabase,
        documents: List<Pair<String, EncryptedEntryDto?>>,
        discarded: Set<String>,
        generation: Long
    ) {
        documents.forEach { (documentId, dto) ->
            if (documentId in discarded) return@forEach
            val incoming = dto?.toRow(documentId, generation)
            val local = db.journalDao().entryByDocId(documentId)
            // «Visto en el servidor» solo cuenta si además es EL MISMO registro
            // (auditoría 2026-10-06 A-2). Antes del ADR el nombre era el id
            // local, así que dos dispositivos pudieron dar el mismo a registros
            // distintos: marcar sin verificar permitiría que eliminar el «1» de
            // un teléfono borrase un registro ajeno en el otro. La fecha de
            // creación no cambia al editar, así que para un nombre opaco
            // siempre coincide y esto no estorba.
            if (incoming != null && (local == null || incoming.createdAt == local.createdAt)) {
                db.journalDao().markCloudSeen(documentId, generation)
            }
            if (incoming == null) return@forEach
            when {
                local == null -> db.journalDao().insert(incoming.copy(cloudSeenGeneration = generation))
                // Guarda contra los nombres legados: antes del ADR 2026-10-06 el
                // nombre era el id local, y dos dispositivos podían asignar el
                // mismo a registros DISTINTOS. Si la fecha de creación no
                // coincide no son el mismo registro, y adoptar sería pisar uno
                // con otro; se deja como está y el conflicto queda visible.
                incoming.createdAt != local.createdAt -> Unit
                incoming.updatedAt > local.updatedAt -> db.journalDao().adopt(
                    id = local.id,
                    day = incoming.day,
                    kind = incoming.kind,
                    label = incoming.label,
                    detail = incoming.detail,
                    updatedAt = incoming.updatedAt,
                    generation = generation
                )
            }
        }
    }

    /**
     * Elimina lo que desapareció de la nube y devuelve sus ids locales.
     *
     * Dos condiciones, y las dos hacen falta:
     *
     * Una sola condición, que encierra dos: que una lectura del SERVIDOR haya
     * visto ESE documento —y fuera el mismo registro— **en la generación de la
     * DEK que está vigente ahora**.
     *
     * - Subir solo ENCOLA (RF-38), así que la constancia de subida no prueba
     *   que el servidor lo tenga: hace falta haberlo leído de allá.
     * - Y hace falta que sea de esta generación: tras reiniciar el respaldo
     *   (RF-39, contraseña restablecida sin copia local) el servidor se purga y
     *   la generación sube. Con una marca de la era anterior, el primer
     *   reingreso borraría TODO el historial local, que es justo lo contrario
     *   de lo que promete el aviso de RF-46 (auditoría 2026-10-06).
     *
     * Que el registro tenga además cambios locales sin subir no lo salva: ahí
     * gana la eliminación, como decidió la usuaria.
     */
    private suspend fun applyRemovals(
        db: JournalDatabase,
        remoteNames: Set<String>,
        discarded: Set<String>,
        generation: Long
    ): List<Long> {
        val removedElsewhere = db.journalDao().entries()
            // Visto por una lectura del servidor EN ESTA generación: una marca
            // de antes de un reinicio de respaldo no vale, porque la purga se
            // llevó el documento sin que nadie lo eliminara.
            .filter { it.cloudSeenGeneration == generation }
            .filter { it.docId.isNotEmpty() && it.docId !in remoteNames }
        removedElsewhere.forEach { row ->
            db.journalDao().deleteById(row.id)
            // Constancia + borrado remoto idempotente: si una subida encolada
            // desde antes resucitara el documento, la cola aplica este borrado
            // después y la constancia impide volver a bajarlo mientras tanto.
            db.removedDao().insert(
                RemovedRow(docId = row.docId, scope = RemovedRow.SCOPE_ENTRY, at = System.currentTimeMillis())
            )
            runCatching { remote.delete(row.docId) }
        }
        // Constancias ya confirmadas por la nube: el documento no está.
        discarded.filterNot { it in remoteNames }
            .forEach { db.removedDao().delete(it, RemovedRow.SCOPE_ENTRY) }
        return removedElsewhere.map { it.id }
    }

    /**
     * Deshace [serialize]. El id LOCAL ya no sale del nombre del documento
     * (ADR 2026-10-06): lo asigna la base al insertar y el nombre remoto se
     * conserva aparte, en `doc_id`. Un payload v2 (dispositivo anterior a
     * P-68) trae el código del catálogo fijo: se traduce al id del tema. Un
     * payload v2 o v3 no trae fecha de edición: se usa la de creación, que es
     * exactamente lo que vale para un registro que ese dispositivo no editó.
     * Lo que llega queda con la generación vigente porque su copia remota sí
     * abre: no hay que re-subirlo.
     */
    private fun EncryptedEntryDto.toRow(documentId: String, generation: Long): JournalEntryRow? {
        if (schemaVersion !in READABLE_PAYLOAD_SCHEMAS) return null
        if (documentId.isEmpty()) return null
        return try {
            val payload = cipher.open(
                SealedPayload(ciphertext = decode(ciphertext), iv = decode(iv))
            )
            val json = JSONObject(payload.toString(Charsets.UTF_8))
            val isLegacy = schemaVersion == LEGACY_PAYLOAD_SCHEMA_VERSION
            val kind = json.getString(FIELD_KIND)
            val createdAt = json.optLong(FIELD_CREATED_AT)
            JournalEntryRow(
                docId = documentId,
                day = json.getLong(FIELD_DAY),
                kind = if (isLegacy) kind.toTopicIdIfLegacy() else kind,
                label = if (isLegacy) "" else json.optString(FIELD_LABEL),
                detail = json.getString(FIELD_DETAIL),
                createdAt = createdAt,
                updatedAt = json.optLong(FIELD_UPDATED_AT, createdAt),
                // Lo restaurado YA está en la nube con esta DEK: no se re-sube.
                syncedGeneration = generation
            )
        } catch (unreadable: Exception) {
            null
        }
    }

    /**
     * Nombre del documento en la nube. Los registros creados desde el ADR
     * 2026-10-06 lo traen propio; los anteriores a la migración v6 conservan su
     * id numérico, que es con el que ya están publicados.
     */
    private fun JournalEntryRow.remoteName(): String = docId.ifEmpty { id.toString() }

    /** JSON compacto del registro. Va cifrado, así que los nombres pueden ser claros (restaurar, RF-39). */
    private fun JournalEntryRow.serialize(): ByteArray {
        val json = JSONObject()
            .put(FIELD_DAY, day)
            .put(FIELD_KIND, kind)
            .put(FIELD_LABEL, label)
            .put(FIELD_DETAIL, detail)
            .put(FIELD_CREATED_AT, createdAt)
            .put(FIELD_UPDATED_AT, updatedAt)
        return json.toString().toByteArray(Charsets.UTF_8)
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    companion object {
        /** v4 (ADR 2026-10-06): suma `up`, la fecha de edición que desempata entre dispositivos. */
        const val PAYLOAD_SCHEMA_VERSION = 4L

        /** v3 (P-68): `kind` es el id del tema publicado y `label` su último nombre conocido. */
        const val KIND_BY_TOPIC_PAYLOAD_SCHEMA_VERSION = 3L

        /** v2 (RF-39): sellado con la DEK recuperable, `at` en el payload y `kind` del catálogo fijo. */
        const val LEGACY_PAYLOAD_SCHEMA_VERSION = 2L

        /** Lo que esta app sabe abrir al restaurar; v1 (llave del dispositivo viejo) sigue fuera. */
        private val READABLE_PAYLOAD_SCHEMAS = setOf(
            LEGACY_PAYLOAD_SCHEMA_VERSION,
            KIND_BY_TOPIC_PAYLOAD_SCHEMA_VERSION,
            PAYLOAD_SCHEMA_VERSION
        )

        const val FIELD_DAY = "day"
        const val FIELD_KIND = "kind"
        const val FIELD_LABEL = "label"
        const val FIELD_DETAIL = "detail"
        const val FIELD_CREATED_AT = "at"
        const val FIELD_UPDATED_AT = "up"

        /** Las constancias de borrado se podan pasados tres meses (ADR 2026-10-06). */
        private const val TOMBSTONE_LIFETIME_MS = 90L * 24 * 60 * 60 * 1000

        /**
         * Una constancia recién puesta no se retira en la misma pasada ni en la
         * siguiente inmediata: debe sobrevivir a que la cola durable aplique el
         * borrado (auditoría 2026-10-06 A-6).
         */
        private const val TOMBSTONE_GRACE_MS = 60L * 60 * 1000
    }
}

package com.auroraclima.app.records.data

import com.auroraclima.app.core.security.AccountDataReconciler
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.SyncKeyManager
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalMediaSyncRepository
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciliación del historial con la nube al confirmarse la contraseña
 * (RF-39; corre como [AccountDataRestorer] tras el establecimiento de llaves
 * del login). Re-sube todo registro cuya constancia (`synced_gen`) no
 * corresponda a la generación vigente de la DEK — eso convierte en UN solo
 * mecanismo, idempotente y reanudable:
 *
 * - la migración de los documentos v1 (llave de dispositivo) al sobre nuevo:
 *   la columna nace en 0 para todo el historial existente y cada registro se
 *   re-cifra y sobreescribe su MISMO docId;
 * - la interrupción de esa migración a mitad: lo ya marcado no se repite, lo
 *   pendiente se retoma en el siguiente login;
 * - los push que fallaron en su momento (sin sesión, sin DEK) y quedaron en 0;
 * - la rotación de DEK tras un reinicio del respaldo: la generación nueva deja
 *   todas las constancias desfasadas y el historial completo vuelve a subir.
 *
 * Mejor esfuerzo y silencioso (§3): cada push falla o avanza por separado y
 * ninguna excepción sale de aquí (regla 4).
 */
@Singleton
class JournalSyncReconciler @Inject constructor(
    private val storeAccess: JournalStoreAccess,
    private val syncRepository: JournalSyncRepository,
    private val mediaSyncRepository: JournalMediaSyncRepository,
    private val attachmentRepository: JournalAttachmentRepository,
    private val keyManager: SyncKeyManager
) : AccountDataRestorer, AccountDataReconciler {
    override suspend fun restore() {
        val generation = currentGenerationOrNull() ?: return
        // Primero reponer (solo actúa sobre un historial vacío: dispositivo
        // nuevo, RF-39 criterio 1); su fallo es silencioso y no impide subir.
        // Las evidencias van DESPUÉS de los registros: su reposición exige que
        // el registro dueño ya exista localmente.
        syncRepository.restore()
        mediaSyncRepository.restore()
        // Push por registro: cada éxito deja su constancia de inmediato — una
        // interrupción conserva el avance y el reintento solo re-sube el resto.
        // Igual para las evidencias reclamadas (la migración de los adjuntos
        // existentes al esquema troceado es exactamente este camino).
        pushPending(generation)
    }

    /**
     * Trae de la nube lo que se creó, editó o eliminó en el otro dispositivo
     * (ADR 2026-10-06), y después sube lo que aquí quedó pendiente — en ese
     * orden, para que una eliminación venida de la nube no se cruce con la
     * re-subida del mismo registro.
     *
     * Los adjuntos de un registro eliminado allá se eliminan aquí por el
     * repositorio de adjuntos, que es quien sabe borrar también sus archivos.
     * Las evidencias se reconcilian al final: necesitan a su registro dueño.
     */
    override suspend fun reconcile() {
        val generation = currentGenerationOrNull() ?: return
        // PRIMERO bajar y borrar, DESPUÉS subir (corrección de la segunda
        // auditoría del 2026-10-06). Al revés, un registro eliminado en el otro
        // teléfono y editado aquí sin subir se re-subía antes de enterarse de
        // su eliminación: o ganaba la edición, o el `set` encolado lo resucitaba
        // en la nube después de borrarlo. `cloud_seen` ya protege lo que
        // motivaba el orden inverso, así que este es seguro y además respeta la
        // regla del conflicto: gana la eliminación.
        val removedElsewhere = syncRepository.reconcile().getOrNull() ?: return
        // Los adjuntos de un registro eliminado allá los borra el repositorio
        // de adjuntos, que es quien sabe borrar también sus archivos.
        removedElsewhere.forEach { entryId -> attachmentRepository.deleteForOwner(entryId) }
        // Las evidencias, con su registro dueño ya resuelto.
        mediaSyncRepository.reconcile()
        // Y al final lo que aquí quedó pendiente de subir.
        pushPending(generation)
    }

    /** Cola de subida compartida por la restauración y la reconciliación. */
    private suspend fun pushPending(generation: Long) {
        val pending = storeAccess.withDatabase { db -> db.journalDao().pendingSync(generation) }
            .getOrNull() ?: return
        pending.forEach { row -> syncRepository.push(row.id) }
        val pendingMedia = storeAccess.withDatabase { db -> db.attachmentDao().pendingSync(generation) }
            .getOrNull() ?: return
        pendingMedia.forEach { row -> mediaSyncRepository.push(row.id) }
    }

    // currentGeneration lanza si la copia local no descifra (contrato DekStore);
    // aquí eso significa simplemente «hoy no se puede reconciliar».
    private fun currentGenerationOrNull(): Long? = try {
        keyManager.currentGeneration()
    } catch (unavailable: Exception) {
        null
    }
}

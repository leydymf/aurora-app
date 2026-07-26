package com.auroraclima.app.records.data

import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.SyncKeyManager
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
    private val keyManager: SyncKeyManager
) : AccountDataRestorer {
    override suspend fun restore() {
        val generation = currentGenerationOrNull() ?: return
        // Primero reponer (solo actúa sobre un historial vacío: dispositivo
        // nuevo, RF-39 criterio 1); su fallo es silencioso y no impide subir.
        syncRepository.restore()
        val pending = storeAccess.withDatabase { db -> db.journalDao().pendingSync(generation) }
            .getOrNull() ?: return
        // Push por registro: cada éxito deja su constancia de inmediato — una
        // interrupción conserva el avance y el reintento solo re-sube el resto.
        pending.forEach { row -> syncRepository.push(row.id) }
    }

    // currentGeneration lanza si la copia local no descifra (contrato DekStore);
    // aquí eso significa simplemente «hoy no se puede reconciliar».
    private fun currentGenerationOrNull(): Long? = try {
        keyManager.currentGeneration()
    } catch (unavailable: Exception) {
        null
    }
}

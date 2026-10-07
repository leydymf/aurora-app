package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.core.security.AccountDataReconciler
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.SyncKeyManager
import com.auroraclima.app.questionnaire.domain.repository.OutcomeSyncRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciliación de los resultados guardados con la nube tras cada login
 * (RF-31 + RF-39), como [AccountDataRestorer] en multibinding — el mismo
 * mecanismo idempotente del historial: primero reponer (solo actúa sobre un
 * almacén vacío: dispositivo nuevo), después re-subir todo guardado cuya
 * constancia no corresponda a la generación vigente (push perdidos, rotación de
 * DEK tras un reinicio del respaldo). Mejor esfuerzo y silencioso (§3, regla 4).
 */
@Singleton
class OutcomeSyncReconciler @Inject constructor(
    private val storeAccess: GuidanceStoreAccess,
    private val syncRepository: OutcomeSyncRepository,
    private val keyManager: SyncKeyManager
) : AccountDataRestorer, AccountDataReconciler {
    override suspend fun restore() {
        val generation = currentGenerationOrNull() ?: return
        syncRepository.restore()
        // Push por guardado: cada éxito deja su constancia de inmediato — una
        // interrupción conserva el avance y el reintento solo re-sube el resto.
        pushPending(generation)
    }

    /**
     * Trae de la nube lo que se guardó o se eliminó en el otro dispositivo
     * (ADR 2026-10-06) y después sube lo que aquí quedó pendiente.
     */
    override suspend fun reconcile() {
        val generation = currentGenerationOrNull() ?: return
        // Bajar y borrar primero, subir después: igual que en el historial, y
        // por el mismo motivo (segunda auditoría del 2026-10-06).
        syncRepository.reconcile()
        pushPending(generation)
    }

    /** Cola de subida compartida por la restauración y la reconciliación. */
    private suspend fun pushPending(generation: Long) {
        val pending = storeAccess.withDatabase { db -> db.outcomeDao().pendingSync(generation) }
            .getOrNull() ?: return
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

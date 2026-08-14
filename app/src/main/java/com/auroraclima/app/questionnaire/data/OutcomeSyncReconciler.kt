package com.auroraclima.app.questionnaire.data

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
) : AccountDataRestorer {
    override suspend fun restore() {
        val generation = currentGenerationOrNull() ?: return
        syncRepository.restore()
        val pending = storeAccess.withDatabase { db -> db.outcomeDao().pendingSync(generation) }
            .getOrNull() ?: return
        // Push por guardado: cada éxito deja su constancia de inmediato — una
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

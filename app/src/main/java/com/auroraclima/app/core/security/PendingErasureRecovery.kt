package com.auroraclima.app.core.security

import com.auroraclima.app.core.startup.AppInitializer
import javax.inject.Inject
import javax.inject.Provider

/**
 * Tarea de arranque (RF-20, auditoría H-1): si quedó izada la marca de borrado
 * local pendiente — el proceso murió entre borrar la cuenta y terminar lo
 * local — completa los erasers locales y la retira. Idempotente y silenciosa:
 * borra hacia el estado de instalación limpia, así que correr de más no daña ni
 * delata nada. También reintenta lo que un eraser fallido dejó a medias (H-3).
 * Contiene sus propios fallos aunque `runStartupTasks` ya contenga los del
 * arranque (doble contención deliberada: la clase no depende de la indulgencia
 * de su llamador), y ningún camino de excepción baja la marca.
 */
class PendingErasureRecovery @Inject constructor(
    private val pendingErasure: PendingErasureFlag,
    // Provider y no Set: el conjunto arrastra Firestore/FirebaseAuth y solo se
    // construye si la marca está izada (ADR 2026-10-05, RNF-07).
    private val localErasers: Provider<Set<@JvmSuppressWildcards LocalAccountDataEraser>>
) : AppInitializer {
    override suspend fun initialize() {
        if (!pendingErasure.isRaised()) return
        // Si el conjunto ni se puede construir (servicios ausentes), la marca
        // queda izada y el siguiente arranque lo reintenta (ADR 2026-10-05 (d)).
        val erasers = runCatching { localErasers.get() }.getOrNull() ?: return
        val allErased = erasers.fold(true) { allSoFar, eraser ->
            runCatching { eraser.erase() }.isSuccess && allSoFar
        }
        // Si algo volvió a fallar, la marca queda izada y el siguiente arranque
        // lo reintenta; correr de más es inofensivo (estado de instalación limpia).
        if (allErased) pendingErasure.clear()
    }
}

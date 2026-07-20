package com.auroraclima.app.core.security

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Marca durable de «borrado local pendiente» (RF-20, auditoría H-1): se iza con
 * confirmación en disco ANTES de borrar la cuenta del proveedor y se retira solo
 * cuando TODOS los erasers locales terminaron. Si el proceso muere en medio, el
 * siguiente arranque ve la marca y completa el borrado — sin ella, esos datos
 * quedarían varados para siempre (la cuenta ya no existe y no hay reintento).
 */
interface PendingErasureFlag {
    /** Persiste la marca de forma síncrona. False si no pudo GARANTIZARSE en disco. */
    suspend fun raise(): Boolean

    suspend fun isRaised(): Boolean

    /** Retira la marca. Mejor esfuerzo: llega cuando el borrado ya se completó (o se abortó). */
    suspend fun clear()
}

/**
 * Implementación en SharedPreferences con archivo PROPIO y neutro (RNF-05): no
 * puede compartir archivo con ningún dueño de datos, porque los erasers limpian
 * sus archivos completos y retirarían la marca a mitad del borrado.
 */
@Singleton
class PrefsPendingErasureFlag @Inject constructor(
    @ApplicationContext private val context: Context
) : PendingErasureFlag {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun raise(): Boolean = withContext(Dispatchers.IO) {
        // commit síncrono: sin la marca en disco no hay red de seguridad, y quien
        // llama debe abortar ANTES del punto de no retorno (mismo criterio que
        // el commit verificado de DatabaseKeyManager, D5 H-2).
        runCatching { preferences.edit().putBoolean(FLAG_KEY, true).commit() }.getOrDefault(false)
    }

    override suspend fun isRaised(): Boolean = withContext(Dispatchers.IO) {
        runCatching { preferences.getBoolean(FLAG_KEY, false) }.getOrDefault(false)
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching { preferences.edit().clear().commit() }
        }
    }

    private companion object {
        // Nombres neutros, plausibles en una app de clima (RNF-05); tras clear()
        // queda solo un XML vacío, como los demás archivos de prefs.
        const val PREFS_NAME = "app_meta"
        const val FLAG_KEY = "a0"
    }
}

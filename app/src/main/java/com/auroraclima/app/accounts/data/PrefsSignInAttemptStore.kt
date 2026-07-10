package com.auroraclima.app.accounts.data

import android.content.Context
import android.content.SharedPreferences
import com.auroraclima.app.accounts.domain.repository.SignInAttemptStore
import com.auroraclima.app.accounts.domain.repository.SignInAttempts
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Contador de intentos fallidos en SharedPreferences privadas (RF-15 criterio 3).
 * Neutro por diseño: archivo y claves con nombres plausibles en cualquier app
 * (RNF-05), solo dos números — nunca el correo ni marcas del propósito. Se
 * persiste para que cerrar la app o forzar su detención no salte el bloqueo.
 * Mejor esfuerzo (§3): un fallo de lectura/escritura degrada en silencio — el
 * proveedor mantiene además su propio límite de intentos en el servidor.
 */
@Singleton
class PrefsSignInAttemptStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SignInAttemptStore {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun read(): SignInAttempts = withContext(Dispatchers.IO) {
        runCatching {
            SignInAttempts(
                failedCount = preferences.getInt(COUNT_KEY, 0),
                lockDeadlineEpochMs = preferences.getLong(DEADLINE_KEY, 0L).takeIf { it > 0L }
            )
        }.getOrDefault(SignInAttempts())
    }

    override suspend fun write(value: SignInAttempts) {
        withContext(Dispatchers.IO) {
            runCatching {
                preferences.edit()
                    .putInt(COUNT_KEY, value.failedCount)
                    .putLong(DEADLINE_KEY, value.lockDeadlineEpochMs ?: 0L)
                    .commit()
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching {
                preferences.edit()
                    .remove(COUNT_KEY)
                    .remove(DEADLINE_KEY)
                    .commit()
            }
        }
    }

    private companion object {
        // Nombres neutros, plausibles en una app de clima (RNF-05).
        const val PREFS_NAME = "sync_meta"
        const val COUNT_KEY = "m0"
        const val DEADLINE_KEY = "m1"
    }
}

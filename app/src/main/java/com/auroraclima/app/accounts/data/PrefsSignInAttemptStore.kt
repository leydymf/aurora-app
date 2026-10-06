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
 *
 * Las claves son parámetro porque el restablecimiento (RF-46) lleva su propio
 * contador en el MISMO archivo neutro: abusar del restablecimiento no debe
 * cerrarle la entrada a la usuaria, ni al revés.
 */
@Singleton
class PrefsSignInAttemptStore(
    private val context: Context,
    private val countKey: String,
    private val deadlineKey: String
) : SignInAttemptStore {
    /** Constructor que inyecta Hilt: el contador del inicio de sesión (RF-15). */
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, COUNT_KEY, DEADLINE_KEY)

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun read(): SignInAttempts = withContext(Dispatchers.IO) {
        runCatching {
            SignInAttempts(
                failedCount = preferences.getInt(countKey, 0),
                lockDeadlineEpochMs = preferences.getLong(deadlineKey, 0L).takeIf { it > 0L }
            )
        }.getOrDefault(SignInAttempts())
    }

    override suspend fun write(value: SignInAttempts) {
        withContext(Dispatchers.IO) {
            runCatching {
                preferences.edit()
                    .putInt(countKey, value.failedCount)
                    .putLong(deadlineKey, value.lockDeadlineEpochMs ?: 0L)
                    .commit()
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching {
                preferences.edit()
                    .remove(countKey)
                    .remove(deadlineKey)
                    .commit()
            }
        }
    }

    companion object {
        // Nombres neutros, plausibles en una app de clima (RNF-05).
        private const val PREFS_NAME = "sync_meta"

        /** Contador del inicio de sesión (RF-15). */
        const val COUNT_KEY = "m0"
        const val DEADLINE_KEY = "m1"

        /** Contador propio del restablecimiento (RF-46), mismo archivo neutro. */
        const val RESET_COUNT_KEY = "m2"
        const val RESET_DEADLINE_KEY = "m3"
    }
}

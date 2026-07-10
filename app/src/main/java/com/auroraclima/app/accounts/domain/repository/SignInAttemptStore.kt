package com.auroraclima.app.accounts.domain.repository

/** Intentos fallidos acumulados y, si existe, el fin del bloqueo temporal (RF-15 criterio 3). */
data class SignInAttempts(
    val failedCount: Int = 0,
    val lockDeadlineEpochMs: Long? = null
)

/**
 * Persistencia local del contador de intentos fallidos: el bloqueo debe
 * sobrevivir a la muerte del proceso (cerrar la app no lo salta). Las
 * implementaciones no lanzan: el registro es de mejor esfuerzo (§3).
 */
interface SignInAttemptStore {
    suspend fun read(): SignInAttempts

    suspend fun write(value: SignInAttempts)

    suspend fun clear()
}

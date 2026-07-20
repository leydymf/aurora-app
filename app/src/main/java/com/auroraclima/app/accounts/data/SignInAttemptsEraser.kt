package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.domain.repository.SignInAttemptStore
import com.auroraclima.app.core.security.LocalAccountDataEraser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aporte de accounts a la eliminación total (RF-20): el contador de intentos
 * fallidos (RF-15) vuelve al estado de instalación limpia — sin la cuenta ya no
 * hay nada que ese bloqueo proteja.
 */
@Singleton
class SignInAttemptsEraser @Inject constructor(
    private val attemptStore: SignInAttemptStore
) : LocalAccountDataEraser {
    override suspend fun erase() {
        attemptStore.clear()
    }
}

package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.SignInAttemptStore
import com.auroraclima.app.accounts.domain.repository.SignInAttempts
import com.auroraclima.app.accounts.domain.security.WallClock
import kotlinx.coroutines.CompletableDeferred

/** Fakes compartidos por los tests de accounts (creación de cuenta e inicio de sesión). */

internal class FakeAuthRepository : AuthRepository {
    var nextCreateResult: CreateAccountResult = CreateAccountResult.Success
    var nextSignInResult: SignInResult = SignInResult.Success
    var gate: CompletableDeferred<Unit>? = null
    var activeSession = false
    var signInCalls = 0
        private set

    /** Correos que llegaron al proveedor; su tamaño es el conteo de llamadas. */
    val requestedEmails = mutableListOf<String>()

    val createCalls: Int get() = requestedEmails.size

    override suspend fun createAccount(email: String, password: String): CreateAccountResult {
        requestedEmails += email
        gate?.await()
        return nextCreateResult
    }

    override suspend fun signIn(email: String, password: String): SignInResult {
        signInCalls++
        gate?.await()
        return nextSignInResult
    }

    override fun hasActiveSession(): Boolean = activeSession
}

internal class FakeSignInAttemptStore : SignInAttemptStore {
    var stored = SignInAttempts()

    override suspend fun read(): SignInAttempts = stored

    override suspend fun write(value: SignInAttempts) {
        stored = value
    }

    override suspend fun clear() {
        stored = SignInAttempts()
    }
}

/** Reloj de pared simulado, movible a mano. */
internal class MutableWallClock(var nowMs: Long = 1_700_000_000_000L) : WallClock {
    override fun nowEpochMs(): Long = nowMs
}

package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.model.DeleteUserResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.accounts.domain.model.SilentSessionState
import com.auroraclima.app.accounts.domain.model.UpdatePasswordResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.accounts.domain.repository.SignInAttemptStore
import com.auroraclima.app.accounts.domain.repository.SignInAttempts
import com.auroraclima.app.accounts.domain.repository.SilentSessionOrchestrator
import com.auroraclima.app.accounts.domain.repository.StoredCredentials
import com.auroraclima.app.accounts.domain.security.WallClock
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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

    /** Credenciales que llegaron al proveedor (el silencioso debe enviar las del blob). */
    val signInRequests = mutableListOf<Pair<String, String>>()

    override suspend fun signIn(email: String, password: String): SignInResult {
        signInCalls++
        signInRequests += email to password
        gate?.await()
        return nextSignInResult
    }

    override fun hasActiveSession(): Boolean = activeSession

    var signOutCalls = 0
        private set

    override fun signOut() {
        signOutCalls++
        activeSession = false
    }

    var nextReauthenticateResult: ReauthenticateResult = ReauthenticateResult.Success
    var nextDeleteResult: DeleteUserResult = DeleteUserResult.Success
    var reauthenticateCalls = 0
        private set
    var deleteCalls = 0
        private set

    /** Bitácora compartida opcional: los tests de RF-20 verifican el ORDEN con ella. */
    var callLog: MutableList<String>? = null

    override suspend fun reauthenticate(password: String): ReauthenticateResult {
        reauthenticateCalls++
        callLog?.add("reauth")
        return nextReauthenticateResult
    }

    override suspend fun deleteCurrentUser(): DeleteUserResult {
        deleteCalls++
        callLog?.add("deleteUser")
        return nextDeleteResult
    }

    var nextUpdatePasswordResult: UpdatePasswordResult = UpdatePasswordResult.Success

    /** Contraseñas que llegaron a updatePassword; su tamaño es el conteo de llamadas. */
    val updatedPasswords = mutableListOf<String>()

    override suspend fun updatePassword(newPassword: String): UpdatePasswordResult {
        updatedPasswords += newPassword
        callLog?.add("updatePassword")
        return nextUpdatePasswordResult
    }
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

/** Orquestador del reingreso silencioso simulado (P-31 opción C). */
internal class FakeSilentSessionOrchestrator : SilentSessionOrchestrator {
    val mutableState = MutableStateFlow(SilentSessionState.Idle)
    override val state: StateFlow<SilentSessionState> = mutableState

    val mutableLoginRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val loginRequests: SharedFlow<Unit> = mutableLoginRequests

    var startCalls = 0
        private set

    override fun start() {
        startCalls++
    }
}

/** Blob de credenciales en memoria (P-31 opción C), con fallos simulables. */
internal class FakeCredentialStore : CredentialStore {
    var stored: StoredCredentials? = null
    var failNextSave = false
    var saveCalls = 0
        private set
    var eraseCalls = 0
        private set

    override fun save(email: String, password: String) {
        saveCalls++
        if (failNextSave) {
            failNextSave = false
            throw IOException()
        }
        stored = StoredCredentials(email, password)
    }

    override fun load(): StoredCredentials? = stored

    override fun exists(): Boolean = stored != null

    override fun erase() {
        eraseCalls++
        stored = null
    }
}

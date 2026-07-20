package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.model.DeleteUserResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Crea la cuenta contra Firebase Auth y traduce cada fallo del proveedor a un
 * [CreateAccountResult]: ninguna excepción cruza hacia la UI (ARQUITECTURA §3)
 * y nada se registra en logs (regla 4 de CLAUDE.md).
 */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val service: AuthService
) : AuthRepository {
    override suspend fun createAccount(email: String, password: String): CreateAccountResult =
        withContext(Dispatchers.IO) {
            try {
                service.createUser(email, password)
                CreateAccountResult.Success
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mapFailure(error)
            }
        }

    override suspend fun signIn(email: String, password: String): SignInResult = withContext(Dispatchers.IO) {
        try {
            service.signIn(email, password)
            SignInResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            mapSignInFailure(error)
        }
    }

    override fun hasActiveSession(): Boolean = service.hasActiveSession()

    override suspend fun reauthenticate(password: String): ReauthenticateResult = withContext(Dispatchers.IO) {
        try {
            service.reauthenticate(password)
            ReauthenticateResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            mapReauthenticateFailure(error)
        }
    }

    override suspend fun deleteCurrentUser(): DeleteUserResult = withContext(Dispatchers.IO) {
        try {
            service.deleteCurrentUser()
            DeleteUserResult.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (errorCode(error) == AuthServiceException.ERROR_NETWORK) {
                DeleteUserResult.NoConnection
            } else {
                DeleteUserResult.GenericFailure
            }
        }
    }

    /**
     * Mismo criterio que [mapSignInFailure]: los rechazos de credenciales
     * convergen en un solo caso indistinguible. La red caída se distingue a
     * propósito — la eliminación (RF-20) debe poder afirmar que sin conexión
     * nada se ha borrado.
     */
    private fun mapReauthenticateFailure(error: Exception): ReauthenticateResult = when (errorCode(error)) {
        ERROR_INVALID_CREDENTIAL,
        ERROR_WRONG_PASSWORD,
        ERROR_USER_NOT_FOUND,
        ERROR_USER_DISABLED,
        ERROR_INVALID_EMAIL -> ReauthenticateResult.InvalidCredentials
        AuthServiceException.ERROR_NETWORK -> ReauthenticateResult.NoConnection
        else -> ReauthenticateResult.GenericFailure
    }

    private fun errorCode(error: Exception): String? = (error as? AuthServiceException)?.errorCode

    /**
     * Todos los rechazos de credenciales convergen en el mismo resultado: el
     * error visible no dice cuál campo falló ni si el correo existe (RF-15
     * criterio 2). Lo demás (red, cuota del proveedor) es fallo genérico y no
     * cuenta como intento.
     */
    private fun mapSignInFailure(error: Exception): SignInResult {
        val code = (error as? AuthServiceException)?.errorCode
        return when (code) {
            ERROR_INVALID_CREDENTIAL,
            ERROR_WRONG_PASSWORD,
            ERROR_USER_NOT_FOUND,
            ERROR_USER_DISABLED,
            ERROR_INVALID_EMAIL -> SignInResult.InvalidCredentials
            else -> SignInResult.GenericFailure
        }
    }

    private fun mapFailure(error: Exception): CreateAccountResult {
        val code = (error as? AuthServiceException)?.errorCode
        return when (code) {
            ERROR_EMAIL_IN_USE -> CreateAccountResult.EmailAlreadyInUse
            ERROR_WEAK_PASSWORD -> CreateAccountResult.WeakPassword
            ERROR_INVALID_EMAIL -> CreateAccountResult.InvalidEmail
            // Red caída, servicio no disponible, etc.: error genérico y silencioso
            // que no distingue esta pantalla de cualquier formulario corriente (§3).
            else -> CreateAccountResult.GenericFailure
        }
    }

    companion object {
        // Códigos documentados de Firebase Auth para createUserWithEmailAndPassword.
        private const val ERROR_EMAIL_IN_USE = "ERROR_EMAIL_ALREADY_IN_USE"
        private const val ERROR_WEAK_PASSWORD = "ERROR_WEAK_PASSWORD"
        private const val ERROR_INVALID_EMAIL = "ERROR_INVALID_EMAIL"

        // Códigos documentados para signInWithEmailAndPassword. El proveedor
        // moderno agrupa correo/contraseña erróneos en ERROR_INVALID_CREDENTIAL.
        private const val ERROR_INVALID_CREDENTIAL = "ERROR_INVALID_CREDENTIAL"
        private const val ERROR_WRONG_PASSWORD = "ERROR_WRONG_PASSWORD"
        private const val ERROR_USER_NOT_FOUND = "ERROR_USER_NOT_FOUND"
        private const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"
    }
}

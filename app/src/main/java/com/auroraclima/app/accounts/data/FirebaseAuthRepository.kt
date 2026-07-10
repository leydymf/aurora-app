package com.auroraclima.app.accounts.data

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
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
    }
}

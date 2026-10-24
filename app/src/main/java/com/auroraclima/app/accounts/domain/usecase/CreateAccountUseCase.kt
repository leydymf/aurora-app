package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.model.PasswordChecks
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.core.security.SyncKeyManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Crea la cuenta validando primero en el dispositivo (RF-07): formato de correo
 * y contraseña de mínimo 8 caracteres. Solo si la entrada es válida se contacta
 * al proveedor de autenticación — así una contraseña débil se rechaza indicando
 * los requisitos sin tráfico de red innecesario (criterio 3).
 *
 * RF-39: la cuenta recién creada publica de una vez su sobre de llaves
 * ([SyncKeyManager.establish]) aprovechando la contraseña en memoria. Mejor
 * esfuerzo y silencioso (§3): si falla, el primer login lo reintenta — no hay
 * restauradores que correr porque una cuenta nueva no tiene nada en la nube.
 *
 * P-31 opción C: el mismo momento hace nacer el blob de credenciales del
 * reingreso silencioso (mismo criterio que establish: los únicos momentos con
 * la contraseña confirmada en memoria). También de mejor esfuerzo.
 *
 * v0.3.2 (hallazgo A): antes de hablar con el proveedor se descarta lo que haya
 * dejado otro registro sin terminar ([DiscardUnfinishedActivationUseCase]), para
 * que la cuenta nueva no herede nada de la anterior.
 */
class CreateAccountUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val syncKeys: SyncKeyManager,
    private val credentials: CredentialStore,
    private val discardUnfinished: DiscardUnfinishedActivationUseCase
) {
    suspend operator fun invoke(email: String, password: String): CreateAccountResult {
        val normalizedEmail = email.trim()
        if (!EMAIL_PATTERN.matches(normalizedEmail)) return CreateAccountResult.InvalidEmail
        if (!PasswordChecks.of(password).allSatisfied) return CreateAccountResult.WeakPassword
        discardUnfinished()
        val result = repository.createAccount(normalizedEmail, password)
        if (result == CreateAccountResult.Success) {
            saveCredentials(normalizedEmail, password)
            establishBackupKeys(password)
        }
        return result
    }

    private fun saveCredentials(email: String, password: String) {
        try {
            credentials.save(email, password)
        } catch (ignored: Exception) {
            // Mejor esfuerzo (§3): sin blob, el primer login manual lo crea.
        }
    }

    private suspend fun establishBackupKeys(password: String) {
        try {
            syncKeys.establish(password)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Exception) {
            // Mejor esfuerzo (§3): el primer inicio de sesión reintenta.
        }
    }

    companion object {
        // Validación mínima de forma (algo@dominio.tld); la verificación real la
        // hace el proveedor. Kotlin puro: sin android.util.Patterns (ARQUITECTURA §2).
        private val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}

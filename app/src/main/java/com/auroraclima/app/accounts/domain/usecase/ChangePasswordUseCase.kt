package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.ChangePasswordResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.model.UpdatePasswordResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.core.security.SyncKeyManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Cambio de contraseña conociendo la actual (RF-39). Orden diseñado en el ADR:
 *
 * 1. Re-autenticación con la contraseña actual (patrón de RF-20): sin red o con
 *    credencial errónea NADA se ha modificado y la UI puede afirmarlo.
 * 2. [SyncKeyManager.rewrap]: publica el sobre re-envuelto con la contraseña
 *    nueva — misma DEK y generación, los registros NO se re-cifran — esperando
 *    al servidor. Si falla, el cambio se aborta con todo intacto.
 * 3. `updatePassword` en el proveedor.
 * 4. [SyncKeyManager.establish] con la contraseña que quedó activa: adopta la
 *    revisión nueva si el cambio culminó, o auto-repara el sobre (re-publica
 *    con la actual) si el paso 3 falló. Es de mejor esfuerzo: si también falla,
 *    el siguiente login concilia — la vía rápida exige la revisión al día, así
 *    que ningún estado intermedio pasa inadvertido ni deja el respaldo
 *    ilegible en un dispositivo nuevo.
 *
 * P-31 opción C: si el cambio culmina, el blob del reingreso silencioso se
 * re-escribe con la contraseña nueva (mejor esfuerzo: si no hay blob o el
 * guardado falla, el siguiente login manual lo crea; un blob desactualizado
 * sería rechazado por el proveedor y el flujo de credencial inválida lo
 * destruye y pide login manual — degradación prevista, nunca un estado roto).
 */
class ChangePasswordUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val syncKeys: SyncKeyManager,
    private val credentials: CredentialStore
) {
    suspend operator fun invoke(currentPassword: String, newPassword: String): ChangePasswordResult {
        if (newPassword.length < CreateAccountUseCase.MIN_PASSWORD_LENGTH) {
            return ChangePasswordResult.WeakPassword
        }
        when (repository.reauthenticate(currentPassword)) {
            ReauthenticateResult.InvalidCredentials -> return ChangePasswordResult.InvalidCredentials
            ReauthenticateResult.NoConnection -> return ChangePasswordResult.NoConnection
            ReauthenticateResult.GenericFailure -> return ChangePasswordResult.GenericFailure
            ReauthenticateResult.Success -> Unit
        }
        if (syncKeys.rewrap(newPassword).isFailure) return ChangePasswordResult.GenericFailure
        val update = repository.updatePassword(newPassword)
        if (update == UpdatePasswordResult.Success) refreshStoredCredentials(newPassword)
        reconcileEnvelope(if (update == UpdatePasswordResult.Success) newPassword else currentPassword)
        return when (update) {
            UpdatePasswordResult.Success -> ChangePasswordResult.Success
            UpdatePasswordResult.WeakPassword -> ChangePasswordResult.WeakPassword
            UpdatePasswordResult.NoConnection -> ChangePasswordResult.NoConnection
            UpdatePasswordResult.GenericFailure -> ChangePasswordResult.GenericFailure
        }
    }

    /** Re-escritura del blob con la contraseña nueva (P-31 C), mejor esfuerzo. */
    private fun refreshStoredCredentials(newPassword: String) {
        try {
            credentials.load()?.let { stored -> credentials.save(stored.email, newPassword) }
        } catch (ignored: Exception) {
            // Sin blob al día no hay reingreso silencioso; el flujo de
            // credencial inválida lo destruye y el login manual lo re-crea.
        }
    }

    /** Paso 4: silencioso — el desenlace visible es el del proveedor. */
    private suspend fun reconcileEnvelope(activePassword: String) {
        try {
            syncKeys.establish(activePassword)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Exception) {
            // El siguiente inicio de sesión concilia (auto-reparación).
        }
    }
}

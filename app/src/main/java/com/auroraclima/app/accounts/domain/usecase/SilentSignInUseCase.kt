package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.SyncKeyManager
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Reingreso silencioso (P-31 opción C): confirma la sesión del proveedor con
 * las credenciales del blob local, sin participación de la usuaria. Difiere de
 * [SignInUseCase] a propósito:
 *
 * - NO toca el contador de intentos de RF-15: no es superficie de adivinación
 *   (las credenciales vienen del blob, no de un teclado) y un rechazo ya tiene
 *   consecuencia terminal — destruye el blob y fuerza el login manual.
 * - Un rechazo definitivo de credenciales (contraseña cambiada en otro
 *   dispositivo, cuenta revocada) DESTRUYE el blob: el estado obsoleto no debe
 *   reintentar contra el proveedor ni sobrevivir a la evidencia de que ya no
 *   sirve.
 * - Cualquier otro fallo (sin red, servicio caído) conserva el blob y reporta
 *   [SilentSignInOutcome.Unavailable]: se opera en local y se reintenta cuando
 *   la red regrese — nunca se pierde el acceso offline por un error transitorio.
 *
 * El éxito corre los mismos pasos de mejor esfuerzo del login manual (RF-39):
 * establecer la llave del respaldo y disparar los restauradores — así la cola
 * de sync pendiente de una sesión offline se re-sube sola.
 */
class SilentSignInUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val credentials: CredentialStore,
    private val syncKeys: SyncKeyManager,
    private val restorers: Set<@JvmSuppressWildcards AccountDataRestorer>
) {
    suspend operator fun invoke(): SilentSignInOutcome {
        // Un blob ilegible ya fue destruido por load(): degrada a login manual.
        val stored = credentials.load() ?: return SilentSignInOutcome.NoCredentials
        // El Task del SDK no se aborta con la cancelación de la corrutina: se
        // espera su desenlace bajo NonCancellable y, si la sesión interna se
        // cerró mientras el proveedor respondía (salida rápida en plena
        // confirmación), se DESHACE lo que acaba de persistir antes de propagar
        // la cancelación — sin esto, el signOut de AuthSessionCleaner quedaría
        // burlado por un login que culmina después de la limpieza (H-3,
        // auditoría P-31 C A-1).
        val result = withContext(NonCancellable) { repository.signIn(stored.email, stored.password) }
        if (!coroutineContext.isActive) {
            when (result) {
                SignInResult.Success -> runCatching { repository.signOut() }
                SignInResult.InvalidCredentials -> credentials.erase()
                SignInResult.GenericFailure -> Unit
            }
            throw CancellationException()
        }
        return when (result) {
            SignInResult.Success -> {
                establishBackupKeys(stored.password)
                SilentSignInOutcome.Success
            }
            SignInResult.InvalidCredentials -> {
                credentials.erase()
                SilentSignInOutcome.Rejected
            }
            SignInResult.GenericFailure -> SilentSignInOutcome.Unavailable
        }
    }

    /** Mismo paso de mejor esfuerzo que el login manual (ver [SignInUseCase]). */
    private suspend fun establishBackupKeys(password: String) {
        try {
            if (syncKeys.establish(password).isSuccess) {
                restorers.forEach { restorer -> restorer.restore() }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Exception) {
            // Mejor esfuerzo (§3): el siguiente inicio de sesión reintenta.
        }
    }
}

sealed interface SilentSignInOutcome {
    /** Sesión del proveedor confirmada sin participación de la usuaria. */
    data object Success : SilentSignInOutcome

    /** Credenciales rechazadas: blob destruido, corresponde el login manual. */
    data object Rejected : SilentSignInOutcome

    /** Sin blob legible: corresponde el login manual (sin nada que destruir). */
    data object NoCredentials : SilentSignInOutcome

    /** Fallo transitorio (red, servicio): blob conservado, se opera en local. */
    data object Unavailable : SilentSignInOutcome
}

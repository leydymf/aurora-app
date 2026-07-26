package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.SignInResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.SignInAttemptStore
import com.auroraclima.app.accounts.domain.repository.SignInAttempts
import com.auroraclima.app.accounts.domain.security.WallClock
import com.auroraclima.app.core.security.AccountDataRestorer
import com.auroraclima.app.core.security.SyncKeyManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Inicio de sesión con bloqueo temporal (RF-15). El contador de intentos es
 * local y neutro: vive en [SignInAttemptStore] sin registrar correo ni marcas
 * que delaten el propósito. Reglas:
 *
 * - Tras [MAX_ATTEMPTS] rechazos consecutivos de credenciales, bloqueo de
 *   [LOCK_DURATION_MS] (5 min, decisión F3/F4 del entregable A2-6: «30 s en la
 *   demo; 5 min reales») — criterio 3.
 * - Solo los rechazos de credenciales cuentan: un fallo de red o del servicio
 *   no castiga a la usuaria.
 * - El éxito limpia el contador.
 * - Durante el bloqueo ni se consulta el proveedor: sin tráfico que delate el
 *   reintento.
 *
 * RF-39: el login es el único momento con la contraseña en memoria, así que el
 * éxito establece la llave del respaldo ([SyncKeyManager.establish] — en un
 * dispositivo nuevo eso desenvuelve la DEK de la nube) y después dispara los
 * restauradores de cada feature. Ambos pasos son de mejor esfuerzo y
 * silenciosos (§3): su fallo jamás degrada el acceso concedido ni asoma un
 * error revelador; el siguiente inicio de sesión reintenta.
 */
class SignInUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val attemptStore: SignInAttemptStore,
    private val clock: WallClock,
    private val syncKeys: SyncKeyManager,
    private val restorers: Set<@JvmSuppressWildcards AccountDataRestorer>
) {
    /** Milisegundos restantes del bloqueo vigente, o 0 si no hay bloqueo. */
    suspend fun currentLockMillis(): Long {
        val deadline = attemptStore.read().lockDeadlineEpochMs ?: return 0
        return (deadline - clock.nowEpochMs()).coerceAtLeast(0)
    }

    suspend operator fun invoke(email: String, password: String): SignInOutcome {
        currentLockMillis().takeIf { it > 0 }?.let { return SignInOutcome.Locked(it) }
        return when (repository.signIn(email, password)) {
            SignInResult.Success -> {
                attemptStore.clear()
                establishBackupKeys(password)
                SignInOutcome.Success
            }
            SignInResult.InvalidCredentials -> registerRejection()
            SignInResult.GenericFailure -> SignInOutcome.Failure
        }
    }

    /** Llave del respaldo y restauración (RF-39), silenciosas: nada revierte el acceso. */
    private suspend fun establishBackupKeys(password: String) {
        try {
            // Los restauradores solo corren con la DEK establecida: sin ella no
            // habría cómo abrir lo descargado y el intento sería ruido.
            if (syncKeys.establish(password).isSuccess) {
                restorers.forEach { restorer -> restorer.restore() }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Exception) {
            // Mejor esfuerzo (§3): el siguiente inicio de sesión reintenta.
        }
    }

    private suspend fun registerRejection(): SignInOutcome {
        val failed = attemptStore.read().failedCount + 1
        return if (failed >= MAX_ATTEMPTS) {
            // El contador vuelve a cero: al expirar el bloqueo empieza una cuenta nueva.
            attemptStore.write(SignInAttempts(0, clock.nowEpochMs() + LOCK_DURATION_MS))
            SignInOutcome.Locked(LOCK_DURATION_MS)
        } else {
            attemptStore.write(SignInAttempts(failed, null))
            SignInOutcome.RejectedCredentials
        }
    }

    companion object {
        /** RF-15 criterio 3: bloqueo tras 5 intentos fallidos consecutivos. */
        const val MAX_ATTEMPTS = 5

        /** Duración del bloqueo (mockup alta-fidelidad-13/15: pausa de 5 minutos). */
        const val LOCK_DURATION_MS = 5 * 60 * 1000L
    }
}

sealed interface SignInOutcome {
    /** Credenciales correctas: acceso a la pantalla principal (criterio 1). */
    data object Success : SignInOutcome

    /** Credenciales rechazadas: error genérico sin señalar campo (criterio 2). */
    data object RejectedCredentials : SignInOutcome

    /** Bloqueo temporal vigente (criterio 3). */
    data class Locked(val remainingMillis: Long) : SignInOutcome

    /** Fallo ajeno a las credenciales (red, servicio): mensaje genérico, sin castigo. */
    data object Failure : SignInOutcome
}

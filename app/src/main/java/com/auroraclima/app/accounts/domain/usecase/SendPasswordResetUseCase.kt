package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.PasswordResetResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import com.auroraclima.app.accounts.domain.repository.SignInAttemptStore
import com.auroraclima.app.accounts.domain.repository.SignInAttempts
import com.auroraclima.app.accounts.domain.security.WallClock
import javax.inject.Inject
import javax.inject.Named

/**
 * Pide el correo de restablecimiento (RF-46, refinamiento de RF-15).
 *
 * Dos decisiones que hacen el grueso del requisito:
 *
 * - **El desenlace es el mismo exista o no la cuenta.** El repositorio ya
 *   convierte «correo no encontrado» y «correo mal formado» en el mismo
 *   resultado; aquí no se vuelve a mirar. Sin eso, la pantalla serviría para
 *   averiguar si una mujer tiene cuenta en Aurora, que es justo lo que
 *   intentaría quien le revisa el teléfono.
 * - **Pausa temporal con el mismo límite del inicio de sesión** (5 intentos,
 *   5 minutos). Sin ella, el formulario sería una forma cómoda de inundar de
 *   correos la bandeja de una mujer —un correo repetido de Aurora en su
 *   bandeja es un rastro fuera del camuflaje— y de gastar la cuota del
 *   proveedor. El contador es PROPIO (`SignInAttemptStore` con claves aparte):
 *   abusar del restablecimiento no debe cerrarle la entrada a la usuaria, ni
 *   al revés.
 *
 * Un fallo genérico o la falta de red NO cuentan como intento: el límite es
 * para el uso repetido, no para castigar una red mala (mismo criterio que
 * `SignInUseCase`).
 */
class SendPasswordResetUseCase @Inject constructor(
    private val repository: AuthRepository,
    @Named(PASSWORD_RESET_ATTEMPTS) private val attemptStore: SignInAttemptStore,
    private val clock: WallClock
) {
    /** Milisegundos restantes de la pausa vigente, o 0 si no hay. */
    suspend fun currentLockMillis(): Long {
        val deadline = attemptStore.read().lockDeadlineEpochMs ?: return 0
        return (deadline - clock.nowEpochMs()).coerceAtLeast(0)
    }

    suspend operator fun invoke(email: String): PasswordResetResult {
        currentLockMillis().takeIf { it > 0 }?.let { return PasswordResetResult.Locked(it) }
        return when (val outcome = repository.sendPasswordReset(email.trim())) {
            PasswordResetResult.Requested -> registerRequest()
            else -> outcome
        }
    }

    /**
     * Toda petición entregada cuenta, haya cuenta o no: el contador no puede
     * depender de algo que la usuaria no ve, o volvería a ser un oráculo.
     */
    private suspend fun registerRequest(): PasswordResetResult {
        val sent = attemptStore.read().failedCount + 1
        if (sent >= SignInUseCase.MAX_ATTEMPTS) {
            // El contador vuelve a cero: al expirar la pausa empieza de nuevo.
            attemptStore.write(SignInAttempts(0, clock.nowEpochMs() + SignInUseCase.LOCK_DURATION_MS))
        } else {
            attemptStore.write(SignInAttempts(sent, null))
        }
        // Lo que ve la usuaria es siempre lo mismo, aunque esta haya sido la última.
        return PasswordResetResult.Requested
    }

    companion object {
        /** Califica el almacén de intentos propio del restablecimiento. */
        const val PASSWORD_RESET_ATTEMPTS = "password_reset_attempts"
    }
}

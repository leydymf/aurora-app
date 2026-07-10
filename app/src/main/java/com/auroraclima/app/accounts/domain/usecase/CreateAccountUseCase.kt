package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.repository.AuthRepository
import javax.inject.Inject

/**
 * Crea la cuenta validando primero en el dispositivo (RF-07): formato de correo
 * y contraseña de mínimo 8 caracteres. Solo si la entrada es válida se contacta
 * al proveedor de autenticación — así una contraseña débil se rechaza indicando
 * los requisitos sin tráfico de red innecesario (criterio 3).
 */
class CreateAccountUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(email: String, password: String): CreateAccountResult {
        val normalizedEmail = email.trim()
        if (!EMAIL_PATTERN.matches(normalizedEmail)) return CreateAccountResult.InvalidEmail
        if (password.length < MIN_PASSWORD_LENGTH) return CreateAccountResult.WeakPassword
        return repository.createAccount(normalizedEmail, password)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 8

        // Validación mínima de forma (algo@dominio.tld); la verificación real la
        // hace el proveedor. Kotlin puro: sin android.util.Patterns (ARQUITECTURA §2).
        private val EMAIL_PATTERN = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}

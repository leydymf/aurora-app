package com.auroraclima.app.accounts.domain.repository

import com.auroraclima.app.accounts.domain.model.CreateAccountResult

/** Autenticación de la cuenta de la usuaria (RF-07). */
interface AuthRepository {
    /** Crea la cuenta con correo y contraseña. Nunca lanza: todo fallo es un [CreateAccountResult]. */
    suspend fun createAccount(email: String, password: String): CreateAccountResult
}

package com.auroraclima.app.accounts.domain.repository

import com.auroraclima.app.accounts.domain.model.CreateAccountResult
import com.auroraclima.app.accounts.domain.model.DeleteUserResult
import com.auroraclima.app.accounts.domain.model.ReauthenticateResult
import com.auroraclima.app.accounts.domain.model.SignInResult

/** Autenticación de la cuenta de la usuaria (RF-07, RF-15, RF-20). */
interface AuthRepository {
    /** Crea la cuenta con correo y contraseña. Nunca lanza: todo fallo es un [CreateAccountResult]. */
    suspend fun createAccount(email: String, password: String): CreateAccountResult

    /** Inicia sesión. Nunca lanza: todo fallo es un [SignInResult] (RF-15). */
    suspend fun signIn(email: String, password: String): SignInResult

    /** True si el proveedor conserva una sesión iniciada (RF-12 criterio 1). */
    fun hasActiveSession(): Boolean

    /** Revalida la contraseña de la sesión vigente. Nunca lanza (RF-20 paso 1). */
    suspend fun reauthenticate(password: String): ReauthenticateResult

    /** Elimina la cuenta del proveedor. Nunca lanza (RF-20 criterio 3). */
    suspend fun deleteCurrentUser(): DeleteUserResult
}

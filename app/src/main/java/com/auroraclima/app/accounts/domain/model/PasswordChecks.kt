package com.auroraclima.app.accounts.domain.model

/** Reglas de la contraseña de la cuenta (RF-07, RF-39): cada una alimenta un renglón en vivo y a los casos de uso. */
data class PasswordChecks(val hasMinLength: Boolean) {
    val allSatisfied: Boolean get() = hasMinLength

    companion object {
        const val MIN_LENGTH = 8

        fun of(password: String): PasswordChecks = PasswordChecks(hasMinLength = password.length >= MIN_LENGTH)
    }
}

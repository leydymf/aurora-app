package com.auroraclima.app.accounts.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas visibles de la contraseña de la cuenta (RF-07, RF-39). */
class PasswordChecksTest {
    @Test
    fun `una contrasena vacia no cumple ninguna regla`() {
        val checks = PasswordChecks.of("")

        assertFalse(checks.hasMinLength)
        assertFalse(checks.allSatisfied)
    }

    @Test
    fun `la longitud minima se cumple justo en el limite`() {
        assertFalse(PasswordChecks.of("a".repeat(PasswordChecks.MIN_LENGTH - 1)).hasMinLength)
        assertTrue(PasswordChecks.of("a".repeat(PasswordChecks.MIN_LENGTH)).hasMinLength)
        assertTrue(PasswordChecks.of("a".repeat(PasswordChecks.MIN_LENGTH + 20)).allSatisfied)
    }

    @Test
    fun `los espacios cuentan como caracteres`() {
        assertTrue(PasswordChecks.of(" ".repeat(PasswordChecks.MIN_LENGTH)).hasMinLength)
    }
}

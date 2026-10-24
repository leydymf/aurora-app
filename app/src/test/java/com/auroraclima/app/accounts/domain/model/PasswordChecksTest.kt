package com.auroraclima.app.accounts.domain.model

import java.io.File
import org.junit.Assert.assertEquals
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

    @Test
    fun `el renglon de la regla dice el mismo minimo que exige el codigo`() {
        val strings = requireNotNull(
            sequenceOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml"))
                .firstOrNull { it.exists() }
        ) { "No se encontró strings.xml" }
        val rule = Regex("""<string name="account_password_rule_length">([^<]*)</string>""")
            .find(strings.readText())?.groupValues?.get(1)

        assertEquals("Mínimo ${PasswordChecks.MIN_LENGTH} caracteres", rule)
    }
}

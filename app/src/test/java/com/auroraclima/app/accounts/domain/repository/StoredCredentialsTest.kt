package com.auroraclima.app.accounts.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * El par descifrado nunca se imprime: ni el correo ni la contraseña pueden
 * aparecer en un `toString` (auditoría 2026-10-05 M-1).
 */
class StoredCredentialsTest {
    @Test
    fun `toString no expone correo ni contraseña`() {
        val credentials = StoredCredentials(email = "ana@example.com", password = "Clave-Secreta-123")

        val printed = credentials.toString()

        assertFalse(printed.contains("ana@example.com"))
        assertFalse(printed.contains("Clave-Secreta-123"))
        assertFalse(printed.contains("password="))
    }

    @Test
    fun `sigue comparando por valor`() {
        assertEquals(
            StoredCredentials("ana@example.com", "Clave-Secreta-123"),
            StoredCredentials("ana@example.com", "Clave-Secreta-123")
        )
    }
}

package com.auroraclima.app.accounts.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthSessionCleanerTest {
    @Test
    fun `la limpieza de sesion cierra la sesion del proveedor`() {
        // Auditoría D3 H-3: la sesión persistida del SDK no sobrevive al cierre
        val service = FakeAuthService()
        val cleaner = AuthSessionCleaner(service)

        cleaner.onSessionCleanup()

        assertEquals(1, service.signOutCalls)
    }

    @Test
    fun `un fallo del proveedor no cruza fuera del cleaner`() {
        // Contrato de SensitiveStateCleaner: jamás lanza
        val service = FakeAuthService(failOnSignOut = true)
        val cleaner = AuthSessionCleaner(service)

        cleaner.onSessionCleanup()

        assertEquals(1, service.signOutCalls)
    }

    private class FakeAuthService(
        private val failOnSignOut: Boolean = false
    ) : AuthService {
        var signOutCalls = 0
            private set

        override suspend fun createUser(email: String, password: String) = Unit

        override suspend fun signIn(email: String, password: String) = Unit

        override fun hasActiveSession(): Boolean = false

        override fun signOut() {
            signOutCalls++
            if (failOnSignOut) error("provider failure")
        }

        override suspend fun reauthenticate(password: String) = Unit

        override suspend fun deleteCurrentUser() = Unit

        override suspend fun updatePassword(newPassword: String) = Unit
    }
}

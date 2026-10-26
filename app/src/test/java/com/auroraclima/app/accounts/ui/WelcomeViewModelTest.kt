package com.auroraclima.app.accounts.ui

import com.auroraclima.app.accounts.domain.usecase.DiscardUnfinishedActivationUseCase
import com.auroraclima.app.accounts.domain.usecase.FakeAuthRepository
import com.auroraclima.app.accounts.domain.usecase.FakeCredentialStore
import com.auroraclima.app.accounts.domain.usecase.NoCloudReconciliation
import com.auroraclima.app.accounts.domain.usecase.RecordingLocalEraser
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.security.FakeAccessCodePresence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val credentials = FakeCredentialStore()
    private val callLog = mutableListOf<String>()

    private fun discard(codeExists: Boolean) = DiscardUnfinishedActivationUseCase(
        FakeAccessCodePresence(codeExists = codeExists),
        repository,
        credentials,
        NoCloudReconciliation,
        setOf(RecordingLocalEraser(callLog, "datos-de-A"))
    )

    @Test
    fun `abrir la bienvenida descarta los restos de un registro sin terminar`() = runTest {
        // Hallazgo A (v0.3.2): la bienvenida es la entrada del flujo de activación.
        repository.activeSession = true
        credentials.save("cuenta-a@correo.co", "clave-cuenta-a")

        WelcomeViewModel(discard(codeExists = false))
        runCurrent()

        assertNull(credentials.stored)
        assertFalse(repository.activeSession)
        assertEquals(listOf("datos-de-A"), callLog.filter { it == "datos-de-A" })
    }

    @Test
    fun `con el codigo de acceso ya creado la bienvenida no borra nada`() = runTest {
        // Una activación terminada no es "sin terminar": el descarte no la toca.
        repository.activeSession = true
        credentials.save("cuenta-a@correo.co", "clave-cuenta-a")

        WelcomeViewModel(discard(codeExists = true))
        runCurrent()

        assertEquals("cuenta-a@correo.co", credentials.stored?.email)
        assertEquals(emptyList<String>(), callLog)
    }
}

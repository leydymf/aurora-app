package com.auroraclima.app.accounts.ui

import com.auroraclima.app.accounts.domain.usecase.FakeCredentialStore
import com.auroraclima.app.accounts.domain.usecase.SignOutUseCase
import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Cierre de sesión explícito (RF-19 con P-31 opción C): el blob se destruye y
 * SOLO DESPUÉS se emite el evento que dispara la rutina única de cierre.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignOutViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val credentials = FakeCredentialStore()
    private val viewModel = SignOutViewModel(SignOutUseCase(credentials))

    @Test
    fun `el cierre explicito destruye el blob y luego avisa`() = runTest {
        credentials.save("usuaria@correo.co", "clave-segura-1")
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.doneEvents.toList(events)
        }

        viewModel.onSignOut()
        advanceUntilIdle()

        assertNull(credentials.stored)
        assertEquals(1, events.size)
    }

    @Test
    fun `sin blob el cierre sigue avisando`() = runTest {
        // erase() es idempotente: RF-19 funciona igual si el blob nunca nació.
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.doneEvents.toList(events)
        }

        viewModel.onSignOut()
        advanceUntilIdle()

        assertEquals(1, events.size)
    }
}

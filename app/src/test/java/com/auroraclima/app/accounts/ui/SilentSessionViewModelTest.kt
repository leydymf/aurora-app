package com.auroraclima.app.accounts.ui

import com.auroraclima.app.accounts.domain.model.SilentSessionState
import com.auroraclima.app.accounts.domain.usecase.FakeSilentSessionOrchestrator
import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Puente de observación del reingreso silencioso (P-31 opción C). */
@OptIn(ExperimentalCoroutinesApi::class)
class SilentSessionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val orchestrator = FakeSilentSessionOrchestrator()

    @Test
    fun `el chip solo se enciende operando en local`() = runTest {
        val viewModel = SilentSessionViewModel(orchestrator)
        val observed = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isOffline.collect { observed += it }
        }
        runCurrent()
        assertFalse(observed.last())

        orchestrator.mutableState.value = SilentSessionState.Offline
        runCurrent()
        assertTrue(observed.last())

        // La confirmación culmina (la red volvió): el chip se apaga solo.
        orchestrator.mutableState.value = SilentSessionState.SignedIn
        runCurrent()
        assertFalse(observed.last())
    }

    @Test
    fun `las peticiones de login pasan tal cual`() = runTest {
        val viewModel = SilentSessionViewModel(orchestrator)
        val requests = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.loginRequests.collect { requests += it }
        }
        runCurrent()

        orchestrator.mutableLoginRequests.tryEmit(Unit)
        runCurrent()

        assertEquals(1, requests.size)
    }
}

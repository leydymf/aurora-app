package com.auroraclima.app.access.ui

import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntroNoteViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `con el aviso pendiente el panel lo muestra`() = runTest {
        val viewModel = IntroNoteViewModel(FakeIntroNoteRepository(pending = true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isVisible)
    }

    @Test
    fun `sin aviso pendiente el panel no muestra nada`() = runTest {
        // Quien vio el tutorial entero, o ya vio el aviso, no lo ve.
        val viewModel = IntroNoteViewModel(FakeIntroNoteRepository(pending = false))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isVisible)
    }

    @Test
    fun `mostrar el aviso lo consume y una entrada nueva del panel ya no lo ve`() = runTest {
        val repository = FakeIntroNoteRepository(pending = true)
        val first = IntroNoteViewModel(repository)
        advanceUntilIdle()
        assertTrue(first.uiState.value.isVisible)

        // Sin cerrarlo: sale con la nube y vuelve a entrar (ViewModel nuevo).
        val second = IntroNoteViewModel(repository)
        advanceUntilIdle()

        assertFalse(second.uiState.value.isVisible)
    }

    @Test
    fun `un fallo al leer equivale a no pendiente`() = runTest {
        val viewModel = IntroNoteViewModel(FakeIntroNoteRepository(pending = true).apply { failReads = true })
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isVisible)
    }

    @Test
    fun `entendido cierra el aviso`() = runTest {
        val viewModel = IntroNoteViewModel(FakeIntroNoteRepository(pending = true))
        advanceUntilIdle()

        viewModel.onDismiss()

        assertFalse(viewModel.uiState.value.isVisible)
    }

    @Test
    fun `ver el tutorial cierra el aviso y emite la navegacion`() = runTest {
        val viewModel = IntroNoteViewModel(FakeIntroNoteRepository(pending = true))
        advanceUntilIdle()
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.openTutorialEvents.collect { events += it }
        }

        viewModel.onOpenTutorial()

        assertFalse(viewModel.uiState.value.isVisible)
        assertEquals(1, events.size)
    }
}

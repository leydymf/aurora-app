package com.auroraclima.app.access.ui

import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Recorrido por Inicio (v0.3.3): aparece una sola vez por sí solo, en el orden pedido,
 * se puede saltar y se vuelve a ver desde Ajustes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeTourViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `con la marca pendiente empieza en el primer paso`() = runTest {
        val viewModel = HomeTourViewModel(FakeHomeTourRepository(pending = true))
        advanceUntilIdle()

        assertEquals(HomeTourStep.START, viewModel.uiState.value.step)
    }

    @Test
    fun `sin marca no aparece`() = runTest {
        val viewModel = HomeTourViewModel(FakeHomeTourRepository(pending = false))
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `aparece una sola vez aunque la sesion se cierre a mitad`() = runTest {
        val repository = FakeHomeTourRepository(pending = true)
        val first = HomeTourViewModel(repository)
        advanceUntilIdle()
        first.onNext()
        assertEquals(HomeTourStep.SAVED, first.uiState.value.step)

        // La nube saca el panel de la pila y su ViewModel muere con él; la siguiente
        // entrada crea uno nuevo: no repite desde cero ni retoma.
        val second = HomeTourViewModel(repository)
        advanceUntilIdle()

        assertNull(second.uiState.value.step)
        assertFalse(repository.pending)
    }

    @Test
    fun `recorre los siete pasos en el orden pedido y termina con el ultimo`() = runTest {
        val viewModel = HomeTourViewModel(FakeHomeTourRepository(pending = true))
        advanceUntilIdle()

        val visited = mutableListOf<HomeTourStep>()
        while (true) {
            val step = viewModel.uiState.value.step ?: break
            visited += step
            viewModel.onNext()
        }

        assertEquals(
            listOf(
                HomeTourStep.START,
                HomeTourStep.SAVED,
                HomeTourStep.GUIDE,
                HomeTourStep.JOURNAL,
                HomeTourStep.SETTINGS,
                HomeTourStep.SHORTCUTS,
                HomeTourStep.CLOUD
            ),
            visited
        )
        assertEquals(7, HomeTourStep.count)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), visited.map { it.number })
        assertTrue("solo el último dice «Entendido»", visited.single { it.isLast } == HomeTourStep.CLOUD)
    }

    @Test
    fun `saltar lo cierra desde cualquier paso`() = runTest {
        val viewModel = HomeTourViewModel(FakeHomeTourRepository(pending = true))
        advanceUntilIdle()
        viewModel.onNext()
        viewModel.onNext()

        viewModel.onSkip()

        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `volver a verlo empieza desde el primer paso sin tocar la marca`() = runTest {
        val repository = FakeHomeTourRepository(pending = false)
        val viewModel = HomeTourViewModel(repository)
        advanceUntilIdle()

        viewModel.onReplay()

        assertEquals(HomeTourStep.START, viewModel.uiState.value.step)
        assertEquals(0, repository.markCalls)
    }

    @Test
    fun `volver a verlo despues de terminarlo lo muestra otra vez`() = runTest {
        val viewModel = HomeTourViewModel(FakeHomeTourRepository(pending = true))
        advanceUntilIdle()
        repeat(HomeTourStep.count) { viewModel.onNext() }
        assertNull(viewModel.uiState.value.step)

        viewModel.onReplay()

        assertEquals(HomeTourStep.START, viewModel.uiState.value.step)
    }

    @Test
    fun `un fallo al leer la marca equivale a no pendiente`() = runTest {
        val viewModel = HomeTourViewModel(FakeHomeTourRepository(pending = true).apply { failReads = true })
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.step)
    }
}

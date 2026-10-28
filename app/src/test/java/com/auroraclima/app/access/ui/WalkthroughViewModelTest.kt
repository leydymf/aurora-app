package com.auroraclima.app.access.ui

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeHasher
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeRepository
import com.auroraclima.app.access.domain.usecase.FakeWalkthroughRepository
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.access.domain.usecase.seed
import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * La guía de Aurora (v0.3.4): sale una sola vez por sí sola, recorre sus pasos en el
 * orden pedido y cada control y el gesto atrás hacen lo descrito.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WalkthroughViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hasher = FakeSecretCodeHasher()
    private val codes = FakeSecretCodeRepository()

    private fun TestScope.viewModel(
        repository: FakeWalkthroughRepository = FakeWalkthroughRepository(WalkthroughMark.FROM_START)
    ): WalkthroughViewModel =
        WalkthroughViewModel(repository, ValidateSecretCodeUseCase(codes, hasher)).also { advanceUntilIdle() }

    private suspend fun seedUserCode(code: String = "amanecer26") {
        codes.seed(CodeSlot.USER, code, hasher)
    }

    private val step get() = WalkthroughStep.entries

    @Test
    fun `el orden de los pasos es el pedido`() {
        assertEquals(
            listOf(
                WalkthroughStep.OVERVIEW,
                WalkthroughStep.TYPE_IN,
                WalkthroughStep.GUIDE,
                WalkthroughStep.JOURNAL,
                WalkthroughStep.START,
                WalkthroughStep.SAVED,
                WalkthroughStep.SHORTCUTS,
                WalkthroughStep.SETTINGS,
                WalkthroughStep.CLOUD,
                WalkthroughStep.DONE
            ),
            step
        )
        assertEquals(6, WalkthroughStep.spaceCount)
        assertEquals((1..6).toList(), step.filter { it.part == 2 }.map { it.numberInSpace })
    }

    @Test
    fun `la marca de primera vez abre Conoce Aurora y se consume una vez`() = runTest {
        val repository = FakeWalkthroughRepository(WalkthroughMark.FROM_START)
        val viewModel = viewModel(repository)

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertEquals(1, repository.consumeCalls)
        assertNull(repository.stored)
        assertNull(viewModel(repository).uiState.value.step)
    }

    @Test
    fun `la marca de cierre abre solo Listo con el texto de la salida`() = runTest {
        val viewModel = viewModel(FakeWalkthroughRepository(WalkthroughMark.TO_FINISH))

        assertEquals(WalkthroughStep.DONE, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.reachedEarly)
    }

    @Test
    fun `sin marca o con un fallo de lectura no aparece`() = runTest {
        assertNull(viewModel(FakeWalkthroughRepository(null)).uiState.value.step)
        val failing = FakeWalkthroughRepository(WalkthroughMark.FROM_START).apply { failReads = true }
        assertNull(viewModel(failing).uiState.value.step)
    }

    @Test
    fun `Empezar y Siguiente recorren todo hasta la nube`() = runTest {
        val viewModel = viewModel()
        viewModel.onStart()
        val visited = mutableListOf(viewModel.uiState.value.step)
        repeat(10) {
            viewModel.onNext()
            visited += viewModel.uiState.value.step
        }

        assertEquals(step.subList(1, 9), visited.distinct())
        // En la parte 3 no hay «Siguiente»: la acción es tocar la nube.
        assertEquals(WalkthroughStep.CLOUD, viewModel.uiState.value.step)
        assertNull(WalkthroughStep.CLOUD.next)
    }

    @Test
    fun `Atras vuelve un paso y no existe en la primera tarjeta`() = runTest {
        val viewModel = viewModel()
        viewModel.onStart()
        assertNull(WalkthroughStep.TYPE_IN.previous)
        viewModel.onBack()
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)

        viewModel.onNext()
        viewModel.onNext()
        assertEquals(WalkthroughStep.JOURNAL, viewModel.uiState.value.step)
        viewModel.onBack()
        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
        viewModel.onBack()
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
    }

    @Test
    fun `Ahora no lleva a Inicio sin guia`() = runTest {
        val viewModel = viewModel()
        viewModel.onNotNow()

        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `Saltar guia desde cualquier tarjeta lleva a Listo con Para recordar`() = runTest {
        step.filter { it.isCard }.forEach { card ->
            val viewModel = viewModel()
            viewModel.onStart()
            while (viewModel.uiState.value.step != card) viewModel.onNext()

            viewModel.onFinishEarly()

            assertEquals("desde $card", WalkthroughStep.DONE, viewModel.uiState.value.step)
            assertTrue(viewModel.uiState.value.reachedEarly)
        }
    }

    @Test
    fun `Saltar guia no hace nada fuera de las tarjetas`() = runTest {
        val viewModel = viewModel()
        viewModel.onFinishEarly()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
    }

    @Test
    fun `Ir a mi espacio cierra la guia`() = runTest {
        val viewModel = viewModel(FakeWalkthroughRepository(WalkthroughMark.TO_FINISH))
        viewModel.onFinish()

        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `el gesto atras equivale a Atras y en Conoce Aurora a Ahora no`() = runTest {
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onNext()
        viewModel.onNext()
        viewModel.onBackGesture()
        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
        viewModel.onBackGesture()
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
        // En la primera tarjeta no hay «Atrás»: el gesto vuelve a Conoce Aurora.
        viewModel.onBackGesture()
        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        viewModel.onBackGesture()
        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `el gesto atras en Listo lleva a mi espacio`() = runTest {
        val viewModel = viewModel(FakeWalkthroughRepository(WalkthroughMark.TO_FINISH))
        viewModel.onBackGesture()

        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun `volver a verla desde Ajustes empieza en Conoce Aurora sin tocar la marca`() = runTest {
        val repository = FakeWalkthroughRepository(null)
        val viewModel = viewModel(repository)
        viewModel.onReplay()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.reachedEarly)
        assertEquals(0, repository.markCalls)
    }

    @Test
    fun `la practica solo se logra con el codigo de acceso real`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()

        viewModel.onPracticeInputChange("otracosa1")
        advanceUntilIdle()
        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)

        viewModel.onPracticeInputChange("amanecer26")
        runCurrent()
        assertEquals(PracticeStatus.MATCHED, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `al coincidir confirma un momento y pasa sola a Inicio`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onPracticeInputChange("amanecer26")
        runCurrent()

        advanceTimeBy(WalkthroughViewModel.CONFIRM_MILLIS - 1)
        runCurrent()
        assertEquals("la confirmación sigue a la vista", WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)
        advanceTimeBy(2)
        runCurrent()

        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
        // Lo tecleado es el código real: no sale de la parte 1.
        assertEquals("", viewModel.uiState.value.practiceInput)
    }

    @Test
    fun `si se sale de la parte 1 durante la confirmacion no avanza sola`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onPracticeInputChange("amanecer26")
        runCurrent()

        viewModel.onBackGesture()
        advanceUntilIdle()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertEquals("", viewModel.uiState.value.practiceInput)
    }

    @Test
    fun `Siguiente en la parte 1 avanza sin escribir el codigo`() = runTest {
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onNext()

        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
    }

    @Test
    fun `la barra no escucha fuera de la parte 1`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onPracticeInputChange("amanecer26")
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.practiceInput)
        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `repetir desde Ajustes limpia la practica`() = runTest {
        seedUserCode()
        val viewModel = viewModel()
        viewModel.onStart()
        viewModel.onPracticeInputChange("amanecer26")
        runCurrent()

        viewModel.onReplay()
        advanceUntilIdle()

        assertEquals(WalkthroughStep.OVERVIEW, viewModel.uiState.value.step)
        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
        assertEquals("", viewModel.uiState.value.practiceInput)
    }
}

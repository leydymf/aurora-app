package com.auroraclima.app.access.ui

import com.auroraclima.app.access.domain.model.CodeSlot
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeHasher
import com.auroraclima.app.access.domain.usecase.FakeSecretCodeRepository
import com.auroraclima.app.access.domain.usecase.ValidateSecretCodeUseCase
import com.auroraclima.app.access.domain.usecase.seed
import com.auroraclima.app.core.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TutorialViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hasher = FakeSecretCodeHasher()
    private val repository = FakeSecretCodeRepository()
    private val viewModel = TutorialViewModel(ValidateSecretCodeUseCase(repository, hasher))

    private suspend fun seedUserCode(code: String = "amanecer26") {
        repository.seed(CodeSlot.USER, code, hasher)
    }

    @Test
    fun `el pager avanza y retrocede por las paginas sin desbordarse`() = runTest {
        // RF-10 criterio 1: ingreso → salida → cierre automático → práctica
        assertEquals(TutorialStep.ENTER, viewModel.uiState.value.step)

        viewModel.onBack() // en la primera página no hay página anterior
        assertEquals(TutorialStep.ENTER, viewModel.uiState.value.step)

        viewModel.onNext()
        assertEquals(TutorialStep.QUICK_EXIT, viewModel.uiState.value.step)
        viewModel.onNext()
        assertEquals(TutorialStep.AUTO_CLOSE, viewModel.uiState.value.step)
        viewModel.onNext()
        assertEquals(TutorialStep.PRACTICE, viewModel.uiState.value.step)

        viewModel.onNext() // la práctica es la última: no hay más páginas
        assertEquals(TutorialStep.PRACTICE, viewModel.uiState.value.step)

        viewModel.onBack()
        assertEquals(TutorialStep.AUTO_CLOSE, viewModel.uiState.value.step)
    }

    @Test
    fun `practicar salta directo a la practica desde el resumen`() = runTest {
        // Vista V2 (v2-08): los tres momentos se muestran juntos y «Practicar»
        // salta a la práctica sin recorrer las páginas intermedias.
        assertEquals(TutorialStep.ENTER, viewModel.uiState.value.step)

        viewModel.onStartPractice()

        assertEquals(TutorialStep.PRACTICE, viewModel.uiState.value.step)
    }

    @Test
    fun `la clave real logra el ingreso de practica`() = runTest {
        // RF-10 criterio 2: la práctica usa la clave verdadera de la usuaria
        seedUserCode()

        viewModel.onPracticeInputChange("amanecer26")
        advanceUntilIdle()

        assertEquals(PracticeStatus.MATCHED, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `un texto corto no se evalua`() = runTest {
        seedUserCode()
        val hashCallsAfterSeed = hasher.hashCallCount

        viewModel.onPracticeInputChange("amane")
        advanceUntilIdle()

        assertEquals(PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)
        // Por debajo del mínimo ni se hashea, como en la barra real (RF-13)
        assertEquals(hashCallsAfterSeed, hasher.hashCallCount)
    }

    @Test
    fun `una clave distinta no logra el ingreso`() = runTest {
        seedUserCode()

        viewModel.onPracticeInputChange("atardecer26")
        advanceUntilIdle()

        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `el codigo de activacion no cuenta como practica`() = runTest {
        // Con clave de usuaria creada, la activación quedó reemplazada (RF-08);
        // la práctica exige la clave propia.
        seedUserCode()
        repository.seed(CodeSlot.ACTIVATION, "luna2026", hasher)

        viewModel.onPracticeInputChange("luna2026")
        advanceUntilIdle()

        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)
    }

    @Test
    fun `el ingreso logrado queda fijo aunque el texto cambie`() = runTest {
        // Criterio 2: la usuaria ya practicó; editar la barra no le quita el logro
        seedUserCode()

        viewModel.onPracticeInputChange("amanecer26")
        advanceUntilIdle()
        viewModel.onPracticeInputChange("amanecer2")
        advanceUntilIdle()

        assertEquals(PracticeStatus.MATCHED, viewModel.uiState.value.practiceStatus)
        assertEquals("amanecer2", viewModel.uiState.value.practiceInput)
    }

    @Test
    fun `un fallo interno de la puerta se muestra como no coincidencia`() = runTest {
        // §3 de ARQUITECTURA.md: las excepciones no cruzan a la UI; la puerta
        // degrada a PassThrough y la práctica lo pinta como «aún no coincide»
        seedUserCode()
        hasher.failOnHash = true

        viewModel.onPracticeInputChange("amanecer26")
        advanceUntilIdle()

        assertEquals(PracticeStatus.MISMATCH, viewModel.uiState.value.practiceStatus)
    }
}

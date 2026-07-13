package com.auroraclima.app.questionnaire.ui

import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.ScaleOption
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import com.auroraclima.app.questionnaire.domain.usecase.CalculateGuidanceLevelUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Flujo del cuestionario (RF-27): avance, retroceso, abandono y carga con el
 * contrato de error genérico. Las respuestas viven solo en el estado del
 * ViewModel: el descarte las deja vacías sin tocar repositorio alguno.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeQuestionnaireRepository()

    @Test
    fun `la carga exitosa expone el cuestionario en la portada`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorRes)
        assertEquals(GuidancePhase.INTRO, state.phase)
        assertEquals("f01", state.questionnaire?.id)
    }

    @Test
    fun `un fallo del repositorio se muestra solo como mensaje generico`() = runTest {
        // El detalle de la excepción revela proyecto y colecciones (P-24):
        // al estado de UI solo puede llegar el recurso genérico.
        repository.result = Result.failure(RuntimeException("detalle interno del SDK"))

        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(R.string.common_error_generic, state.errorRes)
        assertNull(state.questionnaire)
    }

    @Test
    fun `reintentar tras un fallo recupera el cuestionario`() = runTest {
        repository.result = Result.failure(RuntimeException("sin red"))
        val viewModel = viewModel()
        advanceUntilIdle()

        repository.result = Result.success(questionnaire())
        viewModel.onRetry()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.errorRes)
        assertEquals("f01", state.questionnaire?.id)
        assertEquals(2, repository.calls)
    }

    @Test
    fun `comenzar abre la primera pregunta`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onStart()

        val state = viewModel.uiState.value
        assertEquals(GuidancePhase.QUESTION, state.phase)
        assertEquals("q01", state.currentQuestion?.id)
    }

    @Test
    fun `responder y avanzar conserva la respuesta y muestra la siguiente`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()

        viewModel.onOptionSelected("s1")
        viewModel.onNext()

        val state = viewModel.uiState.value
        assertEquals("q02", state.currentQuestion?.id)
        assertEquals(mapOf("q01" to "s1"), state.answers)
    }

    @Test
    fun `avanzar sin responder esta permitido`() = runTest {
        // No se obliga a contestar una pregunta dura para continuar (RF-27,
        // no revictimización): la pregunta sin respuesta aporta 0 al motor.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()

        viewModel.onNext()

        val state = viewModel.uiState.value
        assertEquals("q02", state.currentQuestion?.id)
        assertEquals(emptyMap<String, String>(), state.answers)
    }

    @Test
    fun `cambiar la opcion elegida reemplaza la respuesta de la pregunta`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()

        viewModel.onOptionSelected("s1")
        viewModel.onOptionSelected("s2")

        assertEquals(mapOf("q01" to "s2"), viewModel.uiState.value.answers)
    }

    @Test
    fun `retroceder conserva la respuesta y regresa a la pregunta anterior`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()
        viewModel.onOptionSelected("s2")
        viewModel.onNext()

        viewModel.onPrevious()

        val state = viewModel.uiState.value
        assertEquals("q01", state.currentQuestion?.id)
        assertEquals("s2", state.selectedOptionId)
    }

    @Test
    fun `retroceder desde la primera pregunta vuelve a la portada`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()

        viewModel.onPrevious()

        assertEquals(GuidancePhase.INTRO, viewModel.uiState.value.phase)
    }

    @Test
    fun `terminar la ultima pregunta pasa al resultado`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()
        repeat(3) {
            viewModel.onOptionSelected("s1")
            viewModel.onNext()
        }

        assertEquals(GuidancePhase.RESULT, viewModel.uiState.value.phase)
    }

    @Test
    fun `el resultado se calcula con el motor al terminar`() = runTest {
        // RF-29: todo «Con frecuencia» → (1+1+3)×2 = 10 → nivel n03, con sus
        // rutas recomendadas listas para el acceso directo (criterio 2).
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()
        repeat(3) {
            viewModel.onOptionSelected("s2")
            viewModel.onNext()
        }

        val result = viewModel.uiState.value.result
        assertEquals("n03", result?.level?.id)
        assertEquals(10, result?.score)
        assertEquals(3, result?.answeredCount)
        assertEquals(3, result?.questionCount)
        assertEquals(listOf("r03"), result?.level?.routeIds)
    }

    @Test
    fun `el resultado registra cuantas preguntas quedaron sin responder`() = runTest {
        // Con pocas respuestas la UI matiza el nivel (nota de GuidanceResult):
        // un nivel bajo por silencio no debe leerse como ausencia de señales.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()
        viewModel.onOptionSelected("s1")
        repeat(3) { viewModel.onNext() }

        val result = viewModel.uiState.value.result
        assertEquals(1, result?.answeredCount)
        assertEquals(3, result?.questionCount)
    }

    @Test
    fun `abandonar descarta todas las respuestas y vuelve a la portada`() = runTest {
        // RF-27 criterio 2: al abandonar no se guarda ninguna respuesta.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()
        viewModel.onOptionSelected("s2")
        viewModel.onNext()
        viewModel.onOptionSelected("s1")

        viewModel.onAbandon()

        val state = viewModel.uiState.value
        assertEquals(GuidancePhase.INTRO, state.phase)
        assertEquals(0, state.questionIndex)
        assertEquals(emptyMap<String, String>(), state.answers)
        assertNull(state.result)
    }

    private fun viewModel() = GuidanceViewModel(
        repository = repository,
        calculateGuidanceLevel = CalculateGuidanceLevelUseCase()
    )
}

internal class FakeQuestionnaireRepository : QuestionnaireRepository {
    var result: Result<Questionnaire> = Result.success(questionnaire())
    var calls: Int = 0
        private set

    override suspend fun getQuestionnaire(): Result<Questionnaire> {
        calls++
        return result
    }
}

/** Instrumento mínimo de prueba: 3 preguntas, escala F6 y umbrales 0/4/10. */
internal fun questionnaire() = Questionnaire(
    id = "f01",
    version = 1,
    title = "Título",
    intro = "Intro",
    notice = "Aviso",
    scale = listOf(
        ScaleOption("s0", 1, "Nunca", 0),
        ScaleOption("s1", 2, "Alguna vez", 1),
        ScaleOption("s2", 3, "Con frecuencia", 2)
    ),
    questions = listOf(
        Question("q01", 1, "primera", weight = 1),
        Question("q02", 2, "segunda", weight = 1),
        Question("q03", 3, "tercera", weight = 3, alertLevelId = "n03")
    ),
    levels = listOf(
        level("n01", order = 1, minScore = 0),
        level("n02", order = 2, minScore = 4),
        level("n03", order = 3, minScore = 10)
    )
)

private fun level(id: String, order: Int, minScore: Int) = GuidanceLevel(
    id = id,
    order = order,
    minScore = minScore,
    title = "Nivel $id",
    summary = "Resumen",
    advice = listOf("Consejo"),
    routeIds = listOf("r0$order")
)

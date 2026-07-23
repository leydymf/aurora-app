package com.auroraclima.app.questionnaire.ui

import androidx.lifecycle.ViewModelStore
import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.core.directory.DirectoryAccess
import com.auroraclima.app.core.directory.DirectoryEntity
import com.auroraclima.app.core.directory.DirectoryLine
import com.auroraclima.app.core.directory.DirectoryProvider
import com.auroraclima.app.core.security.MonotonicClock
import com.auroraclima.app.core.security.SessionManager
import com.auroraclima.app.questionnaire.domain.model.Block
import com.auroraclima.app.questionnaire.domain.model.Dimension
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.Municipality
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.Recognition
import com.auroraclima.app.questionnaire.domain.model.Scale
import com.auroraclima.app.questionnaire.domain.model.ScaleOption
import com.auroraclima.app.questionnaire.domain.model.ThresholdFlag
import com.auroraclima.app.questionnaire.domain.repository.QuestionnaireRepository
import com.auroraclima.app.questionnaire.domain.usecase.CalculateGuidanceLevelUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Flujo del cuestionario (RF-27): avance, retroceso, abandono, cálculo del
 * resultado y resolución de los accesos por municipio (§6). Las respuestas
 * viven solo en el estado del ViewModel: el descarte las deja vacías sin tocar
 * repositorio alguno.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeQuestionnaireRepository()
    private val directory = FakeDirectoryProvider()

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
        assertEquals(listOf("s0", "s1", "s2"), state.currentOptions.map { it.id })
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
        // RF-29: todo «Con frecuencia» → (1+1+3)×2 = 10 → nivel n03; la
        // dimensión de q01 se enciende (Track 1).
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
        assertEquals(listOf("d1"), result?.dimensions?.map { it.id })
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
    fun `al llegar al resultado se resuelven las lineas y entidades fijas`() = runTest {
        // §6: los accesos se resuelven contra content por el contrato de core.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()
        repeat(3) {
            viewModel.onOptionSelected("s2")
            viewModel.onNext()
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("l1"), state.nationalLines.map { it.id })
        assertEquals(listOf("af"), state.fixedEntities.map { it.id })
        // La consulta pide TODAS las entidades posibles (fijas + de todo municipio).
        assertEquals(listOf("af", "am1"), directory.lastEntityIds)
    }

    @Test
    fun `elegir municipio muestra las entidades de ese municipio`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()
        repeat(3) {
            viewModel.onOptionSelected("s2")
            viewModel.onNext()
        }
        advanceUntilIdle()
        assertEquals(emptyList<String>(), viewModel.uiState.value.municipalEntities.map { it.id })

        viewModel.onMunicipalitySelected("m1")

        assertEquals(listOf("am1"), viewModel.uiState.value.municipalEntities.map { it.id })
    }

    @Test
    fun `abandonar descarta respuestas resultado municipio y accesos`() = runTest {
        // RF-27 criterio 2: al abandonar no se guarda ninguna respuesta.
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onStart()
        repeat(3) {
            viewModel.onOptionSelected("s2")
            viewModel.onNext()
        }
        advanceUntilIdle()
        viewModel.onMunicipalitySelected("m1")

        viewModel.onAbandon()

        val state = viewModel.uiState.value
        assertEquals(GuidancePhase.INTRO, state.phase)
        assertEquals(0, state.questionIndex)
        assertEquals(emptyMap<String, String>(), state.answers)
        assertNull(state.result)
        assertNull(state.selectedMunicipalityId)
        assertNull(state.access)
    }

    @Test
    fun `la limpieza de sesion de RF-16 descarta las respuestas en memoria`() = runTest {
        // Regresión RF-28: la salida rápida usa la MISMA rutina closeSession()
        // que la inactividad y el background prolongado — el cleaner dinámico
        // del ViewModel debe borrar las respuestas en ese mismo instante.
        val sessionManager = sessionManager()
        val viewModel = viewModel(sessionManager)
        advanceUntilIdle()
        sessionManager.startSession()
        viewModel.onStart()
        viewModel.onOptionSelected("s2")
        viewModel.onNext()
        viewModel.onOptionSelected("s1")

        sessionManager.quickExit()

        val state = viewModel.uiState.value
        assertEquals(emptyMap<String, String>(), state.answers)
        assertEquals(GuidancePhase.INTRO, state.phase)
        assertNull(state.result)
    }

    @Test
    fun `la limpieza de sesion tambien descarta el resultado ya calculado`() = runTest {
        val sessionManager = sessionManager()
        val viewModel = viewModel(sessionManager)
        advanceUntilIdle()
        sessionManager.startSession()
        viewModel.onStart()
        repeat(3) {
            viewModel.onOptionSelected("s2")
            viewModel.onNext()
        }

        sessionManager.closeSession()

        val state = viewModel.uiState.value
        assertNull(state.result)
        assertEquals(emptyMap<String, String>(), state.answers)
    }

    @Test
    fun `al morir el viewmodel su cleaner queda dado de baja`() = runTest {
        // El pop del grafo destruye el ViewModel; su registro no debe quedar
        // colgando en SessionManager para cierres posteriores.
        val sessionManager = sessionManager()
        val viewModel = viewModel(sessionManager)
        advanceUntilIdle()
        val store = ViewModelStore()
        store.put("guidance", viewModel)
        store.clear()

        viewModel.onStart()
        viewModel.onOptionSelected("s1")
        sessionManager.startSession()
        sessionManager.closeSession()

        // Con el cleaner dado de baja, el cierre ya no toca este estado.
        assertEquals(mapOf("q01" to "s1"), viewModel.uiState.value.answers)
    }

    private fun viewModel(sessionManager: SessionManager = sessionManager()) = GuidanceViewModel(
        repository = repository,
        calculateGuidanceLevel = CalculateGuidanceLevelUseCase(),
        directory = directory,
        sessionManager = sessionManager
    )

    private fun sessionManager() = SessionManager(
        scope = CoroutineScope(mainDispatcherRule.dispatcher),
        monotonicClock = MonotonicClock { 0L },
        boundCleaners = emptySet()
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

private class FakeDirectoryProvider : DirectoryProvider {
    var lastLineIds: List<String>? = null
    var lastEntityIds: List<String>? = null
    var access = DirectoryAccess(
        lines = listOf(DirectoryLine("l1", "Línea", "123", "descripción", null)),
        entities = listOf(
            DirectoryEntity("af", "Entidad fija", "rol", "cuándo", listOf("111"), null, null),
            DirectoryEntity("am1", "Entidad municipal", "rol", "cuándo", listOf("222"), null, null)
        )
    )

    override suspend fun resolve(lineIds: List<String>, entityIds: List<String>): Result<DirectoryAccess> {
        lastLineIds = lineIds
        lastEntityIds = entityIds
        return Result.success(access)
    }
}

/** Instrumento mínimo de prueba: 3 preguntas puntuables, escala 0–2, umbrales 0/4/10. */
internal fun questionnaire() = Questionnaire(
    id = "f01",
    version = 2,
    title = "Título",
    intro = "Intro",
    notice = "Aviso",
    scales = listOf(
        Scale(
            "freq",
            listOf(
                ScaleOption("s0", 1, "Nunca", 0),
                ScaleOption("s1", 2, "Alguna vez", 1),
                ScaleOption("s2", 3, "Con frecuencia", 2)
            )
        )
    ),
    blocks = listOf(Block("a", 1, "Lo que estás viviendo")),
    questions = listOf(
        Question("q01", 1, "a", "freq", "primera", weight = 1),
        Question("q02", 2, "a", "freq", "segunda", weight = 1),
        Question("q03", 3, "a", "freq", "tercera", weight = 3)
    ),
    dimensions = listOf(Dimension("d1", 1, "violencia psicológica", listOf("q01"), 1)),
    recognition = Recognition("Por tus respuestas, aparecen señales de", "No aparecen señales"),
    thresholdFlags = listOf(ThresholdFlag("q03", 2, "n03")),
    countFlags = emptyList(),
    municipalities = listOf(Municipality("m1", 1, "Bucaramanga"), Municipality("m2", 2, "Girón")),
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
    lineIds = if (id == "n03") listOf("l1") else emptyList(),
    agencyIds = if (id == "n03") listOf("af") else emptyList(),
    municipalAgencyIds = if (id == "n03") mapOf("m1" to listOf("am1")) else emptyMap()
)

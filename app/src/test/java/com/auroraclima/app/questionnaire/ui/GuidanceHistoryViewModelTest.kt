package com.auroraclima.app.questionnaire.ui

import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.questionnaire.domain.model.NewSavedOutcome
import com.auroraclima.app.questionnaire.domain.model.SavedOutcome
import com.auroraclima.app.questionnaire.domain.repository.OutcomeSyncRepository
import com.auroraclima.app.questionnaire.domain.repository.SavedOutcomeRepository
import com.auroraclima.app.questionnaire.domain.usecase.DeleteSavedOutcomeUseCase
import java.time.Instant
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
 * Listado de resultados guardados (RF-31): expone el orden del repositorio,
 * el aviso RF-30 llega del documento (tolerante a su ausencia), y la
 * eliminación exige dos pasos y propaga a la nube (patrón RF-36).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceHistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val questionnaireRepository = FakeQuestionnaireRepository()
    private val repository = ListingSavedOutcomeRepository()
    private val sync = HistoryOutcomeSyncRepository()

    private fun viewModel() = GuidanceHistoryViewModel(
        repository = repository,
        questionnaireRepository = questionnaireRepository,
        deleteSavedOutcome = DeleteSavedOutcomeUseCase(repository, sync)
    )

    @Test
    fun `expone los guardados en el orden del repositorio y el aviso del documento`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(2L, 1L), state.outcomes.map { it.id })
        assertEquals("Aviso", state.notice)
    }

    @Test
    fun `sin documento el listado se muestra igual sin aviso`() = runTest {
        // Tolerancia estilo RF-25: el aviso ya acompañó al resultado al
        // calcularse; su ausencia aquí no puede tumbar la consulta.
        questionnaireRepository.result = Result.failure(RuntimeException("sin red"))
        val viewModel = viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.outcomes.size)
        assertNull(state.notice)
    }

    @Test
    fun `un fallo del almacen muestra solo el mensaje generico`() = runTest {
        repository.outcomesResult = Result.failure(RuntimeException("detalle interno"))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(R.string.common_error_generic, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `seleccionar un guardado abre su detalle y descartar lo cierra`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onOutcomeSelected(2L)
        assertEquals(2L, viewModel.uiState.value.detail?.id)

        viewModel.onDetailDismissed()
        assertNull(viewModel.uiState.value.detail)
    }

    @Test
    fun `eliminar exige el segundo paso y nada se borra antes de confirmar`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOutcomeSelected(2L)

        viewModel.onDeleteRequested()

        assertTrue(viewModel.uiState.value.isConfirmingDelete)
        assertEquals(emptyList<Long>(), repository.deleted)

        viewModel.onDeleteDismissed()
        assertFalse(viewModel.uiState.value.isConfirmingDelete)
        assertEquals(2L, viewModel.uiState.value.detail?.id)
    }

    @Test
    fun `confirmar borra local propaga a la nube y saca el guardado de la lista`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOutcomeSelected(2L)
        viewModel.onDeleteRequested()

        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(2L), repository.deleted)
        assertEquals(listOf(2L), sync.removed)
        assertEquals(listOf(1L), state.outcomes.map { it.id })
        assertNull(state.detail)
        assertFalse(state.isConfirmingDelete)
    }

    @Test
    fun `un fallo del borrado muestra el error generico y conserva el guardado`() = runTest {
        repository.deleteResult = Result.failure(RuntimeException("detalle interno"))
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onOutcomeSelected(2L)
        viewModel.onDeleteRequested()

        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.deleteErrorRes)
        assertEquals(listOf(2L, 1L), state.outcomes.map { it.id })
        assertEquals(emptyList<Long>(), sync.removed)
    }
}

/** Dos guardados servidos ya en orden (el orden lo fija el DAO y lo prueba el repositorio). */
private class ListingSavedOutcomeRepository : SavedOutcomeRepository {
    var outcomesResult: Result<List<SavedOutcome>>? = null
    var deleteResult: Result<Unit> = Result.success(Unit)
    val deleted = mutableListOf<Long>()

    private val stored = listOf(
        SavedOutcome(id = 2L, savedAt = Instant.ofEpochMilli(300), rank = 3, label = "reciente", tags = listOf("a")),
        SavedOutcome(id = 1L, savedAt = Instant.ofEpochMilli(100), rank = 1, label = "viejo", tags = emptyList())
    )

    override suspend fun save(outcome: NewSavedOutcome): Result<Long> = Result.success(1L)

    override suspend fun outcomes(): Result<List<SavedOutcome>> = outcomesResult
        ?: Result.success(stored.filterNot { it.id in deleted })

    override suspend fun outcome(id: Long): Result<SavedOutcome?> = Result.success(stored.firstOrNull { it.id == id })

    override suspend fun delete(id: Long): Result<Unit> {
        if (deleteResult.isSuccess) deleted += id
        return deleteResult
    }
}

private class HistoryOutcomeSyncRepository : OutcomeSyncRepository {
    val removed = mutableListOf<Long>()

    override suspend fun push(outcomeId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun remove(outcomeId: Long): Result<Unit> {
        removed += outcomeId
        return Result.success(Unit)
    }

    override suspend fun restore(): Result<Unit> = Result.success(Unit)
}

package com.auroraclima.app.questionnaire.domain.usecase

import com.auroraclima.app.questionnaire.domain.model.NewSavedOutcome
import com.auroraclima.app.questionnaire.domain.model.SavedOutcome
import com.auroraclima.app.questionnaire.domain.repository.OutcomeSyncRepository
import com.auroraclima.app.questionnaire.domain.repository.SavedOutcomeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Eliminación individual de un resultado guardado (RF-31, patrón RF-36): el
 * borrado local manda; la propagación remota es de mejor esfuerzo y su fallo
 * no revierte nada.
 */
class DeleteSavedOutcomeUseCaseTest {
    private val repository = RecordingSavedOutcomeRepository()
    private val sync = RecordingOutcomeSyncRepository()
    private val useCase = DeleteSavedOutcomeUseCase(repository, sync)

    @Test
    fun `borra local y propaga el mismo id a la nube`() = runTest {
        val result = useCase(42L)

        assertTrue(result.isSuccess)
        assertEquals(listOf(42L), repository.deleted)
        assertEquals(listOf("doc-42"), sync.removed)
    }

    @Test
    fun `si el borrado local falla nada se propaga`() = runTest {
        repository.deleteResult = Result.failure(IllegalStateException())

        val result = useCase(42L)

        assertTrue(result.isFailure)
        assertEquals(emptyList<String>(), sync.removed)
    }

    @Test
    fun `el fallo remoto se ignora porque localmente ya no existe`() = runTest {
        sync.removeResult = Result.failure(IllegalStateException())

        val result = useCase(42L)

        assertTrue(result.isSuccess)
        assertEquals(listOf(42L), repository.deleted)
    }
}

private class RecordingSavedOutcomeRepository : SavedOutcomeRepository {
    val deleted = mutableListOf<Long>()
    var deleteResult: Result<Unit> = Result.success(Unit)

    override suspend fun save(outcome: NewSavedOutcome): Result<Long> = Result.success(1L)

    override suspend fun outcomes(): Result<List<SavedOutcome>> = Result.success(emptyList())

    override suspend fun outcome(id: Long): Result<SavedOutcome?> = Result.success(null)

    override suspend fun delete(id: Long): Result<String> {
        if (deleteResult.isSuccess) deleted += id
        return deleteResult.map { "doc-$id" }
    }
}

private class RecordingOutcomeSyncRepository : OutcomeSyncRepository {
    val removed = mutableListOf<String>()
    var removeResult: Result<Unit> = Result.success(Unit)

    override suspend fun push(outcomeId: Long): Result<Unit> = Result.success(Unit)

    override suspend fun remove(docId: String): Result<Unit> {
        removed += docId
        return removeResult
    }

    override suspend fun restore(): Result<Unit> = Result.success(Unit)

    override suspend fun reconcile(): Result<Unit> = Result.success(Unit)
}

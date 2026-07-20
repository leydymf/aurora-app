package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.ui.FakeJournalRepository
import com.auroraclima.app.records.ui.FakeJournalSyncRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF-36: el borrado local manda; la propagación remota apunta al mismo docId y
 * es de mejor esfuerzo (§3) — sin red queda en la cola durable de Firestore
 * (cubierto en FirestoreJournalSyncRepositoryTest).
 */
class DeleteJournalEntryUseCaseTest {
    private val repository = FakeJournalRepository()
    private val sync = FakeJournalSyncRepository()
    private val useCase = DeleteJournalEntryUseCase(repository, sync)

    @Test
    fun `borra local y propaga el mismo id a la nube`() = runTest {
        val result = useCase(7)

        assertTrue(result.isSuccess)
        assertEquals(listOf(7L), repository.deleted)
        assertEquals(listOf(7L), sync.removed)
    }

    @Test
    fun `si el borrado local falla no toca la nube`() = runTest {
        repository.deleteResult = Result.failure(IllegalStateException("blob corrupto"))

        val result = useCase(7)

        assertTrue(result.isFailure)
        assertTrue(sync.removed.isEmpty())
    }

    @Test
    fun `un fallo de la propagacion remota no afecta el resultado`() = runTest {
        // Sin sesión o sin red: localmente ya no existe y la UI no ve error revelador.
        sync.removeResult = Result.failure(IllegalStateException("sin sesión"))

        val result = useCase(7)

        assertTrue(result.isSuccess)
        assertEquals(listOf(7L), repository.deleted)
    }
}

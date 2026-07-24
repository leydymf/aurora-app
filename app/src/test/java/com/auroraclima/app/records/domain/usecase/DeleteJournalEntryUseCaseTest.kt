package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.ui.FakeJournalAttachmentRepository
import com.auroraclima.app.records.ui.FakeJournalRepository
import com.auroraclima.app.records.ui.FakeJournalSyncRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF-36: el borrado local manda; con él caen las evidencias del registro
 * (criterio 2, RF-33) y la propagación remota apunta al mismo docId, de mejor
 * esfuerzo (§3) — sin red queda en la cola durable de Firestore (cubierto en
 * FirestoreJournalSyncRepositoryTest).
 */
class DeleteJournalEntryUseCaseTest {
    private val repository = FakeJournalRepository()
    private val attachments = FakeJournalAttachmentRepository()
    private val sync = FakeJournalSyncRepository()
    private val useCase = DeleteJournalEntryUseCase(repository, attachments, sync)

    @Test
    fun `borra local con sus evidencias y propaga el mismo id a la nube`() = runTest {
        val result = useCase(7)

        assertTrue(result.isSuccess)
        assertEquals(listOf(7L), repository.deleted)
        // RF-36 criterio 2: las evidencias del registro caen con él.
        assertEquals(listOf(7L), attachments.deletedOwners)
        assertEquals(listOf(7L), sync.removed)
    }

    @Test
    fun `si el borrado local falla no toca evidencias ni nube`() = runTest {
        repository.deleteResult = Result.failure(IllegalStateException("blob corrupto"))

        val result = useCase(7)

        assertTrue(result.isFailure)
        assertTrue(attachments.deletedOwners.isEmpty())
        assertTrue(sync.removed.isEmpty())
    }

    @Test
    fun `un fallo del borrado de evidencias no revierte ni bloquea`() = runTest {
        // Quedarían huérfanas (ilegibles) y el barrido las limpia después.
        attachments.deleteForOwnerResult = Result.failure(IllegalStateException("bd"))

        val result = useCase(7)

        assertTrue(result.isSuccess)
        assertEquals(listOf(7L), repository.deleted)
        assertEquals(listOf(7L), sync.removed)
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

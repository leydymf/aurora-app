package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.ui.FakeJournalAttachmentRepository
import com.auroraclima.app.records.ui.FakeJournalRepository
import com.auroraclima.app.records.ui.FakeJournalSyncRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF-32 + RF-37: el guardado local es la fuente de verdad y la sync es de mejor
 * esfuerzo. Un fallo local aborta y no sincroniza; un fallo de sync no afecta el
 * resultado que ve la UI (§3, camuflaje). RF-33: el guardado reclama los
 * adjuntos en preparación con el id recién asignado.
 */
class AddJournalEntryUseCaseTest {
    private val repository = FakeJournalRepository()
    private val attachments = FakeJournalAttachmentRepository()
    private val sync = FakeJournalSyncRepository()
    private val useCase = AddJournalEntryUseCase(repository, attachments, sync)

    private val entry = NewJournalEntry(
        occurredOn = LocalDate.of(2026, 6, 28),
        kind = EntryKind.ECONOMIC,
        detail = "tomó el dinero del mercado"
    )

    @Test
    fun `guarda local y sincroniza con el id asignado`() = runTest {
        val result = useCase(entry)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.added.size)
        // La sync recibe el id local recién asignado (docId determinista, RF-37);
        // el contenido lo lee la implementación desde la fila (RF-39).
        assertEquals(listOf(1L), sync.pushed)
    }

    @Test
    fun `si el guardado local falla no sincroniza`() = runTest {
        repository.addResult = Result.failure(IllegalStateException("blob corrupto"))

        val result = useCase(entry)

        assertTrue(result.isFailure)
        assertTrue(sync.pushed.isEmpty())
        assertTrue(attachments.claimed.isEmpty())
    }

    @Test
    fun `reclama los adjuntos en preparacion con el id nuevo`() = runTest {
        // RF-33: los adjuntos importados durante la creación pasan a pertenecer
        // al registro recién guardado.
        val result = useCase(entry, attachmentIds = listOf(4L, 9L))

        assertTrue(result.isSuccess)
        assertEquals(listOf(listOf(4L, 9L) to 1L), attachments.claimed)
    }

    @Test
    fun `sin adjuntos no reclama nada`() = runTest {
        useCase(entry)

        assertTrue(attachments.claimed.isEmpty())
    }

    @Test
    fun `un fallo del reclamo no afecta el guardado`() = runTest {
        // Mejor esfuerzo, como la sync: los huérfanos los limpia el barrido.
        attachments.claimResult = Result.failure(IllegalStateException("bd"))

        val result = useCase(entry, attachmentIds = listOf(4L))

        assertTrue(result.isSuccess)
        assertEquals(1, sync.pushed.size)
    }

    @Test
    fun `un fallo de la sync no afecta el resultado del guardado`() = runTest {
        // Sin sesión o sin red: la nube falla, pero el registro ya está a salvo localmente.
        sync.pushResult = Result.failure(IllegalStateException("sin sesión"))

        val result = useCase(entry)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.added.size)
    }
}

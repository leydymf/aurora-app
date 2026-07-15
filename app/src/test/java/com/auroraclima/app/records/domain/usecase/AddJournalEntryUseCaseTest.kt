package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.NewJournalEntry
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
 * resultado que ve la UI (§3, camuflaje).
 */
class AddJournalEntryUseCaseTest {
    private val repository = FakeJournalRepository()
    private val sync = FakeJournalSyncRepository()
    private val useCase = AddJournalEntryUseCase(repository, sync)

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
        // El docId de la sync es el id local (mapeo determinista, RF-37).
        assertEquals(1, sync.pushed.size)
        assertEquals(1L, sync.pushed[0].id)
        assertEquals(entry.occurredOn, sync.pushed[0].occurredOn)
        assertEquals(entry.kind, sync.pushed[0].kind)
        assertEquals(entry.detail, sync.pushed[0].detail)
    }

    @Test
    fun `si el guardado local falla no sincroniza`() = runTest {
        repository.addResult = Result.failure(IllegalStateException("blob corrupto"))

        val result = useCase(entry)

        assertTrue(result.isFailure)
        assertTrue(sync.pushed.isEmpty())
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

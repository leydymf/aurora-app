package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.ui.FakeJournalRepository
import com.auroraclima.app.records.ui.FakeJournalSyncRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RF-35 + RF-37: la edición guarda local (fuente de verdad) y re-sincroniza el
 * MISMO documento con el contenido nuevo (docId = id local, idempotente). Un
 * fallo local aborta sin sincronizar; un fallo de sync es silencioso (§3).
 */
class UpdateJournalEntryUseCaseTest {
    private val repository = FakeJournalRepository()
    private val sync = FakeJournalSyncRepository()
    private val useCase = UpdateJournalEntryUseCase(repository, sync)

    private val edited = JournalEntry(
        id = 7,
        occurredOn = LocalDate.of(2026, 6, 29),
        kind = EntryKind.PSYCHOLOGICAL,
        detail = "el relato corregido"
    )

    @Test
    fun `actualiza local y re-sincroniza el mismo id con el contenido editado`() = runTest {
        val result = useCase(edited)

        assertTrue(result.isSuccess)
        assertEquals(listOf(edited), repository.updated)
        // La re-subida apunta al MISMO docId (sobrescritura idempotente, RF-37).
        assertEquals(1, sync.pushed.size)
        assertEquals(7L, sync.pushed[0].id)
        assertEquals("el relato corregido", sync.pushed[0].detail)
    }

    @Test
    fun `si el guardado local falla no sincroniza`() = runTest {
        repository.updateResult = Result.failure(IllegalStateException("blob corrupto"))

        val result = useCase(edited)

        assertTrue(result.isFailure)
        assertTrue(sync.pushed.isEmpty())
    }

    @Test
    fun `un fallo de la sync no afecta el resultado de la edicion`() = runTest {
        // Sin sesión o sin red: la nube falla, pero el cambio ya está a salvo localmente.
        sync.pushResult = Result.failure(IllegalStateException("sin sesión"))

        val result = useCase(edited)

        assertTrue(result.isSuccess)
        assertEquals(1, repository.updated.size)
    }
}

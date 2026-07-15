package com.auroraclima.app.records.ui

import com.auroraclima.app.R
import com.auroraclima.app.core.MainDispatcherRule
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.usecase.AddJournalEntryUseCase
import java.time.LocalDate
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
 * Creación de un registro (RF-32): validación en línea sin pérdida de lo
 * diligenciado (criterio 2) y guardado protegido con error solo genérico
 * ante fallo del repositorio (P-36, P-24).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeJournalRepository()
    private val sync = FakeJournalSyncRepository()

    private fun viewModel() = JournalEditorViewModel(AddJournalEntryUseCase(repository, sync))

    @Test
    fun `guardar sin campos obligatorios señala ambos sin perder la fecha`() = runTest {
        val viewModel = viewModel()
        viewModel.onDaySelected(LocalDate.of(2026, 7, 2))

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.journal_kind_missing, state.kindErrorRes)
        assertEquals(R.string.journal_detail_missing, state.detailErrorRes)
        assertEquals(LocalDate.of(2026, 7, 2), state.occurredOn)
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `guardar sin tipo conserva el texto ya escrito`() = runTest {
        // RF-32 criterio 2: señalar lo que falta jamás borra lo diligenciado.
        val viewModel = viewModel()
        viewModel.onDetailChange("me quitó el dinero del mercado")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.journal_kind_missing, state.kindErrorRes)
        assertNull(state.detailErrorRes)
        assertEquals("me quitó el dinero del mercado", state.detail)
        assertTrue(repository.added.isEmpty())
    }

    @Test
    fun `elegir el tipo despeja su error en linea`() = runTest {
        val viewModel = viewModel()
        viewModel.onDetailChange("texto")
        viewModel.onSubmit()
        advanceUntilIdle()

        viewModel.onKindSelected(EntryKind.ECONOMIC)

        assertNull(viewModel.uiState.value.kindErrorRes)
    }

    @Test
    fun `con todo diligenciado guarda recortado y emite el evento`() = runTest {
        val viewModel = viewModel()
        viewModel.onDaySelected(LocalDate.of(2026, 6, 28))
        viewModel.onKindSelected(EntryKind.ECONOMIC)
        viewModel.onDetailChange("  tomó el dinero del mercado  ")

        viewModel.onSubmit()
        advanceUntilIdle()

        assertEquals(1, repository.added.size)
        val saved = repository.added[0]
        assertEquals(LocalDate.of(2026, 6, 28), saved.occurredOn)
        assertEquals(EntryKind.ECONOMIC, saved.kind)
        assertEquals("tomó el dinero del mercado", saved.detail)
        // RF-37: el registro guardado también se sincroniza (recortado, mismo contenido).
        assertEquals(1, sync.pushed.size)
        assertEquals("tomó el dinero del mercado", sync.pushed[0].detail)
        // replay = 1: el evento queda en la caché para la suscripción de la UI.
        assertEquals(listOf(Unit), viewModel.savedEvents.replayCache)
    }

    @Test
    fun `un fallo de la sync no rompe el guardado ni asoma error`() = runTest {
        // RF-37/§3: la nube es mejor esfuerzo; su fallo es silencioso para la UI.
        sync.pushResult = Result.failure(IllegalStateException("sin sesión"))
        val viewModel = viewModel()
        viewModel.onKindSelected(EntryKind.PHYSICAL)
        viewModel.onDetailChange("lo que pasó")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.saveErrorRes)
        assertEquals(1, repository.added.size)
        assertEquals(listOf(Unit), viewModel.savedEvents.replayCache)
    }

    @Test
    fun `sin guardar no hay evento emitido`() = runTest {
        val viewModel = viewModel()

        viewModel.onSubmit()
        advanceUntilIdle()

        assertTrue(viewModel.savedEvents.replayCache.isEmpty())
    }

    @Test
    fun `un fallo del repositorio muestra solo el error generico y no pierde nada`() = runTest {
        // El detalle de la excepción (rutas, passphrase, SQL) jamás llega a la UI (P-24/P-36).
        repository.addResult = Result.failure(IllegalStateException("blob corrupto: detalle interno"))
        val viewModel = viewModel()
        viewModel.onKindSelected(EntryKind.PHYSICAL)
        viewModel.onDetailChange("lo que pasó")

        viewModel.onSubmit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(R.string.common_error_generic, state.saveErrorRes)
        assertFalse(state.isSaving)
        assertEquals("lo que pasó", state.detail)
        assertEquals(EntryKind.PHYSICAL, state.kind)
    }
}

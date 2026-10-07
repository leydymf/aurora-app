package com.auroraclima.app.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.NewJournalEntry
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contrato P-36 del repositorio del historial: la BD se abre perezosamente en
 * el primer uso (nunca en la inyección) y TODA excepción de la apertura —
 * incluido el lanzamiento deliberado ante blob de passphrase corrupto, ADR
 * D6 — se traduce a Result en vez de cruzar hacia la UI (§3).
 *
 * Sobre Room en memoria sin cifrar por la misma razón que JournalDaoTest:
 * SQLCipher no carga en la JVM del host (ADR 2026-07-10).
 */
@RunWith(RobolectricTestRunner::class)
class LocalJournalRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()

    @After
    fun tearDown() {
        database.close()
    }

    private var nextDocId = 0

    private fun repositoryOf(source: JournalDatabaseSource) = LocalJournalRepository(JournalStoreAccess(source)) {
        "doc-${++nextDocId}"
    }

    private fun newEntry(kindId: String = "t02", kindLabel: String = "Segunda", detail: String = "x") =
        NewJournalEntry(occurredOn = LocalDate.now(), kindId = kindId, kindLabel = kindLabel, detail = detail)

    @Test
    fun `guarda y devuelve los registros mapeados al dominio`() = runTest {
        val repository = repositoryOf { database }

        val saved = repository.add(
            NewJournalEntry(
                occurredOn = LocalDate.of(2026, 7, 2),
                kindId = "t02",
                kindLabel = "Segunda",
                detail = "lo que pasó"
            )
        )
        val entries = repository.entries().getOrThrow()

        assertTrue(saved.isSuccess)
        assertEquals(1, entries.size)
        assertEquals(LocalDate.of(2026, 7, 2), entries[0].occurredOn)
        assertEquals("t02", entries[0].kindId)
        assertEquals("Segunda", entries[0].kindLabel)
        assertEquals("lo que pasó", entries[0].detail)
        assertEquals(entries[0], repository.entry(entries[0].id).getOrThrow())
    }

    @Test
    fun `la fuente no se abre hasta el primer uso y se abre una sola vez`() = runTest {
        var opened = 0
        val repository = repositoryOf {
            opened++
            database
        }
        // P-36 (b): inyectar/construir el repositorio no abre la BD.
        assertEquals(0, opened)

        repository.add(newEntry())
        repository.entries()
        repository.entry(1)

        assertEquals(1, opened)
    }

    @Test
    fun `una fuente que lanza se traduce a failure en todas las operaciones`() = runTest {
        // P-36 (a): el blob corrupto lanza a propósito (DatabaseKeyManager);
        // aquí el repositorio es el garante de la promesa «quien consume traduce».
        val repository = repositoryOf { error("blob corrupto") }

        assertTrue(repository.add(newEntry()).isFailure)
        assertTrue(repository.entries().isFailure)
        assertTrue(repository.entry(1).isFailure)
        assertTrue(
            repository.update(JournalEntry(1, LocalDate.now(), "t03", "Tercera", "x")).isFailure
        )
        assertTrue(repository.delete(1).isFailure)
    }

    @Test
    fun `edita y borra un registro existente`() = runTest {
        // RF-35/RF-36 sobre el esquema real: el contenido editado se relee y el
        // borrado deja de exponer el registro.
        val repository = repositoryOf { database }
        val id = repository.add(
            NewJournalEntry(LocalDate.of(2026, 6, 28), "t04", "Cuarta", "original")
        ).getOrThrow()

        val edited = JournalEntry(
            id = id,
            occurredOn = LocalDate.of(2026, 6, 29),
            kindId = "t02",
            kindLabel = "Segunda",
            detail = "corregido"
        )
        assertTrue(repository.update(edited).isSuccess)
        assertEquals(edited, repository.entry(id).getOrThrow())

        assertTrue(repository.delete(id).isSuccess)
        assertNull(repository.entry(id).getOrThrow())
        assertTrue(repository.entries().getOrThrow().isEmpty())
    }

    @Test
    fun `un fallo de apertura no se memoriza y el siguiente uso reintenta`() = runTest {
        var attempts = 0
        val repository = repositoryOf {
            attempts++
            if (attempts == 1) error("fallo transitorio") else database
        }

        assertTrue(repository.entries().isFailure)
        assertTrue(repository.entries().isSuccess)
    }

    @Test
    fun `un tipo que ya no esta publicado y el registro sin tipo se conservan en la lista`() = runTest {
        // P-68: el repositorio no valida contra ningún catálogo — la resolución
        // del nombre y el visual es de presentación; nada desaparece.
        database.journalDao().insert(
            JournalEntryRow(day = 1, kind = "t09", label = "Retirado", detail = "x", createdAt = 1)
        )
        database.journalDao().insert(JournalEntryRow(day = 2, kind = "", detail = "y", createdAt = 2))
        val repository = repositoryOf { database }

        val entries = repository.entries().getOrThrow()

        assertEquals(listOf("" to "", "t09" to "Retirado"), entries.map { it.kindId to it.kindLabel })
    }
}

package com.auroraclima.app.questionnaire.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.questionnaire.data.local.GuidanceDatabase
import com.auroraclima.app.questionnaire.data.local.OutcomeRow
import com.auroraclima.app.questionnaire.domain.model.NewSavedOutcome
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RF-31 sobre el almacén cifrado propio del cuestionario, con el contrato P-36
 * del historial: apertura perezosa traducida a Result y minimización — la fila
 * guarda SOLO fecha, rank, label y tags. Room en memoria sin cifrar porque
 * SQLCipher no carga en la JVM del host (mismo criterio que records).
 */
@RunWith(RobolectricTestRunner::class)
class LocalSavedOutcomeRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, GuidanceDatabase::class.java).build()

    @After
    fun tearDown() {
        database.close()
    }

    private var nextDocId = 0

    /** Fuente determinista de nombres de documento (ADR 2026-10-06). */
    private fun repositoryOf(source: GuidanceDatabaseSource) =
        LocalSavedOutcomeRepository(GuidanceStoreAccess(source)) { "doc-${++nextDocId}" }

    @Test
    fun `guarda fechado y devuelve el minimo consultable`() = runTest {
        val repository = repositoryOf { database }
        val before = System.currentTimeMillis()

        val saved = repository.save(
            NewSavedOutcome(rank = 3, label = "Es importante que busques apoyo pronto", tags = listOf("a", "b"))
        )
        val outcomes = repository.outcomes().getOrThrow()

        assertTrue(saved.isSuccess)
        assertEquals(1, outcomes.size)
        assertEquals(3, outcomes[0].rank)
        assertEquals("Es importante que busques apoyo pronto", outcomes[0].label)
        assertEquals(listOf("a", "b"), outcomes[0].tags)
        assertTrue(outcomes[0].savedAt.toEpochMilli() >= before)
        assertEquals(outcomes[0], repository.outcome(outcomes[0].id).getOrThrow())
    }

    @Test
    fun `el listado va del mas reciente al mas antiguo con desempate por id`() = runTest {
        val repository = repositoryOf { database }
        // Fechas controladas directamente en la tabla para fijar el orden.
        database.outcomeDao().insert(OutcomeRow(savedAt = 100, rank = 1, label = "viejo", tags = "[]"))
        database.outcomeDao().insert(OutcomeRow(savedAt = 300, rank = 2, label = "reciente", tags = "[]"))
        database.outcomeDao().insert(OutcomeRow(savedAt = 300, rank = 3, label = "empate tardio", tags = "[]"))

        val outcomes = repository.outcomes().getOrThrow()

        assertEquals(listOf("empate tardio", "reciente", "viejo"), outcomes.map { it.label })
    }

    @Test
    fun `eliminar quita solo el guardado elegido`() = runTest {
        val repository = repositoryOf { database }
        repository.save(NewSavedOutcome(rank = 1, label = "uno", tags = emptyList()))
        repository.save(NewSavedOutcome(rank = 2, label = "dos", tags = emptyList()))
        val victim = repository.outcomes().getOrThrow().first { it.label == "uno" }

        val deleted = repository.delete(victim.id)

        assertTrue(deleted.isSuccess)
        assertEquals(listOf("dos"), repository.outcomes().getOrThrow().map { it.label })
        assertNull(repository.outcome(victim.id).getOrThrow())
    }

    @Test
    fun `una fila con tags ilegibles se lee sin etiquetas y no tumba el listado`() = runTest {
        val repository = repositoryOf { database }
        database.outcomeDao().insert(OutcomeRow(savedAt = 100, rank = 2, label = "tolerante", tags = "no-json"))

        val outcomes = repository.outcomes().getOrThrow()

        assertEquals(1, outcomes.size)
        assertEquals(emptyList<String>(), outcomes[0].tags)
    }

    @Test
    fun `un fallo de apertura se traduce a Result y el siguiente uso reintenta`() = runTest {
        // Contrato P-36: la excepción de la fuente (blob corrupto, ADR D6)
        // jamás cruza como excepción hacia arriba.
        var fail = true
        val repository = repositoryOf {
            if (fail) throw IllegalStateException() else database
        }

        assertTrue(repository.outcomes().isFailure)
        fail = false
        assertTrue(repository.outcomes().isSuccess)
    }
}

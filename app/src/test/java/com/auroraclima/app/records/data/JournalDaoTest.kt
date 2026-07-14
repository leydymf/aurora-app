package com.auroraclima.app.records.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Consultas del historial (RF-32, RF-34 criterio 1) sobre una BD Room en
 * memoria SIN cifrar: SQLCipher no publica binarios para la JVM del host
 * (ADR 2026-07-10), así que el DAO se prueba aquí y el cifrado en reposo del
 * mismo esquema se verifica instrumentado (EncryptedDatabaseFactoryTest).
 */
@RunWith(RobolectricTestRunner::class)
class JournalDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
    private val dao = database.journalDao()

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `lista del mas reciente al mas antiguo por dia del hecho`() = runTest {
        dao.insert(row(day = 100, createdAt = 5, detail = "antiguo"))
        dao.insert(row(day = 300, createdAt = 1, detail = "reciente"))
        dao.insert(row(day = 200, createdAt = 9, detail = "medio"))

        val details = dao.entries().map { it.detail }

        assertEquals(listOf("reciente", "medio", "antiguo"), details)
    }

    @Test
    fun `a igual dia el creado mas tarde va primero`() = runTest {
        dao.insert(row(day = 100, createdAt = 1, detail = "primero"))
        dao.insert(row(day = 100, createdAt = 2, detail = "segundo"))

        val details = dao.entries().map { it.detail }

        assertEquals(listOf("segundo", "primero"), details)
    }

    @Test
    fun `recupera un registro por su id`() = runTest {
        val id = dao.insert(row(day = 100, createdAt = 1, detail = "buscado"))
        dao.insert(row(day = 200, createdAt = 2, detail = "otro"))

        assertEquals("buscado", dao.entryById(id)?.detail)
    }

    @Test
    fun `un id inexistente devuelve null`() = runTest {
        assertNull(dao.entryById(99))
    }

    private fun row(day: Long, createdAt: Long, detail: String) = JournalEntryRow(
        day = day,
        kind = "physical",
        detail = detail,
        createdAt = createdAt
    )
}

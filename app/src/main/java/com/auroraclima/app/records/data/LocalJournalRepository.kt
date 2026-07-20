package com.auroraclima.app.records.data

import com.auroraclima.app.records.data.local.JournalDao
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.domain.repository.JournalRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Historial personal sobre la BD cifrada central (RF-32, RF-34; RNF-01).
 *
 * Cumple P-36 por construcción: (a) la BD se construye PEREZOSAMENTE aquí
 * dentro, en el primer uso y bajo un dispatcher de I/O — nunca en la inyección
 * de arranque, donde una excepción sería crash sin traducción y el I/O de
 * `obtainPassphrase()` violaría StrictMode en el hilo principal; (b) TODA
 * operación (incluida esa apertura) queda envuelta y traducida a [Result] —
 * el lanzamiento deliberado ante blob corrupto (ADR D6) llega a la UI solo
 * como error genérico (§3). Un fallo de apertura no se memoriza: el siguiente
 * uso reintenta abrir.
 */
@Singleton
class LocalJournalRepository @Inject constructor(
    private val databaseSource: JournalDatabaseSource
) : JournalRepository {
    private val databaseLock = Mutex()
    private var database: JournalDatabase? = null

    override suspend fun add(entry: NewJournalEntry): Result<Long> = withDatabase { dao ->
        dao.insert(
            JournalEntryRow(
                day = entry.occurredOn.toEpochDay(),
                kind = entry.kind.storageCode,
                detail = entry.detail,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun entries(): Result<List<JournalEntry>> = withDatabase { dao ->
        dao.entries().mapNotNull { it.toDomain() }
    }

    override suspend fun entry(id: Long): Result<JournalEntry?> = withDatabase { dao ->
        dao.entryById(id)?.toDomain()
    }

    override suspend fun update(entry: JournalEntry): Result<Unit> = withDatabase { dao ->
        dao.update(
            id = entry.id,
            day = entry.occurredOn.toEpochDay(),
            kind = entry.kind.storageCode,
            detail = entry.detail
        )
    }

    override suspend fun delete(id: Long): Result<Unit> = withDatabase { dao ->
        dao.deleteById(id)
    }

    private suspend fun <T> withDatabase(block: suspend (JournalDao) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block(openDatabase().journalDao()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun openDatabase(): JournalDatabase = databaseLock.withLock {
        database ?: databaseSource.open().also { database = it }
    }

    /**
     * Cierra la conexión perezosa (RF-20): borrar el archivo de la BD exige que
     * no esté abierto. No se memoriza nada: un uso posterior reabriría desde
     * cero, como tras una instalación limpia.
     */
    suspend fun close(): Unit = databaseLock.withLock {
        database?.close()
        database = null
    }

    /** Un código de tipo desconocido (esquema futuro) se omite en vez de romper la lista. */
    private fun JournalEntryRow.toDomain(): JournalEntry? {
        val kind = EntryKind.fromStorageCode(kind) ?: return null
        return JournalEntry(
            id = id,
            occurredOn = LocalDate.ofEpochDay(day),
            kind = kind,
            detail = detail
        )
    }
}

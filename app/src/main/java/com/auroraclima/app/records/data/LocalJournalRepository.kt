package com.auroraclima.app.records.data

import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.domain.repository.JournalRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Historial personal sobre la BD cifrada central (RF-32, RF-34; RNF-01).
 *
 * El contrato P-36 (apertura perezosa + traducción a Result) vive en
 * [JournalStoreAccess], compartido con los adjuntos de RF-33.
 */
@Singleton
class LocalJournalRepository @Inject constructor(
    private val storeAccess: JournalStoreAccess
) : JournalRepository {
    override suspend fun add(entry: NewJournalEntry): Result<Long> = storeAccess.withDatabase { db ->
        db.journalDao().insert(
            JournalEntryRow(
                day = entry.occurredOn.toEpochDay(),
                kind = entry.kind.storageCode,
                detail = entry.detail,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun entries(): Result<List<JournalEntry>> = storeAccess.withDatabase { db ->
        db.journalDao().entries().mapNotNull { it.toDomain() }
    }

    override suspend fun entry(id: Long): Result<JournalEntry?> = storeAccess.withDatabase { db ->
        db.journalDao().entryById(id)?.toDomain()
    }

    override suspend fun update(entry: JournalEntry): Result<Unit> = storeAccess.withDatabase { db ->
        db.journalDao().update(
            id = entry.id,
            day = entry.occurredOn.toEpochDay(),
            kind = entry.kind.storageCode,
            detail = entry.detail
        )
    }

    override suspend fun delete(id: Long): Result<Unit> = storeAccess.withDatabase { db ->
        db.journalDao().deleteById(id)
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

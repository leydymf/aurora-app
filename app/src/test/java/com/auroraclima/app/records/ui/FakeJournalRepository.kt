package com.auroraclima.app.records.ui

import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.domain.repository.JournalRepository

/** Fake del historial para los ViewModels (guía §5: fake antes que mock de framework). */
class FakeJournalRepository : JournalRepository {
    val added = mutableListOf<NewJournalEntry>()
    val updated = mutableListOf<JournalEntry>()
    val deleted = mutableListOf<Long>()

    /** null = éxito con id auto-incremental; no-null fuerza ese resultado. */
    var addResult: Result<Long>? = null
    var entriesResult: Result<List<JournalEntry>> = Result.success(emptyList())
    var entryResult: Result<JournalEntry?> = Result.success(null)
    var updateResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var entriesCalls = 0
        private set
    private var nextId = 1L

    override suspend fun add(entry: NewJournalEntry): Result<Long> {
        addResult?.let { return it }
        added += entry
        return Result.success(nextId++)
    }

    override suspend fun entries(): Result<List<JournalEntry>> {
        entriesCalls++
        return entriesResult
    }

    override suspend fun entry(id: Long): Result<JournalEntry?> = entryResult

    override suspend fun update(entry: JournalEntry): Result<Unit> {
        updateResult.onSuccess { updated += entry }
        return updateResult
    }

    /** Devuelve el nombre del documento en la nube, como el repositorio real. */
    var deleteDocId: String = "doc-$DEFAULT_DOC"

    override suspend fun delete(id: Long): Result<String> {
        deleteResult.onSuccess { deleted += id }
        return deleteResult.map { deleteDocId }
    }

    private companion object {
        const val DEFAULT_DOC = "1"
    }
}

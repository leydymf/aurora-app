package com.auroraclima.app.records.ui

import com.auroraclima.app.records.domain.model.JournalEntry
import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.domain.repository.JournalRepository

/** Fake del historial para los ViewModels (guía §5: fake antes que mock de framework). */
class FakeJournalRepository : JournalRepository {
    val added = mutableListOf<NewJournalEntry>()
    var addResult: Result<Unit> = Result.success(Unit)
    var entriesResult: Result<List<JournalEntry>> = Result.success(emptyList())
    var entryResult: Result<JournalEntry?> = Result.success(null)
    var entriesCalls = 0
        private set

    override suspend fun add(entry: NewJournalEntry): Result<Unit> {
        if (addResult.isSuccess) added += entry
        return addResult
    }

    override suspend fun entries(): Result<List<JournalEntry>> {
        entriesCalls++
        return entriesResult
    }

    override suspend fun entry(id: Long): Result<JournalEntry?> = entryResult
}

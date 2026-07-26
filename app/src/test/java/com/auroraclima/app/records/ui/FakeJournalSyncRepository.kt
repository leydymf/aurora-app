package com.auroraclima.app.records.ui

import com.auroraclima.app.records.domain.repository.JournalSyncRepository

/** Fake de la sync para los casos de uso/ViewModels (guía §5). */
class FakeJournalSyncRepository : JournalSyncRepository {
    val pushed = mutableListOf<Long>()
    val removed = mutableListOf<Long>()
    var pushResult: Result<Unit> = Result.success(Unit)
    var removeResult: Result<Unit> = Result.success(Unit)

    override suspend fun push(entryId: Long): Result<Unit> {
        pushed += entryId
        return pushResult
    }

    override suspend fun remove(entryId: Long): Result<Unit> {
        removed += entryId
        return removeResult
    }
}

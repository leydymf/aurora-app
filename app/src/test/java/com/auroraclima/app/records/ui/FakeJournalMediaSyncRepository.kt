package com.auroraclima.app.records.ui

import com.auroraclima.app.records.domain.repository.JournalMediaSyncRepository

/** Fake de la sync de evidencias para los casos de uso/ViewModels (guía §5). */
class FakeJournalMediaSyncRepository : JournalMediaSyncRepository {
    val pushed = mutableListOf<Long>()
    var pushResult: Result<Unit> = Result.success(Unit)
    var restoreCalls = 0
        private set
    var restoreResult: Result<Unit> = Result.success(Unit)

    override suspend fun push(attachmentId: Long): Result<Unit> {
        pushed += attachmentId
        return pushResult
    }

    override suspend fun restore(): Result<Unit> {
        restoreCalls++
        return restoreResult
    }
}

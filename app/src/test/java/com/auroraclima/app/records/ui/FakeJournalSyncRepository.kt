package com.auroraclima.app.records.ui

import com.auroraclima.app.records.domain.repository.JournalSyncRepository

/** Fake de la sync para los casos de uso/ViewModels (guía §5). */
class FakeJournalSyncRepository : JournalSyncRepository {
    val pushed = mutableListOf<Long>()
    val removed = mutableListOf<String>()
    var pushResult: Result<Unit> = Result.success(Unit)
    var removeResult: Result<Unit> = Result.success(Unit)

    override suspend fun push(entryId: Long): Result<Unit> {
        pushed += entryId
        return pushResult
    }

    override suspend fun remove(docId: String): Result<Unit> {
        removed += docId
        return removeResult
    }

    var restoreCalls = 0
        private set
    var restoreResult: Result<Unit> = Result.success(Unit)

    override suspend fun restore(): Result<Unit> {
        restoreCalls++
        return restoreResult
    }

    var reconcileCalls = 0
        private set

    /** Ids locales que la reconciliación declara eliminados desde la nube. */
    var reconcileResult: Result<List<Long>> = Result.success(emptyList())

    override suspend fun reconcile(): Result<List<Long>> {
        reconcileCalls++
        return reconcileResult
    }
}

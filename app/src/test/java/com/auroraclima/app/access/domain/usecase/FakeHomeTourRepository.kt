package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.repository.HomeTourRepository

/** Recorrido pendiente en memoria para las pruebas de los ViewModels. */
class FakeHomeTourRepository(var pending: Boolean = false) : HomeTourRepository {
    var failWrites = false
    var failReads = false
    var markCalls = 0
        private set
    var consumeCalls = 0
        private set

    override suspend fun markPending(): Result<Unit> {
        markCalls++
        if (failWrites) return Result.failure(IllegalStateException())
        pending = true
        return Result.success(Unit)
    }

    override suspend fun consumePending(): Result<Boolean> {
        consumeCalls++
        if (failReads) return Result.failure(IllegalStateException())
        val wasPending = pending
        pending = false
        return Result.success(wasPending)
    }
}

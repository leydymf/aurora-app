package com.auroraclima.app.access.domain.usecase

import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.access.domain.repository.WalkthroughRepository

/** Marca de la guía en memoria para las pruebas de los ViewModels y de los casos de uso. */
class FakeWalkthroughRepository(var stored: WalkthroughMark? = null) : WalkthroughRepository {
    var failWrites = false

    /** Si la llave ya está (falso: tras la nube, antes del reingreso silencioso). */
    var ready = true
    var failReads = false
    var markCalls = 0
        private set
    var consumeCalls = 0
        private set

    /** Las marcas escritas, en orden (para ver armar y desarmar). */
    val history = mutableListOf<WalkthroughMark>()

    override suspend fun mark(mark: WalkthroughMark): Result<Unit> {
        markCalls++
        if (failWrites) return Result.failure(IllegalStateException())
        stored = mark
        history += mark
        return Result.success(Unit)
    }

    override suspend fun hasMark(): Boolean = stored != null

    override suspend fun canOpen(): Boolean = ready

    override suspend fun consume(): Result<WalkthroughMark?> {
        consumeCalls++
        if (failReads) return Result.failure(IllegalStateException())
        if (!ready) return Result.success(null)
        val was = stored
        stored = null
        return Result.success(was)
    }
}

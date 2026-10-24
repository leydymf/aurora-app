package com.auroraclima.app.core.security

import kotlinx.coroutines.CompletableDeferred

/**
 * Presencia del código de acceso simulada (hallazgo A de v0.3.2). Por defecto el
 * código existe: es el estado de toda pantalla posterior al primer uso.
 */
internal class FakeAccessCodePresence(var codeExists: Boolean = true) : AccessCodePresence {
    var failOnRead = false

    /** Suspende SOLO la siguiente lectura hasta completarse (pruebas de carreras). */
    var gateNextRead: CompletableDeferred<Unit>? = null

    override suspend fun isSet(): Result<Boolean> {
        gateNextRead?.let { gate ->
            gateNextRead = null
            gate.await()
        }
        return if (failOnRead) Result.failure(IllegalStateException()) else Result.success(codeExists)
    }
}

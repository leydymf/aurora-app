package com.auroraclima.app.records.data

import com.auroraclima.app.core.network.networkResult
import com.auroraclima.app.core.security.RemoteAccountDataEraser
import com.auroraclima.app.records.data.remote.JournalPurgeRemoteSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aporte remoto de records a la eliminación total (RF-20): purga confirmada de
 * los registros en la nube. Su fallo detiene la eliminación aguas arriba —
 * nada local se ha perdido y reintentar es idempotente.
 */
@Singleton
class JournalRemoteEraser @Inject constructor(
    private val purgeRemoteSource: JournalPurgeRemoteSource
) : RemoteAccountDataEraser {
    override suspend fun erase(): Result<Unit> = networkResult { purgeRemoteSource.deleteAll() }
}

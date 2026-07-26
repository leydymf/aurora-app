package com.auroraclima.app.core.security

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte remoto de core/security a la eliminación total (RF-20): borra el
 * documento de llaves del respaldo (RF-39) esperando al servidor — tras
 * eliminar la cuenta no debe quedar en la nube ni siquiera el sobre envuelto
 * (indescifrable, pero material de la usuaria al fin).
 */
@Singleton
class SyncKeyRemoteEraser @Inject constructor(
    private val remote: KeyEnvelopeRemoteSource
) : RemoteAccountDataEraser {
    override suspend fun erase(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Result.success(remote.delete())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}

package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.local.GuidanceDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Acceso compartido al almacén cifrado del cuestionario (RF-31), con el
 * contrato P-36 del historial: (a) la BD se abre PEREZOSAMENTE en el primer
 * uso, bajo un dispatcher de I/O, nunca en la inyección de arranque; (b) TODA
 * operación (incluida esa apertura) queda envuelta y traducida a [Result]
 * (CancellationException se relanza). Un fallo de apertura no se memoriza: el
 * siguiente uso reintenta abrir. [close] existe para el borrado de RF-20.
 */
@Singleton
class GuidanceStoreAccess @Inject constructor(
    private val databaseSource: GuidanceDatabaseSource
) {
    private val databaseLock = Mutex()
    private var database: GuidanceDatabase? = null

    suspend fun <T> withDatabase(block: suspend (GuidanceDatabase) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block(openDatabase()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun openDatabase(): GuidanceDatabase = databaseLock.withLock {
        database ?: databaseSource.open().also { database = it }
    }

    /** Cierra la conexión perezosa (RF-20): borrar el archivo exige que no esté abierto. */
    suspend fun close(): Unit = databaseLock.withLock {
        database?.close()
        database = null
    }
}

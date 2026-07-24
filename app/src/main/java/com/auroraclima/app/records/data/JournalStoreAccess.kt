package com.auroraclima.app.records.data

import com.auroraclima.app.records.data.local.JournalDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Acceso compartido a la BD cifrada del historial. Extraído de
 * `LocalJournalRepository` cuando los adjuntos (RF-33) se volvieron el segundo
 * consumidor: una sola conexión perezosa para registros y adjuntos, con el
 * contrato P-36 intacto — (a) la BD se abre PEREZOSAMENTE en el primer uso,
 * bajo un dispatcher de I/O, nunca en la inyección de arranque; (b) TODA
 * operación (incluida esa apertura) queda envuelta y traducida a [Result]
 * (CancellationException se relanza). Un fallo de apertura no se memoriza:
 * el siguiente uso reintenta abrir.
 */
@Singleton
class JournalStoreAccess @Inject constructor(
    private val databaseSource: JournalDatabaseSource
) {
    private val databaseLock = Mutex()
    private var database: JournalDatabase? = null

    suspend fun <T> withDatabase(block: suspend (JournalDatabase) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block(openDatabase()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun openDatabase(): JournalDatabase = databaseLock.withLock {
        database ?: databaseSource.open().also { database = it }
    }

    /**
     * Cierra la conexión perezosa (RF-20): borrar el archivo de la BD exige que
     * no esté abierto. No se memoriza nada: un uso posterior reabriría desde
     * cero, como tras una instalación limpia.
     */
    suspend fun close(): Unit = databaseLock.withLock {
        database?.close()
        database = null
    }
}

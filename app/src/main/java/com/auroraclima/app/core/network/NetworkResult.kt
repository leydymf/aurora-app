package com.auroraclima.app.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ejecuta [block] en el dispatcher de I/O y traduce todo fallo a [Result]:
 * ninguna excepción del SDK cruza hacia la UI (§3) y nada se registra en logs
 * (regla 4 de CLAUDE.md). `CancellationException` se relanza para respetar la
 * cancelación estructurada.
 *
 * Extraído aquí al repetirse por tercera vez el mismo andamiaje sobre Firestore
 * —`content/`, `questionnaire/` y la sync de `records/` (RF-37)— tal como anticipó
 * P-39. La BD local de records mantiene su propio `withDatabase` (es Room, no red).
 */
suspend fun <T> networkResult(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }
}

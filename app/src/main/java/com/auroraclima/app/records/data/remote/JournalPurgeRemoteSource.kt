package com.auroraclima.app.records.data.remote

/**
 * Borrado remoto TOTAL del historial (RF-20). A diferencia de la sync de mejor
 * esfuerzo ([JournalSyncRemoteSource]), aquí se espera la confirmación del
 * servidor: tras eliminar la cuenta el token queda inválido y una mutación
 * apenas encolada sería rechazada — dejaría blobs huérfanos imborrables.
 * Lanza ante cualquier fallo (sin sesión, sin red); quien llama lo traduce.
 */
fun interface JournalPurgeRemoteSource {
    suspend fun deleteAll()
}

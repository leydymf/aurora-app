package com.auroraclima.app.records.data.remote

/**
 * Evidencias cifradas troceadas en Firestore (RF-39, cambio de política D0
 * §5.3): la capa gratuita limita cada documento a 1 MiB, así que el blob
 * sellado de un adjunto viaja partido en trozos. Único conocedor del SDK en el
 * feature para media (mismo patrón que [JournalSyncRemoteSource]).
 *
 * Estructura remota (colecciones planas para que la purga de RF-20 pueda
 * enumerarlas): `/users/{uid}/media/{ref}` (cabecera) y
 * `/users/{uid}/mediaparts/{ref_i}` (trozos). La cabecera se escribe DE
 * ÚLTIMA: es la marca de que el adjunto subió completo — trozos sin cabecera
 * son restos que la restauración ignora y la purga barre.
 */
interface JournalMediaRemoteSource {
    /** Sube los trozos y al final la cabecera (cola durable, mejor esfuerzo — RF-38). */
    suspend fun upload(ref: String, header: EncryptedMediaHeaderDto, parts: List<String>)

    /**
     * Borra la cabecera y los trozos de [ref]. Los trozos se borran A CIEGAS
     * hasta el máximo posible por adjunto: borrar un documento inexistente es
     * un no-op y así la operación entra completa a la cola durable sin
     * necesitar red para consultar cuántos trozos hay.
     */
    suspend fun delete(ref: String)

    /**
     * TODAS las cabeceras, con DTO nulo cuando a la cabecera le falta un campo:
     * el ref siempre viaja para que un documento ilegible no se confunda con
     * uno eliminado (auditoría 2026-10-06).
     */
    suspend fun downloadHeaders(): List<Pair<String, EncryptedMediaHeaderDto?>>

    /** Trozo [index] de [ref] desde el servidor, o null si no existe. */
    suspend fun downloadPart(ref: String, index: Int): String?
}

/**
 * Cabecera de una evidencia en la nube (RNF-01/RNF-04): metadatos SELLADOS con
 * la DEK ([meta]/[iv], JSON con dueño, tipo y fecha — nada legible), el número
 * de trozos (necesario para descargar; no revela contenido) y la versión del
 * formato. El id del documento es el `ref` opaco del almacén local (32 hex
 * aleatorios): ninguna relación visible con registro alguno.
 */
data class EncryptedMediaHeaderDto(
    val meta: String,
    val iv: String,
    val partCount: Int,
    val schemaVersion: Long
)

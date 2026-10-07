package com.auroraclima.app.records.data.remote

/**
 * Fuente remota de la sync del historial: único conocedor del SDK de Firebase en
 * el feature (mismo patrón que `ContentRemoteSource`). Escribe bajo
 * `/users/{uid}/entries/{documentId}`, restringido a la cuenta de la usuaria por
 * las reglas de seguridad (RF-37 criterio 2).
 */
interface JournalSyncRemoteSource {
    /** Crea o sobrescribe el documento cifrado [document] con id [documentId]. */
    suspend fun upload(documentId: String, document: EncryptedEntryDto)

    /** Elimina el documento [documentId] (borrado remoto, RF-36/RF-20). */
    suspend fun delete(documentId: String)

    /**
     * TODOS los documentos de la colección, con DTO nulo cuando al documento le
     * falta algún campo. El id SIEMPRE viaja: la reconciliación borra por
     * ausencia, así que un documento presente pero ilegible no puede
     * confundirse con uno eliminado (auditoría 2026-10-06).
     */
    suspend fun downloadAll(): List<Pair<String, EncryptedEntryDto?>>
}

/**
 * Lo único que viaja a Firestore por cada registro (RF-37, RNF-01/RNF-04): el
 * contenido cifrado en el cliente y su nonce, más la versión del formato. Ni
 * `day`, ni `kind`, ni `detail` aparecen como campos legibles — van dentro de
 * [ciphertext]. El id del documento es el único metadato y no revela
 * contenido: desde el ADR 2026-10-06 son 32 hex aleatorios para lo creado a
 * partir de esa versión, y el id numérico de siempre para lo ya publicado.
 */
data class EncryptedEntryDto(
    val ciphertext: String,
    val iv: String,
    val schemaVersion: Long
)

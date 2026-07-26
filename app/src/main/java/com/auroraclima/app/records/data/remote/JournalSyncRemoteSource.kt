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
     * Todos los documentos cifrados de la cuenta, DESDE EL SERVIDOR
     * (restauración RF-39): la caché local de una instalación nueva está vacía
     * y la restauración debe ver el estado real. Lanza sin red — la
     * reconciliación lo traduce a fallo silencioso y reintenta al siguiente
     * login.
     */
    suspend fun downloadAll(): List<Pair<String, EncryptedEntryDto>>
}

/**
 * Lo único que viaja a Firestore por cada registro (RF-37, RNF-01/RNF-04): el
 * contenido cifrado en el cliente y su nonce, más la versión del formato. Ni
 * `day`, ni `kind`, ni `detail` aparecen como campos legibles — van dentro de
 * [ciphertext]. El id del documento (un entero de secuencia local) es el único
 * metadato y no revela contenido.
 */
data class EncryptedEntryDto(
    val ciphertext: String,
    val iv: String,
    val schemaVersion: Long
)

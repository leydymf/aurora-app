package com.auroraclima.app.records.domain.repository

import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentContent
import com.auroraclima.app.records.domain.model.JournalAttachment

/**
 * Adjuntos protegidos del historial (RF-33). Todo devuelve [Result]: la BD
 * cifrada, el almacén y la lectura del contenido elegido pueden fallar y esa
 * excepción jamás cruza a la UI (§3); se muestra el mensaje genérico.
 *
 * Los orígenes elegidos por la usuaria llegan como URI en String (el dominio
 * no importa tipos de Android); la capa data los resuelve. La importación
 * SIEMPRE deja una copia cifrada propia — el original de galería o archivos
 * no se toca (política D0 §5.2) — y a las imágenes les elimina TODOS los
 * metadatos (GPS incluido) re-codificándolas antes de cifrar.
 */
interface JournalAttachmentRepository {
    /** Importa una imagen elegida (galería): sanea metadatos, cifra y asocia a [owner]. */
    suspend fun importImage(uri: String, owner: Long): Result<JournalAttachment>

    /** Importa un archivo existente (PDF/audio): valida lista blanca y tamaño, cifra y asocia. */
    suspend fun importDocument(uri: String, owner: Long): Result<JournalAttachment>

    /** Prepara una captura de cámara (RF-33): devuelve el URI privado para EXTRA_OUTPUT. */
    suspend fun beginCapture(): Result<String>

    /** Importa la captura pendiente (sanea, cifra, borra el temporal) y la asocia a [owner]. */
    suspend fun importCapture(owner: Long): Result<JournalAttachment>

    /** Descarta una captura cancelada: elimina el temporal sin dejar rastro. */
    suspend fun discardCapture()

    /** Adjuntos de un registro (o en preparación con [AttachmentPolicy.STAGED_OWNER]). */
    suspend fun attachments(owner: Long): Result<List<JournalAttachment>>

    /** Contadores por registro para el listado (P18); los en preparación no cuentan. */
    suspend fun counts(): Result<Map<Long, Int>>

    /** Contenido descifrado para el visor interno; null si el adjunto ya no existe. */
    suspend fun content(id: Long): Result<AttachmentContent?>

    /** Bytes descifrados de cualquier adjunto para exportarlo (RF-45), sin temporal; null si ya no existe. */
    suspend fun exportable(id: Long): Result<AttachmentBytes?>

    /** El guardado de un registro nuevo reclama sus adjuntos en preparación. */
    suspend fun claim(ids: List<Long>, owner: Long): Result<Unit>

    /** Elimina un adjunto: su fila y su archivo cifrado. */
    suspend fun delete(id: Long): Result<Unit>

    /** Elimina todos los adjuntos de un registro (RF-36 criterio 2). */
    suspend fun deleteForOwner(owner: Long): Result<Unit>

    /** Barrido de huérfanos: en preparación abandonados o restos de borrados interrumpidos. */
    suspend fun sweepOrphans(): Result<Unit>
}

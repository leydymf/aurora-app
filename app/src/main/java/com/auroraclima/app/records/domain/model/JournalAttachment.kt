package com.auroraclima.app.records.domain.model

/**
 * Un adjunto protegido de un registro (RF-33): imagen, documento o audio,
 * cifrado en el almacén local. Solo lo mínimo que la UI necesita para pintar
 * el chip y decidir el visor (RNF-04): el contenido se pide aparte y los
 * metadatos del original (nombre, ruta, EXIF) NUNCA se conservan.
 */
data class JournalAttachment(
    val id: Long,
    val kind: AttachmentKind
)

/** Familia del adjunto; decide ícono y visor. Se deriva del MIME al importar. */
enum class AttachmentKind {
    IMAGE,
    DOCUMENT,
    AUDIO;

    companion object {
        /** Familia de un MIME almacenado; desconocido (esquema futuro) → null y la fila se omite. */
        fun fromMime(mime: String): AttachmentKind? = when {
            mime.startsWith("image/") -> IMAGE
            mime == "application/pdf" -> DOCUMENT
            mime.startsWith("audio/") -> AUDIO
            else -> null
        }
    }
}

/**
 * Contenido descifrado de un adjunto para el visor interno (RF-33 criterio 2:
 * visibles SOLO dentro de la app; el visor vive en el grafo seguro, RNF-02).
 */
sealed interface AttachmentContent {
    /** Bytes de la imagen saneada (JPEG ya re-codificado, sin metadatos). */
    data class Image(val bytes: ByteArray) : AttachmentContent {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Image && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /**
     * Ruta de una copia descifrada TEMPORAL en la caché privada, para el visor
     * de PDF (PdfRenderer exige un descriptor posicionable). Quien la abre debe
     * eliminar el archivo inmediatamente después de abrir su descriptor — el
     * descriptor sigue vivo sin entrada en el sistema de archivos.
     */
    data class Document(val path: String) : AttachmentContent
}

/**
 * Política del almacén de evidencias (RF-33; límites justificados en el ADR):
 * lista blanca de tipos, tope de tamaño por archivo y tope de adjuntos por
 * registro. Lógica pura, probada por unidad.
 */
object AttachmentPolicy {
    /** Marca de un adjunto en preparación: su registro aún no existe (creación en curso). */
    const val STAGED_OWNER: Long = 0

    /** Máximo de adjuntos por registro: mantiene manejable la rejilla del mockup P20 y acota el disco. */
    const val MAX_PER_ENTRY: Int = 10

    /** Tope para PDF/audio/documentos; el exceso se rechaza con mensaje genérico. */
    const val MAX_DOCUMENT_BYTES: Long = 10L * 1024 * 1024

    /** Las imágenes se re-escalan a este lado mayor antes de cifrar (gama baja, RNF-07). */
    const val IMAGE_MAX_DIMENSION: Int = 2048

    /** Calidad JPEG de la re-codificación (la que además elimina los metadatos). */
    const val IMAGE_JPEG_QUALITY: Int = 85

    /** MIME con el que se almacena toda imagen importada (siempre re-codificada). */
    const val IMPORTED_IMAGE_MIME: String = "image/jpeg"

    /**
     * Lista blanca de la vía «archivo existente» (política D0 §5.1.iii): PDF y
     * audios comunes. Sin video (peso) ni formatos exóticos; las imágenes van
     * por su propia vía con re-codificación.
     */
    val allowedDocumentTypes: Set<String> = setOf(
        "application/pdf",
        "audio/mpeg",
        "audio/mp4",
        "audio/aac",
        "audio/ogg",
        "audio/wav",
        "audio/x-wav",
        "audio/flac",
        "audio/amr",
        "audio/3gpp"
    )

    /** ¿El MIME reportado por el selector está en la lista blanca? */
    fun isAllowedDocumentType(mime: String?): Boolean = mime != null && mime in allowedDocumentTypes

    /** ¿Un tamaño declarado o contado respeta el tope? (desconocido = se cuenta al copiar). */
    fun isWithinDocumentSize(sizeBytes: Long): Boolean = sizeBytes in 1..MAX_DOCUMENT_BYTES

    /** ¿Cabe un adjunto más en el registro? */
    fun hasCapacity(currentCount: Int): Boolean = currentCount < MAX_PER_ENTRY
}

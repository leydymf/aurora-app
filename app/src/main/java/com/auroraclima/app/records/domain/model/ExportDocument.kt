package com.auroraclima.app.records.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Lo que va DENTRO del PDF exportado (RF-45 criterio 3): por cada registro
 * elegido, fecha del hecho, tipo y relato, y sus adjuntos solo si la usuaria
 * pidió incluirlos — imágenes y documentos PDF como páginas, audios como
 * archivos incrustados en el propio PDF. Modelo puro: la capa data lo
 * convierte en páginas.
 *
 * [ExportEntry.kindLabel] es el nombre VIVO del tema publicado (cierre P-68),
 * la instantánea guardada si el tema fue retirado, o null si no hay nada —
 * quien escribe el PDF imprime entonces el rótulo neutro.
 */
data class ExportDocument(
    val createdOn: LocalDate,
    val entries: List<ExportEntry>,
    val includesAttachments: Boolean
)

data class ExportEntry(
    val occurredOn: LocalDate,
    val kindLabel: String?,
    val detail: String,
    /** Adjuntos descifrados que se incluyen (bytes + MIME); vacío cuando no se incluyen. */
    val attachments: List<AttachmentBytes>,
    /** Familias de los adjuntos que se citan sin incluirse (interruptor apagado). */
    val omittedAttachments: List<AttachmentKind>
)

/**
 * Resumen previo a exportar (RF-45 criterio 2): cuántos registros, cuáles y
 * cuántos adjuntos podrían incluirse. No descifra nada: se arma con la lista
 * de adjuntos, no con su contenido.
 */
data class ExportSummary(
    val rows: List<ExportSummaryRow>,
    val attachmentCount: Int,
    /** Suma de tamaños de los adjuntos elegidos; decide si caben en un solo archivo. */
    val attachmentBytes: Long = 0
) {
    val entryCount: Int
        get() = rows.size

    /** Con adjuntos demasiado pesados el interruptor no se ofrece: habría que exportar menos registros a la vez. */
    val canIncludeAttachments: Boolean
        get() = attachmentCount > 0 && ExportPolicy.fitsInOneFile(attachmentBytes)
}

data class ExportSummaryRow(
    val id: Long,
    val occurredOn: LocalDate,
    val kindId: String,
    val kindLabel: String
)

/**
 * Política de la exportación (RF-45). Objeto sin estado, mismo criterio que
 * [AttachmentPolicy] (excepción §3 registrada en el ADR de RF-33).
 */
object ExportPolicy {
    /**
     * Largo mínimo de la contraseña del archivo (criterio 4). El cifrado
     * estándar del PDF deriva la llave sin factor de costo, así que fuera del
     * espacio protegido la contraseña es la única barrera: 10 caracteres
     * (auditoría RF-45 H-4; el residual sigue en P-76).
     */
    const val MIN_PASSWORD_LENGTH: Int = 10

    /** Lado mayor de las imágenes dentro del PDF: ancho útil de una página A4 a ~150 ppp. */
    const val IMAGE_MAX_DIMENSION: Int = 1280

    /** Calidad JPEG de la re-codificación para el PDF (acota memoria y tamaño del archivo). */
    const val IMAGE_JPEG_QUALITY: Int = 80

    /** MIME del archivo exportado. */
    const val MIME_TYPE: String = "application/pdf"

    /**
     * Tope de bytes de adjuntos por exportación (auditoría RF-45 A-1): los
     * adjuntos se descifran a memoria y el PDF se arma en memoria principal;
     * en gama baja el proceso tiene 128–256 MB de heap. Por encima, la app
     * pide exportar menos registros a la vez en lugar de arriesgar la caída.
     */
    const val MAX_TOTAL_ATTACHMENT_BYTES: Long = 32L * 1024 * 1024

    /** ¿La suma de adjuntos cabe en una sola exportación? */
    fun fitsInOneFile(totalBytes: Long): Boolean = totalBytes <= MAX_TOTAL_ATTACHMENT_BYTES

    /** ¿La contraseña cumple el largo mínimo? */
    fun isPasswordLongEnough(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    /**
     * Nombre del archivo incrustado en el PDF para un audio: numerado a nivel
     * de DOCUMENTO (no por registro) para que dos registros con audio no
     * colisionen, y con la extensión que corresponde al MIME de la lista
     * blanca de [AttachmentPolicy.allowedDocumentTypes].
     */
    fun embeddedFileName(mime: String, index: Int): String = "audio_$index." + extensionFor(mime)

    private fun extensionFor(mime: String): String = when (mime) {
        "audio/mpeg" -> "mp3"
        "audio/mp4" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg" -> "ogg"
        "audio/wav", "audio/x-wav" -> "wav"
        "audio/flac" -> "flac"
        "audio/amr" -> "amr"
        "audio/3gpp" -> "3gp"
        else -> "bin"
    }

    /**
     * Nombre neutro del archivo (criterio 4): nada en él sugiere su contenido;
     * la fecha ayuda a la usuaria a reconocerlo entre varios.
     */
    fun fileName(day: LocalDate): String = "documento_" + day.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".pdf"
}

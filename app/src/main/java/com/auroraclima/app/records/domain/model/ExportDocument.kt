package com.auroraclima.app.records.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Lo que va DENTRO del PDF exportado (RF-45 criterio 3): por cada registro
 * elegido, fecha del hecho, tipo y relato, y sus imágenes solo si la usuaria
 * pidió incluirlas. Modelo puro: la capa data lo convierte en páginas.
 *
 * [ExportEntry.kindLabel] es el nombre VIVO del tema publicado (cierre P-68),
 * la instantánea guardada si el tema fue retirado, o null si no hay nada —
 * quien escribe el PDF imprime entonces el rótulo neutro.
 */
data class ExportDocument(
    val createdOn: LocalDate,
    val entries: List<ExportEntry>,
    val includesImages: Boolean
)

data class ExportEntry(
    val occurredOn: LocalDate,
    val kindLabel: String?,
    val detail: String,
    /** JPEG saneados y descifrados; vacío cuando no se incluyen imágenes. */
    val images: List<ByteArray>,
    /** Adjuntos que se citan sin incrustarse: documentos y audios, y las imágenes omitidas. */
    val omittedAttachments: List<AttachmentKind>
) {
    override fun equals(other: Any?): Boolean = this === other ||
        (
            other is ExportEntry &&
                occurredOn == other.occurredOn &&
                kindLabel == other.kindLabel &&
                detail == other.detail &&
                images.size == other.images.size &&
                images.zip(other.images).all { (a, b) -> a.contentEquals(b) } &&
                omittedAttachments == other.omittedAttachments
            )

    override fun hashCode(): Int = listOf(occurredOn, kindLabel, detail, images.size, omittedAttachments).hashCode()
}

/**
 * Resumen previo a exportar (RF-45 criterio 2): cuántos registros, cuáles y
 * cuántas imágenes podrían incluirse. No descifra nada: se arma con la lista
 * de adjuntos, no con su contenido.
 */
data class ExportSummary(
    val rows: List<ExportSummaryRow>,
    val imageCount: Int
) {
    val entryCount: Int
        get() = rows.size
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

    /** ¿La contraseña cumple el largo mínimo? */
    fun isPasswordLongEnough(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    /**
     * Nombre neutro del archivo (criterio 4): nada en él sugiere su contenido;
     * la fecha ayuda a la usuaria a reconocerlo entre varios.
     */
    fun fileName(day: LocalDate): String = "documento_" + day.format(DateTimeFormatter.ISO_LOCAL_DATE) + ".pdf"
}

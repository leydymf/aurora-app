package com.auroraclima.app.records.data.export

import android.content.Context
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.auroraclima.app.records.domain.repository.ExportDocumentWriter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escritor del PDF con PdfBox-Android (ADR RF-45): páginas A4 con Helvetica
 * estándar (sin incrustar fuente; WinAnsi cubre tildes, ñ, ¿ y ¡ — lo que no
 * cabe se sustituye por «?»), una portada breve y UN registro por página con
 * jerarquía visual clara: banda «Registro n de N» con la fecha, tipo, relato y
 * adjuntos en cajas que anuncian qué viene (imagen enmarcada, páginas de un
 * PDF anexadas a continuación con cabecera propia, audio incrustado como
 * archivo del PDF). El documento se protege con AES-256 (ISO 32000-2,
 * revisión 6) usando la contraseña de la usuaria como contraseña de usuario y
 * de propietaria; el proveedor criptográfico es el de la plataforma
 * (javax.crypto), BouncyCastle no entra al APK.
 *
 * Los rótulos salen de recursos con claves neutras; el contenido real va
 * dentro del archivo, que es exactamente lo que RF-45 pide. Nada se registra
 * en logs; las llamadas internas de la librería a `android.util.Log` se
 * eliminan del release por R8 (proguard-rules.pro) y [BundledFontMapper]
 * evita el escaneo de `/system/fonts`, su índice en caché y la salida a
 * consola del mapeador por defecto. La maquetación vive en [PdfPageLayout].
 */
@Singleton
class PdfBoxExportWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scaler: ExportImageScaler
) : ExportDocumentWriter {
    override fun write(document: ExportDocument, password: String, sink: OutputStream) {
        if (!PDFBoxResourceLoader.isReady()) PDFBoxResourceLoader.init(context.applicationContext)
        BundledFontMapper.install()
        PDDocument().use { pdf ->
            val layout = PdfPageLayout(pdf)
            try {
                writeCover(layout, document)
                document.entries.forEachIndexed { index, entry ->
                    if (index > 0) layout.newPageForEntry()
                    writeEntry(layout, entry, index + 1, document.entries.size)
                }
                val footer = context.getString(R.string.journal_export_pdf_generated, document.createdOn.toLongText())
                layout.finish(context.getString(R.string.journal_export_pdf_title) + " · " + footer) { index, total ->
                    context.getString(R.string.journal_export_pdf_page, index, total)
                }
                pdf.protect(protection(password))
                pdf.save(sink)
            } finally {
                // Las páginas anexadas siguen apuntando a sus documentos de
                // origen hasta el guardado: se cierran al final, pase lo que pase.
                layout.release()
            }
        }
    }

    private fun writeCover(layout: PdfPageLayout, document: ExportDocument) {
        val count = document.entries.size
        val summary = context.resources.getQuantityString(R.plurals.journal_export_summary_count, count, count)
        val dates = document.entries.map { it.occurredOn }
        val range = if (dates.size > 1) {
            val span = context.getString(
                R.string.journal_export_pdf_range,
                dates.min().toLongText(),
                dates.max().toLongText()
            )
            " · $span"
        } else {
            ""
        }
        layout.title(context.getString(R.string.journal_export_pdf_title))
        layout.caption(context.getString(R.string.journal_export_pdf_generated, document.createdOn.toLongText()))
        layout.caption(summary + range)
        layout.rule()
    }

    private fun writeEntry(layout: PdfPageLayout, entry: ExportEntry, position: Int, total: Int) {
        val bandLabel = context.getString(R.string.journal_export_pdf_entry_band, position, total)
        layout.band(bandLabel, entry.occurredOn.toLongText())
        layout.labeled(
            context.getString(R.string.journal_export_pdf_kind),
            entry.kindLabel ?: context.getString(R.string.journal_kind_none)
        )
        layout.sectionLabel(context.getString(R.string.journal_export_pdf_detail))
        layout.paragraph(entry.detail)
        val attachmentCount = entry.attachments.size + entry.omittedAttachments.size
        if (attachmentCount == 0) return
        layout.sectionLabel(context.getString(R.string.journal_export_pdf_attachments, attachmentCount))
        val ordinals = Ordinals()
        // Los documentos van al final: sus páginas anexadas cierran el registro
        // y lo que siga (otro documento) retoma con una banda de continuación.
        entry.attachments.sortedBy { ATTACHMENT_ORDER.indexOf(it.kind) }.forEach { attachment ->
            writeAttachment(layout, attachment, ordinals.next(attachment.kind), position, bandLabel)
        }
        entry.omittedAttachments.forEach { kind -> layout.note(omittedLine(kind, ordinals.next(kind)), muted = true) }
        if (entry.omittedAttachments.isNotEmpty()) {
            layout.caption(
                context.getString(R.string.journal_export_pdf_omitted)
            )
        }
    }

    private fun writeAttachment(
        layout: PdfPageLayout,
        attachment: AttachmentBytes,
        ordinal: Int,
        position: Int,
        bandLabel: String
    ) {
        when (attachment.kind) {
            AttachmentKind.IMAGE -> {
                layout.caption(context.getString(R.string.journal_export_pdf_image_n, ordinal, position))
                layout.framedImage(scaler.scale(attachment.bytes))
            }
            AttachmentKind.DOCUMENT -> {
                val source = openForAppend(attachment.bytes)
                if (source == null) {
                    layout.note(context.getString(R.string.journal_export_pdf_document_failed, ordinal), muted = true)
                } else {
                    val pages = source.numberOfPages
                    layout.note(
                        context.getString(R.string.journal_export_pdf_document_n, ordinal, pages),
                        muted = false
                    )
                    layout.appendPages(source, continuation = bandLabel) { index ->
                        context.getString(R.string.journal_export_pdf_appended, position, ordinal, index, pages)
                    }
                }
            }
            AttachmentKind.AUDIO -> {
                // El ordinal visible es por registro; el NOMBRE del archivo es
                // por documento: dos registros con audio no deben colisionar.
                val name = layout.embed(attachment.mime, attachment.bytes)
                layout.note(context.getString(R.string.journal_export_pdf_audio_n, ordinal, name), muted = false)
            }
        }
    }

    /**
     * Abre un PDF adjunto y lo ensaya en seco — anexa todas sus páginas a un
     * documento desechable y lo serializa — antes de tocar el PDF real
     * (auditoría A-3): un árbol de páginas o un stream roto se descubre aquí
     * y el adjunto se cita como «no se pudo incluir», sin dejar páginas a
     * medias. Devuelve el documento abierto (lo cierra `PdfPageLayout.release`).
     */
    private fun openForAppend(bytes: ByteArray): PDDocument? {
        val source = runCatching { PDDocument.load(bytes) }.getOrNull() ?: return null
        val rehearsal = runCatching {
            if (source.numberOfPages == 0) error("empty")
            PDDocument().use { probe ->
                source.pages.forEach { page -> probe.importPruned(page) }
                probe.save(DiscardingOutputStream)
            }
        }
        if (rehearsal.isFailure) {
            runCatching { source.close() }
            return null
        }
        return source
    }

    private fun omittedLine(kind: AttachmentKind, ordinal: Int): String = when (kind) {
        AttachmentKind.IMAGE -> context.getString(R.string.journal_export_pdf_image_omitted, ordinal)
        AttachmentKind.DOCUMENT -> context.getString(R.string.journal_export_pdf_document_omitted, ordinal)
        AttachmentKind.AUDIO -> context.getString(R.string.journal_export_pdf_audio_omitted, ordinal)
    }

    private fun protection(password: String): StandardProtectionPolicy =
        StandardProtectionPolicy(password, password, AccessPermission()).apply {
            encryptionKeyLength = KEY_LENGTH_BITS
            isPreferAES = true
        }

    /** Numeración por familia dentro de un registro («Imagen 2», «Audio adjunto 1»). */
    private class Ordinals {
        private val counts = mutableMapOf<AttachmentKind, Int>()

        fun next(kind: AttachmentKind): Int = ((counts[kind] ?: 0) + 1).also { counts[kind] = it }
    }

    /** Salida que descarta todo: el ensayo solo necesita que la serialización no falle. */
    private object DiscardingOutputStream : OutputStream() {
        override fun write(b: Int) = Unit

        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private fun LocalDate.toLongText(): String = format(LONG_DAY)

    private companion object {
        const val KEY_LENGTH_BITS = 256

        /** Orden de los adjuntos dentro de un registro: lo que cabe en la página primero, los PDF anexados al final. */
        val ATTACHMENT_ORDER = listOf(AttachmentKind.IMAGE, AttachmentKind.AUDIO, AttachmentKind.DOCUMENT)
        val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "d 'de' MMMM 'de' uuuu",
            Locale.forLanguageTag("es")
        )
    }
}

package com.auroraclima.app.records.data.export

import android.content.Context
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.auroraclima.app.records.domain.repository.ExportDocumentWriter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.encoding.GlyphList
import com.tom_roush.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Escritor del PDF con PdfBox-Android (ADR RF-45): páginas A4 con Helvetica
 * estándar (sin incrustar fuente; WinAnsi cubre tildes, ñ, ¿ y ¡ — lo que no
 * cabe se sustituye por «?»), una sección por registro y las imágenes
 * incrustadas como JPEG sin re-decodificar. El documento se protege con
 * AES-256 (ISO 32000-2, revisión 6) usando la contraseña de la usuaria como
 * contraseña de usuario y de propietaria; el proveedor criptográfico es el de
 * la plataforma (javax.crypto), BouncyCastle no entra al APK.
 *
 * Los rótulos salen de recursos con claves neutras; el contenido real va
 * dentro del archivo, que es exactamente lo que RF-45 pide. Nada se registra
 * en logs; las llamadas internas de la librería a `android.util.Log` se
 * eliminan del release por R8 (proguard-rules.pro) y [BundledFontMapper]
 * evita el escaneo de `/system/fonts`, su índice en caché y la salida a
 * consola del mapeador por defecto.
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
            val layout = PageLayout(pdf)
            layout.title(context.getString(R.string.journal_export_pdf_title))
            layout.caption(context.getString(R.string.journal_export_pdf_generated, document.createdOn.toLongText()))
            document.entries.forEach { entry -> writeEntry(layout, entry, document.includesImages) }
            layout.finish { index, total -> context.getString(R.string.journal_export_pdf_page, index, total) }
            pdf.protect(protection(password))
            pdf.save(sink)
        }
    }

    private fun writeEntry(layout: PageLayout, entry: ExportEntry, includesImages: Boolean) {
        layout.sectionGap()
        layout.labeled(context.getString(R.string.journal_export_pdf_date), entry.occurredOn.toLongText())
        layout.labeled(
            context.getString(R.string.journal_export_pdf_kind),
            entry.kindLabel ?: context.getString(R.string.journal_kind_none)
        )
        layout.heading(context.getString(R.string.journal_export_pdf_detail))
        layout.paragraph(entry.detail)
        if (entry.images.isEmpty() && entry.omittedAttachments.isEmpty()) return
        layout.heading(context.getString(R.string.journal_export_pdf_attachments))
        entry.images.forEachIndexed { index, jpeg ->
            layout.caption(context.getString(R.string.journal_export_pdf_image_n, index + 1))
            layout.image(scaler.scale(jpeg))
        }
        omittedLines(entry.omittedAttachments, includesImages).forEach(layout::line)
    }

    /** Un renglón por adjunto que se cita sin incrustarse, numerado por familia; las imágenes omitidas, una sola nota. */
    private fun omittedLines(omitted: List<AttachmentKind>, includesImages: Boolean): List<String> {
        val counts = mutableMapOf<AttachmentKind, Int>()
        val lines = omitted.mapNotNull { kind ->
            val ordinal = (counts[kind] ?: 0) + 1
            counts[kind] = ordinal
            when (kind) {
                AttachmentKind.DOCUMENT -> context.getString(R.string.journal_export_pdf_document_n, ordinal)
                AttachmentKind.AUDIO -> context.getString(R.string.journal_export_pdf_audio_n, ordinal)
                AttachmentKind.IMAGE -> null
            }
        }
        val omittedImages = AttachmentKind.IMAGE in counts && !includesImages
        return if (omittedImages) lines + context.getString(R.string.journal_export_pdf_images_omitted) else lines
    }

    private fun protection(password: String): StandardProtectionPolicy =
        StandardProtectionPolicy(password, password, AccessPermission()).apply {
            encryptionKeyLength = KEY_LENGTH_BITS
            isPreferAES = true
        }

    /**
     * Paginador mínimo: cursor vertical, salto de página al agotar el alto
     * útil, ajuste de línea por anchos reales de la fuente y pie «Página x de
     * y» agregado al final, cuando ya se conoce el total.
     */
    private class PageLayout(private val pdf: PDDocument) {
        private var page: PDPage = newPage()
        private var stream: PDPageContentStream = PDPageContentStream(pdf, page)
        private var cursorY: Float = PAGE_TOP

        fun title(text: String) = textLines(text, PDType1Font.HELVETICA_BOLD, TITLE_SIZE)

        fun heading(text: String) {
            cursorY -= HEADING_GAP
            textLines(text, PDType1Font.HELVETICA_BOLD, HEADING_SIZE)
        }

        fun labeled(label: String, value: String) = textLines("$label: $value", PDType1Font.HELVETICA, BODY_SIZE)

        fun paragraph(text: String) = textLines(text, PDType1Font.HELVETICA, BODY_SIZE)

        fun line(text: String) = textLines(text, PDType1Font.HELVETICA, BODY_SIZE)

        fun caption(text: String) = textLines(text, PDType1Font.HELVETICA_OBLIQUE, CAPTION_SIZE)

        fun sectionGap() {
            cursorY -= SECTION_GAP
            if (cursorY < PAGE_BOTTOM) nextPage()
        }

        fun image(jpeg: ByteArray) {
            val image = JPEGFactory.createFromByteArray(pdf, jpeg)
            val scale = min(CONTENT_WIDTH / image.width, IMAGE_MAX_HEIGHT / image.height).coerceAtMost(1f)
            val width = image.width * scale
            val height = image.height * scale
            if (cursorY - height < PAGE_BOTTOM) nextPage()
            cursorY -= height
            stream.drawImage(image, MARGIN, cursorY, width, height)
            cursorY -= LINE_GAP
        }

        fun finish(pageLabel: (index: Int, total: Int) -> String) {
            stream.close()
            val total = pdf.numberOfPages
            pdf.pages.forEachIndexed { index, footerPage ->
                PDPageContentStream(pdf, footerPage, PDPageContentStream.AppendMode.APPEND, true).use { footer ->
                    footer.beginText()
                    footer.setFont(PDType1Font.HELVETICA, CAPTION_SIZE)
                    footer.newLineAtOffset(MARGIN, FOOTER_Y)
                    footer.showText(sanitize(pageLabel(index + 1, total)))
                    footer.endText()
                }
            }
        }

        private fun textLines(text: String, font: PDFont, size: Float) {
            val lineHeight = size * LINE_HEIGHT_FACTOR
            text.split('\n').forEach { paragraph ->
                wrap(sanitize(paragraph), font, size).forEach { line ->
                    if (cursorY - lineHeight < PAGE_BOTTOM) nextPage()
                    cursorY -= lineHeight
                    stream.beginText()
                    stream.setFont(font, size)
                    stream.newLineAtOffset(MARGIN, cursorY)
                    stream.showText(line)
                    stream.endText()
                }
            }
            cursorY -= LINE_GAP
        }

        private fun wrap(text: String, font: PDFont, size: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            text.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (widthOf(candidate, font, size) <= CONTENT_WIDTH) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines += current.toString()
                    current = StringBuilder(fitWord(word, font, size, lines))
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines
        }

        /** Una palabra más ancha que la línea se parte en trozos que quepan. */
        private fun fitWord(word: String, font: PDFont, size: Float, lines: MutableList<String>): String {
            var rest = word
            while (widthOf(rest, font, size) > CONTENT_WIDTH && rest.length > 1) {
                var cut = rest.length - 1
                while (cut > 1 && widthOf(rest.substring(0, cut), font, size) > CONTENT_WIDTH) cut--
                lines += rest.substring(0, cut)
                rest = rest.substring(cut)
            }
            return rest
        }

        private fun widthOf(text: String, font: PDFont, size: Float): Float = font.getStringWidth(text) / 1000f * size

        private fun nextPage() {
            stream.close()
            page = newPage()
            stream = PDPageContentStream(pdf, page)
            cursorY = PAGE_TOP
        }

        private fun newPage(): PDPage = PDPage(PDRectangle.A4).also { pdf.addPage(it) }

        /** Solo caracteres que WinAnsi sabe dibujar; el resto se vuelve «?» (tabulador → espacio). */
        private fun sanitize(text: String): String {
            val glyphs = GlyphList.getAdobeGlyphList()
            val out = StringBuilder(text.length)
            text.codePoints().forEach { cp ->
                when {
                    cp == '\t'.code -> out.append(' ')
                    cp < ' '.code -> Unit
                    WinAnsiEncoding.INSTANCE.contains(glyphs.codePointToName(cp)) -> out.appendCodePoint(cp)
                    else -> out.append('?')
                }
            }
            return out.toString()
        }
    }

    private fun LocalDate.toLongText(): String = format(LONG_DAY)

    private companion object {
        const val KEY_LENGTH_BITS = 256
        const val MARGIN = 48f
        const val TITLE_SIZE = 18f
        const val HEADING_SIZE = 12f
        const val BODY_SIZE = 11f
        const val CAPTION_SIZE = 9f
        const val LINE_HEIGHT_FACTOR = 1.35f
        const val LINE_GAP = 4f
        const val HEADING_GAP = 6f
        const val SECTION_GAP = 18f
        const val FOOTER_Y = 28f
        const val IMAGE_MAX_HEIGHT = 420f
        val PAGE_TOP = PDRectangle.A4.height - MARGIN
        const val PAGE_BOTTOM = MARGIN + 16f
        val CONTENT_WIDTH = PDRectangle.A4.width - 2 * MARGIN
        val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern(
            "d 'de' MMMM 'de' uuuu",
            Locale.forLanguageTag("es")
        )
    }
}

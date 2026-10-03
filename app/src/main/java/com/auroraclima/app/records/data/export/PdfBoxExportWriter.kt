package com.auroraclima.app.records.data.export

import android.content.Context
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.auroraclima.app.records.domain.model.ExportPolicy
import com.auroraclima.app.records.domain.repository.ExportDocumentWriter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PageMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.encoding.GlyphList
import com.tom_roush.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
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
 * cabe se sustituye por «?»), una sección por registro y los adjuntos DENTRO
 * del mismo archivo: las imágenes como página (JPEG sin re-decodificar), los
 * documentos PDF con sus páginas anexadas a continuación y los audios como
 * archivos incrustados del PDF (panel de adjuntos del lector). El documento
 * se protege con AES-256 (ISO 32000-2, revisión 6) usando la contraseña de la
 * usuaria como contraseña de usuario y de propietaria; el proveedor
 * criptográfico es el de la plataforma (javax.crypto), BouncyCastle no entra
 * al APK.
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
            try {
                layout.title(context.getString(R.string.journal_export_pdf_title))
                layout.caption(
                    context.getString(R.string.journal_export_pdf_generated, document.createdOn.toLongText())
                )
                document.entries.forEach { entry -> writeEntry(layout, entry) }
                layout.finish { index, total -> context.getString(R.string.journal_export_pdf_page, index, total) }
                pdf.protect(protection(password))
                pdf.save(sink)
            } finally {
                // Las páginas anexadas siguen apuntando a sus documentos de
                // origen hasta el guardado: se cierran al final, pase lo que pase.
                layout.release()
            }
        }
    }

    private fun writeEntry(layout: PageLayout, entry: ExportEntry) {
        layout.sectionGap()
        layout.labeled(context.getString(R.string.journal_export_pdf_date), entry.occurredOn.toLongText())
        layout.labeled(
            context.getString(R.string.journal_export_pdf_kind),
            entry.kindLabel ?: context.getString(R.string.journal_kind_none)
        )
        layout.heading(context.getString(R.string.journal_export_pdf_detail))
        layout.paragraph(entry.detail)
        if (entry.attachments.isEmpty() && entry.omittedAttachments.isEmpty()) return
        layout.heading(context.getString(R.string.journal_export_pdf_attachments))
        val ordinals = Ordinals()
        entry.attachments.forEach { attachment -> writeAttachment(layout, attachment, ordinals.next(attachment.kind)) }
        entry.omittedAttachments.forEach { kind -> layout.line(omittedLine(kind, ordinals.next(kind))) }
        if (entry.omittedAttachments.isNotEmpty()) layout.line(context.getString(R.string.journal_export_pdf_omitted))
    }

    private fun writeAttachment(layout: PageLayout, attachment: AttachmentBytes, ordinal: Int) {
        when (attachment.kind) {
            AttachmentKind.IMAGE -> {
                layout.caption(context.getString(R.string.journal_export_pdf_image_n, ordinal))
                layout.image(scaler.scale(attachment.bytes))
            }
            AttachmentKind.DOCUMENT -> {
                val source = openForAppend(attachment.bytes)
                if (source == null) {
                    layout.line(context.getString(R.string.journal_export_pdf_document_failed, ordinal))
                } else {
                    layout.line(
                        context.getString(R.string.journal_export_pdf_document_n, ordinal, source.numberOfPages)
                    )
                    layout.appendPages(source)
                }
            }
            AttachmentKind.AUDIO -> {
                // El ordinal visible es por registro; el NOMBRE del archivo es
                // por documento: dos registros con audio no deben colisionar.
                val name = layout.embed(attachment.mime, attachment.bytes)
                layout.line(context.getString(R.string.journal_export_pdf_audio_n, ordinal, name))
            }
        }
    }

    /**
     * Abre un PDF adjunto y lo ensaya en seco — anexa todas sus páginas a un
     * documento desechable y lo serializa — antes de tocar el PDF real
     * (auditoría A-3): un árbol de páginas o un stream roto se descubre aquí
     * y el adjunto se cita como «no se pudo incluir», sin dejar páginas a
     * medias. Devuelve el documento abierto (lo cierra `PageLayout.release`).
     */
    private fun openForAppend(bytes: ByteArray): PDDocument? {
        val source = runCatching { PDDocument.load(bytes) }.getOrNull() ?: return null
        val rehearsal = runCatching {
            if (source.numberOfPages == 0) error("empty")
            PDDocument().use { probe ->
                source.pages.forEach { page -> pruneImported(page, probe.importPage(page)) }
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

    /** Numeración por familia dentro de un registro («Imagen adjunta 2», «Audio adjunto 1»). */
    private class Ordinals {
        private val counts = mutableMapOf<AttachmentKind, Int>()

        fun next(kind: AttachmentKind): Int = ((counts[kind] ?: 0) + 1).also { counts[kind] = it }
    }

    /**
     * Paginador mínimo: cursor vertical, página creada al primer trazo (así
     * las páginas anexadas de un documento no dejan una hoja vacía detrás),
     * salto al agotar el alto útil, ajuste de línea por anchos reales de la
     * fuente, anexado de páginas ajenas, archivos incrustados y pie «Página x
     * de y» agregado al final, cuando ya se conoce el total.
     */
    private class PageLayout(private val pdf: PDDocument) {
        private var stream: PDPageContentStream? = null
        private var cursorY: Float = PAGE_TOP
        private val sources = mutableListOf<PDDocument>()
        private val embedded = linkedMapOf<String, PDComplexFileSpecification>()

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
            if (cursorY < PAGE_BOTTOM) closePage()
        }

        fun image(jpeg: ByteArray) {
            val image = JPEGFactory.createFromByteArray(pdf, jpeg)
            val scale = min(CONTENT_WIDTH / image.width, IMAGE_MAX_HEIGHT / image.height).coerceAtMost(1f)
            val width = image.width * scale
            val height = image.height * scale
            if (cursorY - height < PAGE_BOTTOM) closePage()
            val target = ensurePage()
            cursorY -= height
            target.drawImage(image, MARGIN, cursorY, width, height)
            cursorY -= LINE_GAP
        }

        /** Anexa todas las páginas de [source] (ya ensayadas); el origen queda abierto hasta [release]. */
        fun appendPages(source: PDDocument) {
            closePage()
            sources += source
            source.pages.forEach { page -> pruneImported(page, pdf.importPage(page)) }
        }

        /** Archivo incrustado en el PDF (panel de adjuntos del lector), cifrado con el documento; devuelve su nombre único. */
        fun embed(mime: String, bytes: ByteArray): String {
            val name = ExportPolicy.embeddedFileName(mime, embedded.size + 1)
            val file = PDEmbeddedFile(pdf, ByteArrayInputStream(bytes)).apply {
                subtype = mime
                size = bytes.size
            }
            embedded[name] = PDComplexFileSpecification().apply {
                setFile(name)
                fileUnicode = name
                embeddedFile = file
                embeddedFileUnicode = file
            }
            return name
        }

        fun finish(pageLabel: (index: Int, total: Int) -> String) {
            closePage()
            attachEmbedded()
            val total = pdf.numberOfPages
            pdf.pages.forEachIndexed { index, footerPage ->
                PDPageContentStream(pdf, footerPage, PDPageContentStream.AppendMode.APPEND, true, true).use { footer ->
                    footer.beginText()
                    footer.setFont(PDType1Font.HELVETICA, CAPTION_SIZE)
                    footer.newLineAtOffset(MARGIN, FOOTER_Y)
                    footer.showText(sanitize(pageLabel(index + 1, total)))
                    footer.endText()
                }
            }
        }

        fun release() {
            runCatching { stream?.close() }
            sources.forEach { runCatching { it.close() } }
            sources.clear()
        }

        private fun attachEmbedded() {
            if (embedded.isEmpty()) return
            val tree = PDEmbeddedFilesNameTreeNode().apply { names = embedded }
            val catalog = pdf.documentCatalog
            catalog.names = PDDocumentNameDictionary(catalog).apply { embeddedFiles = tree }
            catalog.pageMode = PageMode.USE_ATTACHMENTS
        }

        private fun textLines(text: String, font: PDFont, size: Float) {
            val lineHeight = size * LINE_HEIGHT_FACTOR
            text.split('\n').forEach { paragraph ->
                wrap(sanitize(paragraph), font, size).forEach { line ->
                    if (cursorY - lineHeight < PAGE_BOTTOM) closePage()
                    val target = ensurePage()
                    cursorY -= lineHeight
                    target.beginText()
                    target.setFont(font, size)
                    target.newLineAtOffset(MARGIN, cursorY)
                    target.showText(line)
                    target.endText()
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

        private fun ensurePage(): PDPageContentStream = stream ?: run {
            val page = PDPage(PDRectangle.A4).also { pdf.addPage(it) }
            cursorY = PAGE_TOP
            PDPageContentStream(pdf, page).also { stream = it }
        }

        private fun closePage() {
            stream?.close()
            stream = null
            cursorY = PAGE_TOP
        }

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

    /** Salida que descarta todo: el ensayo solo necesita que la serialización no falle. */
    private object DiscardingOutputStream : OutputStream() {
        override fun write(b: Int) = Unit

        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

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

        /** Claves de página que NO viajan al PDF exportado (auditoría A-4): acciones, anotaciones, metadatos, restos. */
        val PRUNED_PAGE_KEYS = listOf(
            COSName.AA,
            COSName.ANNOTS,
            COSName.METADATA,
            COSName.THUMB,
            COSName.getPDFName("PieceInfo")
        )

        /**
         * Completa la página importada: `importPage` copia el diccionario de la
         * página pero NO los recursos heredados del nodo `Pages` (fuentes e
         * imágenes de PDF de escáner o Ghostscript quedarían en blanco —
         * auditoría A-2), y arrastra anotaciones, acciones JavaScript y
         * metadatos XMP del original que la usuaria no eligió entregar (A-4).
         */
        fun pruneImported(original: PDPage, imported: PDPage) {
            if (!original.cosObject.containsKey(COSName.RESOURCES)) imported.resources = original.resources
            PRUNED_PAGE_KEYS.forEach { imported.cosObject.removeItem(it) }
        }
    }
}

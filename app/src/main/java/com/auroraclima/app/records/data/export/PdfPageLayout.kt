package com.auroraclima.app.records.data.export

import com.auroraclima.app.records.domain.model.ExportPolicy
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
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.font.encoding.GlyphList
import com.tom_roush.pdfbox.pdmodel.font.encoding.WinAnsiEncoding
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayInputStream
import kotlin.math.min

/**
 * Maquetador del PDF exportado (RF-45): cursor vertical sobre páginas A4
 * creadas al primer trazo (así las páginas anexadas de un documento no dejan
 * una hoja vacía detrás), salto al agotar el alto útil, ajuste de línea por
 * anchos reales de la fuente y las piezas de la jerarquía visual — banda de
 * registro, rótulos de sección, cajas de aviso, imágenes enmarcadas,
 * cabecera sobre las páginas anexadas y pie común con numeración, que se
 * dibujan al final cuando ya se conoce el total de páginas. También guarda
 * los archivos incrustados y los documentos de origen hasta [release].
 * Solo tonos de gris: el PDF se imprime y se lee en cualquier lector.
 */
internal class PdfPageLayout(private val pdf: PDDocument) {
    private var stream: PDPageContentStream? = null
    private var cursorY: Float = PAGE_TOP
    private val sources = mutableListOf<PDDocument>()
    private val embedded = linkedMapOf<String, PDComplexFileSpecification>()
    private val appendedHeaders = mutableMapOf<Int, String>()

    /** Rótulo que se repite como «… · continuación» en la página que retoma tras un anexo; null = nada pendiente. */
    private var pendingContinuation: String? = null

    fun title(text: String) = textLines(text, PDType1Font.HELVETICA_BOLD, TITLE_SIZE, INK)

    fun caption(text: String) = textLines(text, PDType1Font.HELVETICA, CAPTION_SIZE, INK_MUTED)

    fun paragraph(text: String) {
        textLines(text, PDType1Font.HELVETICA, BODY_SIZE, INK)
        cursorY -= PARAGRAPH_GAP
    }

    /** Rótulo de sección: pequeño, en mayúsculas y gris, con aire arriba. */
    fun sectionLabel(text: String) {
        cursorY -= SECTION_GAP
        textLines(text.uppercase(), PDType1Font.HELVETICA_BOLD, LABEL_SIZE, INK_MUTED)
    }

    /** «Tipo  valor» con el rótulo en negrita y el valor normal, en una sola línea (si no cabe, termina en «…»). */
    fun labeled(label: String, value: String) {
        ensureRoom(BODY_SIZE * LINE_HEIGHT_FACTOR)
        val target = ensurePage()
        cursorY -= BODY_SIZE * LINE_HEIGHT_FACTOR
        val labelText = sanitize("$label  ")
        showText(target, labelText, PDType1Font.HELVETICA_BOLD, BODY_SIZE, INK, MARGIN)
        val offset = widthOf(labelText, PDType1Font.HELVETICA_BOLD, BODY_SIZE)
        val lines = wrap(sanitize(value), PDType1Font.HELVETICA, BODY_SIZE, CONTENT_WIDTH - offset)
        val shown = if (lines.size > 1) lines.first().dropLast(1) + "\u2026" else lines.first()
        showText(target, shown, PDType1Font.HELVETICA, BODY_SIZE, INK, MARGIN + offset)
        cursorY -= LINE_GAP
    }

    /** Banda gris del registro: rótulo pequeño («Registro 1 de 3») y la fecha en grande. */
    fun band(label: String, headline: String) {
        ensureRoom(BAND_HEIGHT + LINE_GAP)
        val target = ensurePage()
        cursorY -= BAND_HEIGHT
        target.setNonStrokingColor(FILL_BAND)
        target.addRect(MARGIN, cursorY, CONTENT_WIDTH, BAND_HEIGHT)
        target.fill()
        showText(
            target,
            sanitize(label.uppercase()),
            PDType1Font.HELVETICA_BOLD,
            LABEL_SIZE,
            INK_MUTED,
            MARGIN + BOX_PADDING,
            cursorY + BAND_LABEL_BASELINE
        )
        showText(
            target,
            sanitize(headline),
            PDType1Font.HELVETICA_BOLD,
            HEADLINE_SIZE,
            INK,
            MARGIN + BOX_PADDING,
            cursorY + BAND_HEADLINE_BASELINE
        )
        cursorY -= LINE_GAP * 2
    }

    /** Caja con borde para anunciar un adjunto que no es imagen; [muted] para lo omitido o fallido. */
    fun note(text: String, muted: Boolean) {
        val font = if (muted) PDType1Font.HELVETICA_OBLIQUE else PDType1Font.HELVETICA
        val lines = wrap(sanitize(text), font, BODY_SIZE, CONTENT_WIDTH - 2 * BOX_PADDING)
        val height = lines.size * BODY_SIZE * LINE_HEIGHT_FACTOR + 2 * BOX_PADDING
        ensureRoom(height + LINE_GAP)
        val target = ensurePage()
        cursorY -= height
        target.setNonStrokingColor(if (muted) FILL_MUTED else FILL_NOTE)
        target.addRect(MARGIN, cursorY, CONTENT_WIDTH, height)
        target.fill()
        target.setStrokingColor(STROKE)
        target.setLineWidth(0.6f)
        target.addRect(MARGIN, cursorY, CONTENT_WIDTH, height)
        target.stroke()
        var y = cursorY + height - BOX_PADDING - BODY_SIZE
        lines.forEach { line ->
            showText(target, line, font, BODY_SIZE, if (muted) INK_MUTED else INK, MARGIN + BOX_PADDING, y)
            y -= BODY_SIZE * LINE_HEIGHT_FACTOR
        }
        cursorY -= LINE_GAP * 2
    }

    /** Regla fina a lo ancho, con aire. */
    fun rule() {
        ensureRoom(RULE_GAP * 2)
        val target = ensurePage()
        cursorY -= RULE_GAP
        target.setStrokingColor(STROKE)
        target.setLineWidth(0.8f)
        target.moveTo(MARGIN, cursorY)
        target.lineTo(MARGIN + CONTENT_WIDTH, cursorY)
        target.stroke()
        cursorY -= RULE_GAP
    }

    /** Imagen a lo ancho del contenido con un marco fino. */
    fun framedImage(jpeg: ByteArray) {
        val image = JPEGFactory.createFromByteArray(pdf, jpeg)
        val scale = min(CONTENT_WIDTH / image.width, IMAGE_MAX_HEIGHT / image.height).coerceAtMost(1f)
        val width = image.width * scale
        val height = image.height * scale
        ensureRoom(height + LINE_GAP)
        val target = ensurePage()
        cursorY -= height
        target.drawImage(image, MARGIN, cursorY, width, height)
        target.setStrokingColor(STROKE)
        target.setLineWidth(0.6f)
        target.addRect(MARGIN, cursorY, width, height)
        target.stroke()
        cursorY -= LINE_GAP * 2
    }

    /** Una página nueva pedida explícitamente (registro nuevo) no es una continuación. */
    fun newPageForEntry() {
        closePage()
        pendingContinuation = null
    }

    /**
     * Anexa todas las páginas de [source] (ya ensayadas) y les asigna la
     * cabecera que [header] produce para cada índice (1-based); el origen
     * queda abierto hasta [release]. Si después del anexo sigue contenido del
     * mismo registro, la página que lo retoma abre con [continuation].
     */
    fun appendPages(source: PDDocument, continuation: String, header: (index: Int) -> String) {
        closePage()
        sources += source
        source.pages.forEachIndexed { index, _ ->
            pdf.importPruned(source.getPage(index))
            appendedHeaders[pdf.numberOfPages - 1] = header(index + 1)
        }
        pendingContinuation = continuation
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

    /** Cierra la maqueta: pie común en todas las páginas, cabecera sobre las anexadas y archivos incrustados. */
    fun finish(footer: String, pageLabel: (index: Int, total: Int) -> String) {
        closePage()
        attachEmbedded()
        val total = pdf.numberOfPages
        pdf.pages.forEachIndexed { index, page ->
            PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { overlay ->
                drawFooter(overlay, page, footer, pageLabel(index + 1, total))
                appendedHeaders[index]?.let { drawAppendedHeader(overlay, page, it) }
            }
        }
    }

    fun release() {
        runCatching { stream?.close() }
        sources.forEach { runCatching { it.close() } }
        sources.clear()
    }

    private fun drawFooter(overlay: PDPageContentStream, page: PDPage, footer: String, pageLabel: String) {
        val view = enterDisplaySpace(overlay, page)
        overlay.setStrokingColor(STROKE)
        overlay.setLineWidth(0.5f)
        overlay.moveTo(MARGIN, FOOTER_Y + FOOTER_RULE_OFFSET)
        overlay.lineTo(view.width - MARGIN, FOOTER_Y + FOOTER_RULE_OFFSET)
        overlay.stroke()
        showText(overlay, sanitize(footer), PDType1Font.HELVETICA, CAPTION_SIZE, INK_MUTED, MARGIN, FOOTER_Y)
        val label = sanitize(pageLabel)
        val labelX = view.width - MARGIN - widthOf(label, PDType1Font.HELVETICA, CAPTION_SIZE)
        showText(overlay, label, PDType1Font.HELVETICA, CAPTION_SIZE, INK_MUTED, labelX, FOOTER_Y)
        overlay.restoreGraphicsState()
    }

    /** Rótulo discreto sobre una página anexada: dice de qué registro y documento viene, sin tapar el original. */
    private fun drawAppendedHeader(overlay: PDPageContentStream, page: PDPage, text: String) {
        val view = enterDisplaySpace(overlay, page)
        showText(
            overlay,
            sanitize(text),
            PDType1Font.HELVETICA_BOLD,
            LABEL_SIZE,
            INK_MUTED,
            MARGIN,
            view.height - APPENDED_HEADER_BASELINE
        )
        overlay.setStrokingColor(STROKE)
        overlay.setLineWidth(0.5f)
        overlay.moveTo(MARGIN, view.height - APPENDED_HEADER_HEIGHT)
        overlay.lineTo(view.width - MARGIN, view.height - APPENDED_HEADER_HEIGHT)
        overlay.stroke()
        overlay.restoreGraphicsState()
    }

    /**
     * Deja el CTM en el espacio TAL COMO SE VE la página (auditoría H-1): el
     * origen en la esquina inferior izquierda del `CropBox` y el eje girado
     * según `/Rotate`, para que la cabecera y el pie queden derechos y dentro
     * del área visible también en escaneos rotados o recortados. Devuelve el
     * tamaño visible (ancho × alto en pantalla). Abre un estado gráfico que el
     * llamador cierra con `restoreGraphicsState`.
     */
    private fun enterDisplaySpace(overlay: PDPageContentStream, page: PDPage): PDRectangle {
        val box = page.cropBox
        overlay.saveGraphicsState()
        val rotation = ((page.rotation % 360) + 360) % 360
        val turn = when (rotation) {
            90 -> Matrix.getRotateInstance(Math.toRadians(90.0), box.width, 0f)
            180 -> Matrix.getRotateInstance(Math.toRadians(180.0), box.width, box.height)
            270 -> Matrix.getRotateInstance(Math.toRadians(270.0), 0f, box.height)
            else -> Matrix()
        }
        overlay.transform(turn.multiply(Matrix.getTranslateInstance(box.lowerLeftX, box.lowerLeftY)))
        return if (rotation == 90 || rotation == 270) {
            PDRectangle(
                box.height,
                box.width
            )
        } else {
            PDRectangle(box.width, box.height)
        }
    }

    private fun attachEmbedded() {
        if (embedded.isEmpty()) return
        val tree = PDEmbeddedFilesNameTreeNode().apply { names = embedded }
        val catalog = pdf.documentCatalog
        catalog.names = PDDocumentNameDictionary(catalog).apply { embeddedFiles = tree }
        catalog.pageMode = PageMode.USE_ATTACHMENTS
    }

    private fun textLines(text: String, font: PDFont, size: Float, ink: Float) {
        val lineHeight = size * LINE_HEIGHT_FACTOR
        text.split('\n').forEach { paragraph ->
            wrap(sanitize(paragraph), font, size).forEach { line ->
                ensureRoom(lineHeight)
                val target = ensurePage()
                cursorY -= lineHeight
                showText(target, line, font, size, ink, MARGIN)
            }
        }
        cursorY -= LINE_GAP
    }

    private fun showText(
        target: PDPageContentStream,
        text: String,
        font: PDFont,
        size: Float,
        ink: Float,
        x: Float,
        y: Float = cursorY
    ) {
        target.beginText()
        target.setFont(font, size)
        target.setNonStrokingColor(ink)
        target.newLineAtOffset(x, y)
        target.showText(text)
        target.endText()
    }

    private fun wrap(text: String, font: PDFont, size: Float, maxWidth: Float = CONTENT_WIDTH): List<String> {
        if (text.isBlank()) return listOf("")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        text.split(' ').filter { it.isNotEmpty() }.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (widthOf(candidate, font, size) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(fitWord(word, font, size, maxWidth, lines))
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    /** Una palabra más ancha que la línea se parte en trozos que quepan. */
    private fun fitWord(word: String, font: PDFont, size: Float, maxWidth: Float, lines: MutableList<String>): String {
        var rest = word
        while (widthOf(rest, font, size) > maxWidth && rest.length > 1) {
            var cut = rest.length - 1
            while (cut > 1 && widthOf(rest.substring(0, cut), font, size) > maxWidth) cut--
            lines += rest.substring(0, cut)
            rest = rest.substring(cut)
        }
        return rest
    }

    private fun widthOf(text: String, font: PDFont, size: Float): Float = font.getStringWidth(text) / 1000f * size

    private fun ensureRoom(height: Float) {
        if (stream != null && cursorY - height < PAGE_BOTTOM) closePage()
    }

    private fun ensurePage(): PDPageContentStream = stream ?: run {
        val page = PDPage(PDRectangle.A4).also { pdf.addPage(it) }
        cursorY = PAGE_TOP
        val opened = PDPageContentStream(pdf, page).also { stream = it }
        pendingContinuation?.let { label ->
            pendingContinuation = null
            cursorY -= LABEL_SIZE * LINE_HEIGHT_FACTOR
            val text = sanitize("$label · continuación".uppercase())
            showText(opened, text, PDType1Font.HELVETICA_BOLD, LABEL_SIZE, INK_MUTED, MARGIN)
            cursorY -= LINE_GAP * 2
        }
        opened
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

    private companion object {
        const val MARGIN = 48f
        const val TITLE_SIZE = 20f
        const val HEADLINE_SIZE = 14f
        const val BODY_SIZE = 11f
        const val CAPTION_SIZE = 9f
        const val LABEL_SIZE = 8.5f
        const val LINE_HEIGHT_FACTOR = 1.4f
        const val LINE_GAP = 4f
        const val PARAGRAPH_GAP = 4f
        const val SECTION_GAP = 10f
        const val RULE_GAP = 10f
        const val BAND_HEIGHT = 44f
        const val BOX_PADDING = 8f
        const val FOOTER_Y = 28f
        const val APPENDED_HEADER_HEIGHT = 22f
        const val APPENDED_HEADER_BASELINE = 14f
        const val BAND_LABEL_BASELINE = BAND_HEIGHT - 16f
        const val BAND_HEADLINE_BASELINE = 11f
        const val FOOTER_RULE_OFFSET = 12f
        const val IMAGE_MAX_HEIGHT = 400f
        const val INK = 0.12f
        const val INK_MUTED = 0.42f
        const val STROKE = 0.72f
        const val FILL_BAND = 0.92f
        const val FILL_NOTE = 0.965f
        const val FILL_MUTED = 0.985f
        val PAGE_TOP = PDRectangle.A4.height - MARGIN
        const val PAGE_BOTTOM = MARGIN + 20f
        val CONTENT_WIDTH = PDRectangle.A4.width - 2 * MARGIN
    }
}

/** Claves de página que NO viajan al PDF exportado (auditoría A-4): acciones, anotaciones, metadatos, restos. */
private val PRUNED_PAGE_KEYS = listOf(
    COSName.AA,
    COSName.ANNOTS,
    COSName.METADATA,
    COSName.THUMB,
    COSName.getPDFName("PieceInfo")
)

/**
 * Importa [page] en este documento y la completa: `importPage` copia el
 * diccionario de la página pero NO los recursos heredados del nodo `Pages`
 * (fuentes e imágenes de PDF de escáner o Ghostscript quedarían en blanco —
 * auditoría A-2), y arrastra anotaciones, acciones JavaScript y metadatos
 * XMP del original que la usuaria no eligió entregar (A-4).
 */
internal fun PDDocument.importPruned(page: PDPage): PDPage {
    val imported = importPage(page)
    if (!page.cosObject.containsKey(COSName.RESOURCES)) imported.resources = page.resources
    PRUNED_PAGE_KEYS.forEach { imported.cosObject.removeItem(it) }
    return imported
}

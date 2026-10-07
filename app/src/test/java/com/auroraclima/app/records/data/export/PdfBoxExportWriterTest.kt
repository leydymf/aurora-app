package com.auroraclima.app.records.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PageMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Escritor real del PDF (ADR RF-45) en la JVM con los assets del AAR
 * (`isIncludeAndroidResources`): el archivo abre SOLO con la contraseña, el
 * texto (fecha, tipo, relato, adjuntos citados) está dentro, las imágenes se
 * incrustan, los PDF adjuntos se anexan página a página y los audios viajan
 * como archivos incrustados — todo cifrado con el documento. El cifrado corre
 * con el proveedor de la plataforma: BouncyCastle no está en el classpath.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class PdfBoxExportWriterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val writer = PdfBoxExportWriter(context, ExportImageScaler())

    /** Los fixtures ajenos usan `PDType1Font` antes que el escritor: mismas condiciones que él impone. */
    @Before
    fun prepareLibrary() {
        if (!PDFBoxResourceLoader.isReady()) PDFBoxResourceLoader.init(context)
        BundledFontMapper.install()
    }

    private fun write(document: ExportDocument, password: String): ByteArray =
        ByteArrayOutputStream().also { writer.write(document, password, it) }.toByteArray()

    private fun textOf(pdf: ByteArray, password: String): String =
        PDDocument.load(pdf, password).use { PDFTextStripper().getText(it) }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }.toByteArray()
    }

    /** Un PDF ajeno de [pages] páginas con un texto reconocible en cada una. */
    private fun foreignPdf(pages: Int): ByteArray = PDDocument().use { doc ->
        repeat(pages) { index ->
            val page = PDPage(PDRectangle.LETTER).also { doc.addPage(it) }
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 14f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Contenido ajeno pagina ${index + 1}")
                stream.endText()
            }
        }
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    @Test
    fun `el archivo abre con la contrasena y contiene fecha tipo y relato`() {
        val pdf =
            write(document(entry(kindLabel = "Psicológica", detail = "Me gritó frente a los niños.")), "clave-1234")

        val text = textOf(pdf, "clave-1234")
        assertTrue(text.contains("2 de julio de 2026"))
        assertTrue(text.contains("Psicológica"))
        assertTrue(text.contains("Me gritó frente a los niños."))
        assertTrue(text.contains("Exportado el 3 de octubre de 2026"))
        assertTrue(text.contains("Página 1 de 1"))
    }

    @Test
    fun `sin la contrasena no abre y los bytes no exponen el relato`() {
        val pdf = write(document(entry(detail = "texto que no debe verse")), "clave-1234")

        assertThrows(InvalidPasswordException::class.java) { PDDocument.load(pdf).close() }
        assertThrows(InvalidPasswordException::class.java) { PDDocument.load(pdf, "otra-clave").close() }
        assertFalse(String(pdf, Charsets.ISO_8859_1).contains("texto que no debe verse"))
        PDDocument.load(pdf, "clave-1234").use { assertTrue(it.isEncrypted) }
    }

    @Test
    fun `sin tipo imprime el rotulo neutro y cita los adjuntos no incluidos`() {
        val entry =
            entry(
                kindLabel = null,
                omitted = listOf(AttachmentKind.DOCUMENT, AttachmentKind.AUDIO, AttachmentKind.IMAGE)
            )
        val pdf = write(document(entry), "clave-1234")

        val text = textOf(pdf, "clave-1234")
        assertTrue(text.contains("Sin tipo"))
        assertTrue(text.contains("Documento adjunto 1 · no incluido"))
        assertTrue(text.contains("Audio adjunto 1 · no incluido"))
        assertTrue(text.contains("Imagen adjunta 1 · no incluida"))
        assertTrue(text.contains("Los adjuntos no se incluyeron"))
        assertEquals(0, imageCount(pdf, "clave-1234"))
    }

    @Test
    fun `las imagenes del documento se incrustan como objetos de imagen`() {
        val entry = entry(attachments = listOf(image(jpeg(64, 48)), image(jpeg(2048, 1536))))
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        assertEquals(2, imageCount(pdf, "clave-1234"))
        assertTrue(textOf(pdf, "clave-1234").contains("Imagen 2 · Registro 1"))
    }

    @Test
    fun `un PDF adjunto se anexa pagina a pagina y el pie numera el total`() {
        val entry = entry(
            detail = "relato con documento",
            attachments = listOf(AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", foreignPdf(2)))
        )
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        PDDocument.load(pdf, "clave-1234").use { doc ->
            // Portada con el registro + 2 páginas anexadas, sin hoja vacía al final.
            assertEquals(3, doc.numberOfPages)
            assertTrue(doc.getPage(0).cosObject.containsKey(COSName.CONTENTS))
            assertTrue(doc.isEncrypted)
        }
        val text = textOf(pdf, "clave-1234")
        assertTrue(text.contains("Documento adjunto 1 · sus 2 páginas van a continuación"))
        assertTrue(text.contains("Registro 1 · Documento adjunto 1 · página 2 de 2"))
        assertTrue(text.contains("Contenido ajeno pagina 1"))
        assertTrue(text.contains("Contenido ajeno pagina 2"))
        assertTrue(text.contains("Página 3 de 3"))
    }

    @Test
    fun `tras un PDF anexado el siguiente registro empieza en pagina nueva`() {
        val withDocument = entry(
            detail = "primero",
            attachments = listOf(AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", foreignPdf(1)))
        )
        val second = entry(detail = "segundo registro despues del anexo")
        val pdf = write(
            ExportDocument(LocalDate.of(2026, 10, 3), listOf(withDocument, second), includesAttachments = true),
            "clave-1234"
        )

        PDDocument.load(pdf, "clave-1234").use { doc ->
            assertEquals(3, doc.numberOfPages)
            val lastPage = PDFTextStripper().apply {
                startPage = 3
                endPage = 3
            }.getText(doc)
            assertTrue(lastPage.contains("segundo registro despues del anexo"))
        }
    }

    @Test
    fun `una pagina con recursos heredados del nodo Pages conserva su texto al anexarse`() {
        // PDF de escáner/Ghostscript: `/Resources` vive en `Pages`, no en cada página.
        val inherited = PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER).also { doc.addPage(it) }
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 14f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Texto con recursos heredados")
                stream.endText()
            }
            doc.pages.cosObject.setItem(COSName.RESOURCES, page.resources.cosObject)
            page.cosObject.removeItem(COSName.RESOURCES)
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val entry = entry(attachments = listOf(AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", inherited)))
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        PDDocument.load(pdf, "clave-1234").use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertTrue(doc.getPage(1).cosObject.containsKey(COSName.RESOURCES))
        }
        assertTrue(textOf(pdf, "clave-1234").contains("Texto con recursos heredados"))
    }

    @Test
    fun `la pagina anexada entra sin anotaciones ni metadatos del original`() {
        val annotated = PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER).also { doc.addPage(it) }
            page.annotations = listOf(PDAnnotationLink().apply { contents = "enlace del original" })
            page.cosObject.setItem(COSName.METADATA, COSName.getPDFName("xmp"))
            page.cosObject.setItem(COSName.AA, COSName.getPDFName("js"))
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val entry = entry(attachments = listOf(AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", annotated)))
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        PDDocument.load(pdf, "clave-1234").use { doc ->
            val imported = doc.getPage(1).cosObject
            assertFalse(imported.containsKey(COSName.ANNOTS))
            assertFalse(imported.containsKey(COSName.METADATA))
            assertFalse(imported.containsKey(COSName.AA))
        }
    }

    @Test
    fun `una pagina anexada rotada y recortada conserva su cabecera y su pie`() {
        val rotated = PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER).also { doc.addPage(it) }
            page.rotation = 90
            page.cropBox = PDRectangle(30f, 40f, 500f, 700f)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 14f)
                stream.newLineAtOffset(60f, 600f)
                stream.showText("Escaneo de lado")
                stream.endText()
            }
            ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
        val entry = entry(attachments = listOf(AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", rotated)))
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        PDDocument.load(pdf, "clave-1234").use { doc ->
            val appended = doc.getPage(1)
            assertEquals(90, appended.rotation)
            val text = PDFTextStripper().apply {
                startPage = 2
                endPage = 2
            }.getText(doc)
            assertEquals(PDRectangle(30f, 40f, 500f, 700f).toString(), appended.cropBox.toString())
            assertTrue(text.contains("Registro 1 · Documento adjunto 1 · página 1 de 1"))
            assertTrue(text.contains("Página 2 de 2"))
        }
    }

    @Test
    fun `lo que sigue a un PDF anexado retoma con la banda de continuacion del registro`() {
        val entry = entry(
            attachments = listOf(
                AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", foreignPdf(1)),
                AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", foreignPdf(1))
            )
        )
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        PDDocument.load(pdf, "clave-1234").use { doc ->
            // registro + anexo 1 + página de continuación con la caja del documento 2 + anexo 2
            assertEquals(4, doc.numberOfPages)
            val resumed = PDFTextStripper().apply {
                startPage = 3
                endPage = 3
            }.getText(doc)
            assertTrue(resumed.contains("REGISTRO 1 DE 1 · CONTINUACIÓN"))
            assertTrue(resumed.contains("Documento adjunto 2"))
        }
    }

    @Test
    fun `un PDF cuyo arbol de paginas apunta a objetos inexistentes se cita como no incluido`() {
        val broken = """%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[9 0 R]/Count 1>>endobj
trailer<</Root 1 0 R>>
""".toByteArray(Charsets.ISO_8859_1)
        val entry = entry(attachments = listOf(AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", broken)))
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        assertTrue(textOf(pdf, "clave-1234").contains("Documento adjunto 1 · no se pudo incluir"))
        PDDocument.load(pdf, "clave-1234").use { assertEquals(1, it.numberOfPages) }
    }

    @Test
    fun `un PDF adjunto danado se cita como no incluido sin romper la exportacion`() {
        val entry = entry(
            attachments = listOf(
                AttachmentBytes(AttachmentKind.DOCUMENT, "application/pdf", "no es un pdf".toByteArray())
            )
        )
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        val text = textOf(pdf, "clave-1234")
        assertTrue(text.contains("Documento adjunto 1 · no se pudo incluir"))
        PDDocument.load(pdf, "clave-1234").use { assertEquals(1, it.numberOfPages) }
    }

    @Test
    fun `un audio adjunto queda incrustado como archivo del PDF con su nombre y MIME`() {
        val audio = ByteArray(2048) { (it % 251).toByte() }
        val entry = entry(attachments = listOf(AttachmentBytes(AttachmentKind.AUDIO, "audio/wav", audio)))
        val pdf = write(document(entry, includesAttachments = true), "clave-1234")

        PDDocument.load(pdf, "clave-1234").use { doc ->
            val files = doc.documentCatalog.names.embeddedFiles.names
            assertEquals(setOf("audio_1.wav"), files.keys)
            val spec = files.getValue("audio_1.wav")
            assertEquals("audio/wav", spec.embeddedFile.subtype)
            assertArrayEquals(audio, spec.embeddedFile.toByteArray())
            assertEquals(PageMode.USE_ATTACHMENTS, doc.documentCatalog.pageMode)
        }
        assertTrue(textOf(pdf, "clave-1234").contains("Audio adjunto 1 · archivo «audio_1.wav»"))
        // Incrustado Y cifrado: los bytes del audio no aparecen en claro en el archivo.
        assertFalse(String(pdf, Charsets.ISO_8859_1).contains(String(audio.copyOfRange(0, 64), Charsets.ISO_8859_1)))
    }

    @Test
    fun `dos registros con audio conservan ambos archivos incrustados con nombres distintos`() {
        val first = ByteArray(300) { 1 }
        val second = ByteArray(300) { 2 }
        val document = ExportDocument(
            createdOn = LocalDate.of(2026, 10, 3),
            entries = listOf(
                entry(detail = "uno", attachments = listOf(AttachmentBytes(AttachmentKind.AUDIO, "audio/mpeg", first))),
                entry(detail = "dos", attachments = listOf(AttachmentBytes(AttachmentKind.AUDIO, "audio/wav", second)))
            ),
            includesAttachments = true
        )
        val pdf = write(document, "clave-1234")

        PDDocument.load(pdf, "clave-1234").use { doc ->
            val files = doc.documentCatalog.names.embeddedFiles.names
            assertEquals(setOf("audio_1.mp3", "audio_2.wav"), files.keys)
            assertArrayEquals(first, files.getValue("audio_1.mp3").embeddedFile.toByteArray())
            assertArrayEquals(second, files.getValue("audio_2.wav").embeddedFile.toByteArray())
        }
        val text = textOf(pdf, "clave-1234")
        // El rótulo visible numera por registro; el nombre del archivo, por documento.
        assertTrue(text.contains("Audio adjunto 1 · archivo «audio_1.mp3»"))
        assertTrue(text.contains("Audio adjunto 1 · archivo «audio_2.wav»"))
    }

    @Test
    fun `un relato largo se pagina y el pie numera todas las paginas`() {
        val longDetail = (1..120).joinToString(
            " "
        ) { "párrafo número $it con varias palabras para forzar el salto de línea" }
        val pdf = write(document(entry(detail = longDetail)), "clave-1234")

        PDDocument.load(pdf, "clave-1234").use { assertTrue(it.numberOfPages > 1) }
        assertTrue(textOf(pdf, "clave-1234").contains("Página 2 de"))
    }

    @Test
    fun `los caracteres fuera de WinAnsi no rompen la escritura`() {
        val pdf = write(document(entry(detail = "Con emoji 😀 y tilde ñ á ¿?")), "clave-1234")

        val text = textOf(pdf, "clave-1234")
        assertTrue(text.contains("tilde ñ á ¿?"))
    }

    private fun imageCount(pdf: ByteArray, password: String): Int = PDDocument.load(pdf, password).use { doc ->
        doc.pages.sumOf { page -> page.resources.xObjectNames.count { page.resources.isImageXObject(it) } }
    }

    private fun image(bytes: ByteArray) = AttachmentBytes(AttachmentKind.IMAGE, "image/jpeg", bytes)

    private fun document(entry: ExportEntry, includesAttachments: Boolean = false) = ExportDocument(
        createdOn = LocalDate.of(2026, 10, 3),
        entries = listOf(entry),
        includesAttachments = includesAttachments
    )

    private fun entry(
        kindLabel: String? = "Segunda",
        detail: String = "relato",
        attachments: List<AttachmentBytes> = emptyList(),
        omitted: List<AttachmentKind> = emptyList()
    ) = ExportEntry(LocalDate.of(2026, 7, 2), kindLabel, detail, attachments, omitted)
}

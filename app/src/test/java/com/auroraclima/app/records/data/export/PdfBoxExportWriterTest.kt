package com.auroraclima.app.records.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Escritor real del PDF (ADR RF-45) en la JVM con los assets del AAR
 * (`isIncludeAndroidResources`): el archivo abre SOLO con la contraseña, el
 * texto (fecha, tipo, relato, adjuntos citados) está dentro y las imágenes
 * se incrustan únicamente cuando el documento las trae. El cifrado corre con
 * el proveedor de la plataforma: BouncyCastle no está en el classpath.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class PdfBoxExportWriterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val writer = PdfBoxExportWriter(context, ExportImageScaler())

    private fun write(document: ExportDocument, password: String): ByteArray =
        ByteArrayOutputStream().also { writer.write(document, password, it) }.toByteArray()

    private fun textOf(pdf: ByteArray, password: String): String =
        PDDocument.load(pdf, password).use { PDFTextStripper().getText(it) }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return ByteArrayOutputStream().also { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }.toByteArray()
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
        val pdf = write(document(entry, includesImages = false), "clave-1234")

        val text = textOf(pdf, "clave-1234")
        assertTrue(text.contains("Sin tipo"))
        assertTrue(text.contains("Documento adjunto 1 (no incluido)"))
        assertTrue(text.contains("Audio adjunto 1 (no incluido)"))
        assertTrue(text.contains("Las imágenes adjuntas no se incluyeron"))
        assertEquals(0, imageCount(pdf, "clave-1234"))
    }

    @Test
    fun `las imagenes del documento se incrustan como objetos de imagen`() {
        val entry = entry(images = listOf(jpeg(64, 48), jpeg(2048, 1536)))
        val pdf = write(document(entry, includesImages = true), "clave-1234")

        assertEquals(2, imageCount(pdf, "clave-1234"))
        assertTrue(textOf(pdf, "clave-1234").contains("Imagen adjunta 2"))
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

    private fun document(entry: ExportEntry, includesImages: Boolean = false) =
        ExportDocument(createdOn = LocalDate.of(2026, 10, 3), entries = listOf(entry), includesImages = includesImages)

    private fun entry(
        kindLabel: String? = "Segunda",
        detail: String = "relato",
        images: List<ByteArray> = emptyList(),
        omitted: List<AttachmentKind> = emptyList()
    ) = ExportEntry(LocalDate.of(2026, 7, 2), kindLabel, detail, images, omitted)
}

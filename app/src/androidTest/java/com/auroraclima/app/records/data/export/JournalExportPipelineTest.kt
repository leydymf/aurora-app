package com.auroraclima.app.records.data.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.core.security.KeystoreMediaCipher
import com.auroraclima.app.records.data.AttachmentStore
import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flujo principal de RF-45 con las piezas REALES del dispositivo: un adjunto
 * sellado con la llave del Keystore se descifra, entra al PDF escrito por
 * PdfBox-Android (sin BouncyCastle en el APK), el archivo abre solo con la
 * contraseña, «guardar» escribe directo al destino y «compartir» deja un
 * único temporal en `doc_out` expuesto por el FileProvider real que el
 * barrido elimina. La parte que es UI del sistema (selector de documentos y
 * de apps) queda en la corrida manual CP-38.
 *
 * Ejecutar con `./gradlew connectedDebugAndroidTest` en un emulador.
 */
@RunWith(AndroidJUnit4::class)
class JournalExportPipelineTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AttachmentStore(context, KeystoreMediaCipher())
    private val staging = ExportStaging(context, FileProviderShareUriResolver(context))
    private val writer = PdfBoxExportWriter(context, ExportImageScaler())
    private val stagingDir = File(context.cacheDir, ExportStaging.DIRECTORY_NAME)
    private val savedFile = File(context.cacheDir, "destino_de_prueba.pdf")
    private val refs = mutableListOf<String>()

    private val repository = LocalJournalExportRepository(
        context = context,
        writer = writer,
        staging = staging,
        opener = { uri -> context.contentResolver.openOutputStream(Uri.parse(uri), "wt") }
    )

    /**
     * El barrido de arranque ([ExportStagingSweeper], `AppInitializer`) corre
     * fuera del hilo principal al crearse el proceso; el test empieza
     * milisegundos después y su temporal podía caer en ese barrido. En la app
     * real nadie exporta sin pasar antes por el código y el login.
     */
    @Before
    fun awaitStartupSweep() {
        Thread.sleep(STARTUP_SWEEP_GRACE_MS)
    }

    @After
    fun tearDown() {
        refs.forEach { store.delete(it) }
        stagingDir.deleteRecursively()
        savedFile.delete()
    }

    @Test
    fun sealedImageTravelsIntoThePasswordProtectedPdf() = runBlocking {
        val sealedRef = sealImage()
        val image = store.read(sealedRef) { it.readBytes() }
        val document = document(
            attachments = listOf(
                AttachmentBytes(AttachmentKind.IMAGE, "image/jpeg", image),
                AttachmentBytes(AttachmentKind.AUDIO, "audio/wav", ByteArray(512) { it.toByte() })
            )
        )

        val result = repository.exportTo(Uri.fromFile(savedFile).toString(), document, "clave-de-prueba")

        assertTrue(result.isSuccess)
        PDDocument.load(savedFile, "clave-de-prueba").use { pdf ->
            assertTrue(pdf.isEncrypted)
            val text = PDFTextStripper().getText(pdf)
            assertTrue(text.contains("2 de julio de 2026"))
            assertTrue(text.contains("Segunda"))
            assertTrue(text.contains("relato de prueba con tilde y ñ"))
            assertTrue(text.contains("Audio adjunto 1: incluido como archivo «audio_1.wav»"))
            val images = pdf.pages.sumOf { p -> p.resources.xObjectNames.count { p.resources.isImageXObject(it) } }
            assertEquals(1, images)
            assertEquals(setOf("audio_1.wav"), pdf.documentCatalog.names.embeddedFiles.names.keys)
        }
        assertThrows(InvalidPasswordException::class.java) { PDDocument.load(savedFile).close() }
        assertThrows(InvalidPasswordException::class.java) { PDDocument.load(savedFile, "otra").close() }
        // Guardar no pasa por el temporal de compartir.
        assertTrue(stagingDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun sharingStagesOneEncryptedFileBehindTheFileProviderAndClearingRemovesIt() = runBlocking {
        val uri = repository.stage(document(), "clave-de-prueba").getOrThrow()

        val files = stagingDir.listFiles().orEmpty()
        assertEquals(1, files.size)
        assertEquals("documento_2026-10-03.pdf", files.single().name)
        assertEquals("content", Uri.parse(uri).scheme)
        assertEquals(context.packageName + ".files", Uri.parse(uri).authority)
        // El FileProvider real sirve el temporal (lo que leerá la app receptora).
        context.contentResolver.openInputStream(Uri.parse(uri))!!.use { input ->
            PDDocument.load(input, "clave-de-prueba").use { assertTrue(it.isEncrypted) }
        }
        assertFalse(files.single().readText(Charsets.ISO_8859_1).contains("relato de prueba"))

        assertTrue(repository.clearStaged().isSuccess)
        assertTrue(stagingDir.listFiles().orEmpty().isEmpty())
    }

    private fun sealImage(): String {
        val jpeg = ByteArrayOutputStream().also { out ->
            val bitmap = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(10, 90, 160))
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
        }.toByteArray()
        val ref = store.newRef().also { refs += it }
        store.write(ref) { it.write(jpeg) }
        return ref
    }

    private fun document(attachments: List<AttachmentBytes> = emptyList()) = ExportDocument(
        createdOn = LocalDate.of(2026, 10, 3),
        entries = listOf(
            ExportEntry(
                occurredOn = LocalDate.of(2026, 7, 2),
                kindLabel = "Segunda",
                detail = "relato de prueba con tilde y ñ",
                attachments = attachments,
                omittedAttachments = emptyList()
            )
        ),
        includesAttachments = attachments.isNotEmpty()
    )

    private companion object {
        const val STARTUP_SWEEP_GRACE_MS = 1_500L
    }
}

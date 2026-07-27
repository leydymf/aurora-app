package com.auroraclima.app.records.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.records.data.local.AttachmentRow
import com.auroraclima.app.records.data.local.JournalDatabase
import com.auroraclima.app.records.data.local.JournalEntryRow
import com.auroraclima.app.records.domain.model.AttachmentContent
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Adjuntos protegidos (RF-33) sobre el esquema real v2 y el almacén de archivos
 * con un cifrador de stream reversible (el Keystore real va instrumentado —
 * `KeystoreMediaCipherTest`). Cubre los criterios de salida de la sesión:
 * cifrado/descifrado por stream (el archivo en disco NO contiene el claro),
 * cupo por registro, reclamo del guardado, cascada de RF-36 a nivel de datos
 * (filas Y archivos) y barrido de huérfanos.
 *
 * La vía de cámara importa desde el búfer de captura (archivo local), así que
 * el pipeline completo saneado→cifrado→fila se prueba sin ContentResolver.
 */
// Gráficos NATIVOS por el mismo motivo que ImageImporterTest: el shadow legacy
// aceptaría cualquier byte como imagen y el JPEG en disco no sería real.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class LocalJournalAttachmentRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
    private val store = AttachmentStore(context, ReversibleMediaCipher())
    private val captureBuffer = CaptureBuffer(context)
    private val mediaRemote = FakeJournalMediaRemoteSource()
    private val repository = LocalJournalAttachmentRepository(
        context = context,
        storeAccess = JournalStoreAccess { database },
        store = store,
        imageImporter = ImageImporter(),
        captureBuffer = captureBuffer,
        mediaRemote = mediaRemote
    )

    private val mediaDir = File(context.filesDir, "media_store")
    private val pendingDir = File(context.cacheDir, "media_pending")

    @After
    fun tearDown() {
        database.close()
        mediaDir.deleteRecursively()
        pendingDir.deleteRecursively()
    }

    /**
     * Simula a la cámara del sistema: un JPEG real en el búfer de captura. No
     * pasa por `beginCapture()` porque `FileProvider.getUriForFile` exige el
     * manifest real, ausente en Robolectric sin recursos; la construcción del
     * URI la validan `ManifestMinimizationTest` (provider y rutas) y la pasada
     * manual en emulador.
     */
    private fun stageCapture() {
        pendingDir.mkdirs()
        File(pendingDir, "c0ffee").outputStream().use { out ->
            val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(10, 90, 160))
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
        }
    }

    @Test
    fun `la captura importada queda cifrada en disco y el temporal se elimina`() = runTest {
        stageCapture()

        val attachment = repository.importCapture(AttachmentPolicy.STAGED_OWNER).getOrThrow()

        assertEquals(AttachmentKind.IMAGE, attachment.kind)
        // El temporal en claro no sobrevive a la importación (política §5.1.ii).
        assertTrue(pendingDir.listFiles().isNullOrEmpty())
        // Un solo archivo en el almacén, nombre opaco de 32 hex sin extensión.
        val storedFile = requireNotNull(mediaDir.listFiles()?.single())
        assertTrue(storedFile.name.matches(Regex("[0-9a-f]{32}")))
        // Cifrado en reposo: los bytes en disco NO son un JPEG legible…
        val onDisk = storedFile.readBytes()
        assertFalse(onDisk.size >= 2 && onDisk[0] == 0xFF.toByte() && onDisk[1] == 0xD8.toByte())
        // …y el contenido descifrado sí lo es (round-trip por stream).
        val content = repository.content(attachment.id).getOrThrow()
        val image = content as AttachmentContent.Image
        assertTrue(image.bytes[0] == 0xFF.toByte() && image.bytes[1] == 0xD8.toByte())
    }

    @Test
    fun `cancelar la captura descarta el temporal sin dejar nada`() = runTest {
        stageCapture()

        repository.discardCapture()

        assertTrue(pendingDir.listFiles().isNullOrEmpty())
        assertTrue(mediaDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `el guardado reclama los adjuntos en preparacion`() = runTest {
        stageCapture()
        val staged = repository.importCapture(AttachmentPolicy.STAGED_OWNER).getOrThrow()

        repository.claim(listOf(staged.id), owner = 7).getOrThrow()

        assertTrue(repository.attachments(AttachmentPolicy.STAGED_OWNER).getOrThrow().isEmpty())
        assertEquals(listOf(staged.id), repository.attachments(7).getOrThrow().map { it.id })
    }

    @Test
    fun `al cupo lleno la importacion se rechaza`() = runTest {
        repeat(AttachmentPolicy.MAX_PER_ENTRY) { index ->
            database.attachmentDao().insert(
                AttachmentRow(owner = 7, ref = "ref$index", kind = "image/jpeg", createdAt = index.toLong())
            )
        }
        stageCapture()

        assertTrue(repository.importCapture(7).isFailure)
    }

    @Test
    fun `eliminar por registro borra filas y archivos`() = runTest {
        // Cascada de RF-36 (criterio 2) a nivel de datos.
        stageCapture()
        val attachment = repository.importCapture(AttachmentPolicy.STAGED_OWNER).getOrThrow()
        repository.claim(listOf(attachment.id), owner = 7).getOrThrow()
        assertEquals(1, mediaDir.listFiles()?.size)

        repository.deleteForOwner(7).getOrThrow()

        assertTrue(repository.attachments(7).getOrThrow().isEmpty())
        assertTrue(mediaDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `eliminar un adjunto borra su fila y su archivo`() = runTest {
        stageCapture()
        val attachment = repository.importCapture(AttachmentPolicy.STAGED_OWNER).getOrThrow()

        repository.delete(attachment.id).getOrThrow()

        assertTrue(repository.attachments(AttachmentPolicy.STAGED_OWNER).getOrThrow().isEmpty())
        assertTrue(mediaDir.listFiles().isNullOrEmpty())
        assertNull(repository.content(attachment.id).getOrThrow())
    }

    @Test
    fun `el barrido limpia huerfanos y respeta a los registros vivos`() = runTest {
        // Registro vivo con adjunto + un en-preparación abandonado + un huérfano
        // de un borrado interrumpido: solo el primero sobrevive.
        val entryId = database.journalDao().insert(
            JournalEntryRow(day = 1, kind = "physical", detail = "x", createdAt = 1)
        )
        stageCapture()
        val kept = repository.importCapture(AttachmentPolicy.STAGED_OWNER).getOrThrow()
        repository.claim(listOf(kept.id), owner = entryId).getOrThrow()
        stageCapture()
        repository.importCapture(AttachmentPolicy.STAGED_OWNER).getOrThrow()
        stageCapture()
        val orphan = repository.importCapture(AttachmentPolicy.STAGED_OWNER).getOrThrow()
        repository.claim(listOf(orphan.id), owner = 999).getOrThrow()

        repository.sweepOrphans().getOrThrow()

        assertEquals(listOf(kept.id), repository.attachments(entryId).getOrThrow().map { it.id })
        assertTrue(repository.attachments(AttachmentPolicy.STAGED_OWNER).getOrThrow().isEmpty())
        assertTrue(repository.attachments(999).getOrThrow().isEmpty())
        assertEquals(1, mediaDir.listFiles()?.size)
    }

    @Test
    fun `los contadores excluyen lo en preparacion`() = runTest {
        database.attachmentDao().insert(AttachmentRow(owner = 7, ref = "a", kind = "image/jpeg", createdAt = 1))
        database.attachmentDao().insert(AttachmentRow(owner = 7, ref = "b", kind = "application/pdf", createdAt = 2))
        database.attachmentDao().insert(AttachmentRow(owner = 9, ref = "c", kind = "audio/ogg", createdAt = 3))
        database.attachmentDao().insert(
            AttachmentRow(owner = AttachmentPolicy.STAGED_OWNER, ref = "d", kind = "image/jpeg", createdAt = 4)
        )

        assertEquals(mapOf(7L to 2, 9L to 1), repository.counts().getOrThrow())
    }

    @Test
    fun `un MIME desconocido almacenado se omite sin romper la lista`() = runTest {
        // Esquema futuro (§6), mismo criterio que EntryKind desconocido.
        database.attachmentDao().insert(AttachmentRow(owner = 7, ref = "v", kind = "video/mp4", createdAt = 1))
        database.attachmentDao().insert(AttachmentRow(owner = 7, ref = "a", kind = "audio/ogg", createdAt = 2))

        val attachments = repository.attachments(7).getOrThrow()

        assertEquals(listOf(AttachmentKind.AUDIO), attachments.map { it.kind })
    }

    @Test
    fun `una BD que lanza se traduce a failure en todas las operaciones`() = runTest {
        // Mismo contrato P-36 que el repositorio de registros.
        val broken = LocalJournalAttachmentRepository(
            context = context,
            storeAccess = JournalStoreAccess { error("blob corrupto") },
            store = store,
            imageImporter = ImageImporter(),
            captureBuffer = captureBuffer,
            mediaRemote = mediaRemote
        )

        assertTrue(broken.importCapture(1).isFailure)
        assertTrue(broken.attachments(1).isFailure)
        assertTrue(broken.counts().isFailure)
        assertTrue(broken.content(1).isFailure)
        assertTrue(broken.claim(listOf(1), 1).isFailure)
        assertTrue(broken.delete(1).isFailure)
        assertTrue(broken.deleteForOwner(1).isFailure)
        assertTrue(broken.sweepOrphans().isFailure)
    }

    @Test
    fun `borrar un adjunto reclamado propaga el borrado remoto`() = runTest {
        // RF-36 criterio 2 + RF-39: la copia troceada en la nube se va con él.
        database.attachmentDao().insert(AttachmentRow(owner = 7, ref = "r1", kind = "image/jpeg", createdAt = 1))
        store.write("r1") { it.write(byteArrayOf(1)) }

        repository.delete(1).getOrThrow()

        assertEquals(listOf("r1"), mediaRemote.deleted)
    }

    @Test
    fun `borrar un adjunto en preparacion no toca la nube`() = runTest {
        // Los adjuntos con owner = 0 jamás subieron: no hay nada que borrar.
        database.attachmentDao().insert(
            AttachmentRow(owner = AttachmentPolicy.STAGED_OWNER, ref = "r0", kind = "image/jpeg", createdAt = 1)
        )
        store.write("r0") { it.write(byteArrayOf(1)) }

        repository.delete(1).getOrThrow()

        assertTrue(mediaRemote.deleted.isEmpty())
    }

    @Test
    fun `el borrado en cascada del registro propaga cada adjunto a la nube`() = runTest {
        database.journalDao().insert(JournalEntryRow(day = 1, kind = "economic", detail = "x", createdAt = 1))
        database.attachmentDao().insert(AttachmentRow(owner = 1, ref = "r1", kind = "image/jpeg", createdAt = 1))
        database.attachmentDao().insert(AttachmentRow(owner = 1, ref = "r2", kind = "application/pdf", createdAt = 2))
        store.write("r1") { it.write(byteArrayOf(1)) }
        store.write("r2") { it.write(byteArrayOf(2)) }

        repository.deleteForOwner(1).getOrThrow()

        assertEquals(listOf("r1", "r2"), mediaRemote.deleted)
    }

    @Test
    fun `una importacion fallida no deja archivo parcial en el almacen`() = runTest {
        // La cámara nunca escribió: el temporal quedó vacío y no hay nada que
        // importar. (El rechazo de bytes indecodificables va instrumentado —
        // ImageImporterRejectionTest — porque Robolectric los degrada a un
        // bitmap falso.)
        pendingDir.mkdirs()
        File(pendingDir, "c0ffee").createNewFile()

        assertTrue(repository.importCapture(AttachmentPolicy.STAGED_OWNER).isFailure)
        assertTrue(mediaDir.listFiles().isNullOrEmpty())
        assertTrue(repository.attachments(AttachmentPolicy.STAGED_OWNER).getOrThrow().isEmpty())
        // El temporal tampoco sobrevive al intento.
        assertTrue(pendingDir.listFiles().isNullOrEmpty())
    }
}

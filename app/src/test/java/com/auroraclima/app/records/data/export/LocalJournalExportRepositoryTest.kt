package com.auroraclima.app.records.data.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportEntry
import com.auroraclima.app.records.domain.repository.ExportDocumentWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Salida del PDF (RF-45 criterio 5) con un escritor falso: «guardar» escribe
 * directo al stream del destino sin tocar la caché; «compartir» deja UN
 * temporal en `doc_out` con el nombre neutro y `clearStaged` lo elimina; un
 * fallo del escritor no deja restos.
 */
@RunWith(RobolectricTestRunner::class)
class LocalJournalExportRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val staging =
        ExportStaging(context) { file -> Uri.parse("content://${context.packageName}.files/d/${file.name}") }
    private val stagingDir = File(context.cacheDir, ExportStaging.DIRECTORY_NAME)
    private val sink = ByteArrayOutputStream()
    private val document = ExportDocument(
        createdOn = LocalDate.of(2026, 10, 3),
        entries = listOf(ExportEntry(LocalDate.of(2026, 7, 2), "Segunda", "relato", emptyList(), emptyList())),
        includesImages = false
    )

    private val writer = ExportDocumentWriter { _, password, out -> out.write("pdf:$password".toByteArray()) }
    private val failingWriter = ExportDocumentWriter { _, _, out ->
        out.write("parcial".toByteArray())
        throw IOException("fallo a medias")
    }

    private fun repository(writer: ExportDocumentWriter, opener: OutputStreamOpener = OutputStreamOpener { sink }) =
        LocalJournalExportRepository(context, writer, staging, opener)

    @After
    fun tearDown() {
        stagingDir.deleteRecursively()
    }

    @Test
    fun `guardar escribe al destino elegido y no deja nada en la cache`() = runTest {
        val result = repository(writer).exportTo("content://docs/1", document, "clave")

        assertTrue(result.isSuccess)
        assertEquals("pdf:clave", sink.toString())
        assertFalse(stagingDir.exists() && stagingDir.listFiles().orEmpty().isNotEmpty())
    }

    @Test
    fun `guardar sin stream del proveedor falla sin excepcion`() = runTest {
        val result = repository(writer, OutputStreamOpener { null }).exportTo("content://docs/1", document, "clave")

        assertTrue(result.isFailure)
    }

    @Test
    fun `compartir deja un unico temporal con el nombre neutro y devuelve su uri compartible`() = runTest {
        val uri = repository(writer).stage(document, "clave").getOrThrow()

        val files = stagingDir.listFiles().orEmpty()
        assertEquals(1, files.size)
        assertEquals("documento_2026-10-03.pdf", files.single().name)
        assertEquals("pdf:clave", files.single().readText())
        assertTrue(uri.startsWith("content://${context.packageName}.files/d/"))
        assertTrue(uri.endsWith("documento_2026-10-03.pdf"))
    }

    @Test
    fun `compartir dos veces conserva solo el ultimo temporal`() = runTest {
        val repository = repository(writer)
        repository.stage(document, "una").getOrThrow()
        repository.stage(document.copy(createdOn = LocalDate.of(2026, 10, 4)), "otra").getOrThrow()

        assertEquals(listOf("documento_2026-10-04.pdf"), stagingDir.listFiles().orEmpty().map { it.name })
    }

    @Test
    fun `clearStaged elimina el temporal y es idempotente`() = runTest {
        val repository = repository(writer)
        repository.stage(document, "clave").getOrThrow()

        assertTrue(repository.clearStaged().isSuccess)
        assertTrue(stagingDir.listFiles().orEmpty().isEmpty())
        assertTrue(repository.clearStaged().isSuccess)
    }

    @Test
    fun `un fallo del escritor al compartir no deja restos y se traduce a Result`() = runTest {
        val result = repository(failingWriter).stage(document, "clave")

        assertTrue(result.isFailure)
        assertTrue(stagingDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `si la sesion se cierra durante la escritura el temporal no sobrevive`() = runTest {
        // Auditoría H-1: el cleaner barre al cerrar, pero la escritura bloqueante
        // termina después; el repositorio debe eliminar lo que escribió.
        val job = Job()
        val cancellingWriter = ExportDocumentWriter { _, _, out ->
            out.write("pdf".toByteArray())
            job.cancel()
        }

        val result = runCatching { withContext(job) { repository(cancellingWriter).stage(document, "clave") } }

        assertTrue(result.isFailure || result.getOrThrow().isFailure)
        assertTrue(stagingDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `un fallo del escritor al guardar se traduce a Result`() = runTest {
        val result = repository(failingWriter).exportTo("content://docs/1", document, "clave")

        assertTrue(result.isFailure)
    }
}

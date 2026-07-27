package com.auroraclima.app.records.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.core.security.KeystoreMediaCipher
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RF-20 sobre RF-33: la eliminación total hace desaparecer el almacén de
 * adjuntos completo — archivos cifrados, caché de visualización y cualquier
 * captura temporal. La destrucción de la llave (primer paso del eraser, B-1)
 * va en runCatching: el Keystore no existe en la JVM y su prueba real es
 * instrumentada, como la del resto del Keystore.
 */
@RunWith(RobolectricTestRunner::class)
class JournalMediaEraserTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = AttachmentStore(context, ReversibleMediaCipher())
    private val captureBuffer = CaptureBuffer(context)
    private val eraser = JournalMediaEraser(context, store, captureBuffer, KeystoreMediaCipher())

    @Test
    fun `el borrado elimina almacen, cache de visualizacion, capturas y temporales de viaje`() = runTest {
        store.write("ref1") { it.write("contenido".toByteArray()) }
        store.newViewFile("ref1")
        // Directo al búfer: FileProvider exige el manifest real, ausente en
        // Robolectric sin recursos (mismo criterio que el test del repositorio).
        File(context.cacheDir, "media_pending").apply { mkdirs() }
            .resolve("c0ffee").writeBytes("captura pendiente".toByteArray())
        // Resto de un viaje interrumpido (RF-39, auditoría D2 M-2).
        File(context.cacheDir, "media_relay").apply { mkdirs() }
            .resolve("mtx123.tmp").writeBytes(byteArrayOf(1, 2, 3))

        eraser.erase()

        assertFalse(File(context.filesDir, "media_store").exists())
        assertFalse(File(context.cacheDir, "media_view").exists())
        assertFalse(File(context.cacheDir, "media_relay").exists())
        assertFalse(File(context.cacheDir, "media_pending").listFiles()?.isNotEmpty() ?: false)
    }
}

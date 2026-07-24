package com.auroraclima.app.records.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Saneado de imágenes (RF-33, política D0 §5.2): la importación re-codifica y
 * con ello ELIMINA los metadatos — la prueba clave escribe un fixture JPEG con
 * coordenadas GPS reales en su EXIF y verifica que la salida no conserva ni la
 * posición ni segmento EXIF alguno. También: re-escalado al tope de la
 * política y rechazo de lo no decodificable.
 *
 * En Robolectric los gráficos nativos decodifican y codifican JPEG de verdad;
 * el EXIF del fixture se escribe con androidx.exifinterface (implementación
 * Java pura, funciona en la JVM — parte de la justificación de la dependencia).
 */
// Gráficos NATIVOS: el shadow legacy inventa un bitmap de 100×100 para
// CUALQUIER byte y la prueba de rechazo de no-imágenes perdería sentido.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ImageImporterTest {
    private val importer = ImageImporter()

    /** JPEG real en disco con EXIF de GPS y orientación incrustados. */
    private fun fixtureWithGps(width: Int = 320, height: Int = 200): File {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File.createTempFile("fixture", ".jpg", context.cacheDir)
        file.outputStream().use { out ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(120, 40, 200))
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out))
        }
        ExifInterface(file.path).apply {
            // Coordenadas de Bucaramanga: exactamente lo que JAMÁS debe quedar
            // en la copia protegida.
            setLatLong(7.119349, -73.122742)
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_MAKE, "FabricanteDePrueba")
            saveAttributes()
        }
        return file
    }

    @Test
    fun `el fixture de entrada si contiene GPS`() {
        // Sanidad de la prueba: si esto fallara, el test principal no probaría nada.
        val fixture = fixtureWithGps()
        assertNotNull(ExifInterface(fixture.path).latLong)
    }

    @Test
    fun `la importacion elimina el GPS y todo el EXIF`() {
        val fixture = fixtureWithGps()
        val output = ByteArrayOutputStream()

        importer.sanitize({ fixture.inputStream() }, output)
        val sanitized = output.toByteArray()

        val exif = ExifInterface(ByteArrayInputStream(sanitized))
        assertNull("El GPS sobrevivió a la importación", exif.latLong)
        assertNull("El fabricante sobrevivió a la importación", exif.getAttribute(ExifInterface.TAG_MAKE))
        // Defensa en profundidad: ni siquiera queda un segmento EXIF en los bytes.
        assertEquals(-1, indexOfExifMarker(sanitized))
    }

    @Test
    fun `la salida sigue siendo una imagen decodificable`() {
        val fixture = fixtureWithGps()
        val output = ByteArrayOutputStream()

        importer.sanitize({ fixture.inputStream() }, output)
        val decoded = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())

        assertNotNull(decoded)
        // ROTATE_90 horneado en píxeles: las dimensiones quedan intercambiadas.
        assertEquals(200, decoded.width)
        assertEquals(320, decoded.height)
    }

    @Test
    fun `una imagen grande se re-escala al tope de la politica`() {
        val fixture = fixtureWithGps(width = 4096, height = 1024)
        val output = ByteArrayOutputStream()

        importer.sanitize({ fixture.inputStream() }, output)
        val decoded = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())

        assertNotNull(decoded)
        assertTrue(max(decoded.width, decoded.height) <= AttachmentPolicy.IMAGE_MAX_DIMENSION)
    }

    // El rechazo de un origen que NO es imagen se prueba instrumentado
    // (ImageImporterRejectionTest): el BitmapFactory de Robolectric — aun en
    // modo nativo — degrada los bytes indecodificables a un bitmap falso de
    // 100×100 en vez de fallar, así que esta ruta solo es observable en Android
    // real.

    @Test
    fun `el muestreo nunca decodifica de menos ni de mas`() {
        // Pura (guía §5): el lado decodificado queda en [tope, 2·tope) — ni se
        // pierde nitidez ni se cargan píxeles de más; el ajuste exacto lo hace
        // el re-escalado.
        assertEquals(1, ImageImporter.sampleSizeFor(1000, 2048))
        assertEquals(1, ImageImporter.sampleSizeFor(4095, 2048))
        assertEquals(2, ImageImporter.sampleSizeFor(4096, 2048))
        assertEquals(2, ImageImporter.sampleSizeFor(8191, 2048))
        assertEquals(4, ImageImporter.sampleSizeFor(8192, 2048))
    }

    /** Busca el marcador APP1 "Exif\0\0" en los bytes del JPEG. */
    private fun indexOfExifMarker(bytes: ByteArray): Int {
        val marker = byteArrayOf(0x45, 0x78, 0x69, 0x66, 0x00, 0x00)
        outer@ for (i in 0..bytes.size - marker.size) {
            for (j in marker.indices) {
                if (bytes[i + j] != marker[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

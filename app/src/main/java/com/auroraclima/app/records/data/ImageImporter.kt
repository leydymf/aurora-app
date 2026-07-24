package com.auroraclima.app.records.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Saneado de imágenes al importarlas (RF-33, política D0 §5.2): la imagen se
 * DECODIFICA y RE-CODIFICA a JPEG — la copia protegida se construye píxel a
 * píxel, así que ningún metadato del original (EXIF, GPS, XMP, miniaturas
 * embebidas) puede sobrevivir por construcción. La orientación EXIF se lee
 * antes de descartarse y se hornea en los píxeles para que la foto no quede
 * acostada.
 *
 * De paso se re-escala al lado mayor de la política (gama baja, RNF-07) y el
 * resultado se escribe directo en el stream cifrante del almacén: el JPEG
 * saneado nunca existe completo en memoria ni en claro en disco.
 */
@Singleton
class ImageImporter @Inject constructor() {
    /**
     * Decodifica la imagen de [open] (invocable varias veces: medidas,
     * orientación y decodificación son pasadas separadas) y escribe el JPEG
     * saneado en [target]. Lanza si el origen no es una imagen decodificable.
     */
    fun sanitize(open: () -> InputStream, target: OutputStream) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException()

        // Mejor esfuerzo: si el EXIF no puede leerse, se importa sin rotar —
        // la limpieza de metadatos no depende de esta lectura.
        val orientation = runCatching {
            open().use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(max(bounds.outWidth, bounds.outHeight), AttachmentPolicy.IMAGE_MAX_DIMENSION)
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException()
        val prepared = capDimension(applyOrientation(decoded, orientation), AttachmentPolicy.IMAGE_MAX_DIMENSION)
        if (!prepared.compress(Bitmap.CompressFormat.JPEG, AttachmentPolicy.IMAGE_JPEG_QUALITY, target)) {
            throw IOException()
        }
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply {
                postRotate(270f)
                postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun capDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            max(1, (bitmap.width * scale).toInt()),
            max(1, (bitmap.height * scale).toInt()),
            true
        )
    }

    companion object {
        /**
         * Mayor potencia de dos que deja el lado decodificado en el rango
         * [tope, 2·tope): nunca se cargan más píxeles de los necesarios y el
         * ajuste exacto lo hace el re-escalado. Pura, con test.
         */
        fun sampleSizeFor(largestDimension: Int, maxDimension: Int): Int {
            var sample = 1
            var size = largestDimension
            while (size / 2 >= maxDimension) {
                sample *= 2
                size /= 2
            }
            return sample
        }
    }
}

package com.auroraclima.app.records.data.export

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.auroraclima.app.records.data.ImageImporter
import com.auroraclima.app.records.domain.model.ExportPolicy
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reduce una imagen adjunta (ya saneada, ≤2048 px) al tamaño de página del
 * PDF (RF-45; [ExportPolicy.IMAGE_MAX_DIMENSION]) antes de incrustarla: el
 * JPEG resultante entra al documento sin volver a decodificarse (DCTDecode),
 * así que el tamaño del archivo y la memoria del documento quedan acotados
 * en gama baja (RNF-07). Decodifica con `inSampleSize` para no cargar más
 * píxeles de los necesarios (mismo criterio que [ImageImporter]).
 */
class ExportImageScaler @Inject constructor() {
    fun scale(jpeg: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        val largest = max(bounds.outWidth, bounds.outHeight)
        if (largest <= 0) throw IllegalArgumentException("not an image")
        if (largest <= ExportPolicy.IMAGE_MAX_DIMENSION) return jpeg
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageImporter.sampleSizeFor(largest, ExportPolicy.IMAGE_MAX_DIMENSION)
        }
        val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
            ?: throw IllegalArgumentException("not an image")
        val capped = cap(decoded)
        return ByteArrayOutputStream().use { out ->
            capped.compress(Bitmap.CompressFormat.JPEG, ExportPolicy.IMAGE_JPEG_QUALITY, out)
            if (capped !== decoded) capped.recycle()
            decoded.recycle()
            out.toByteArray()
        }
    }

    private fun cap(bitmap: Bitmap): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= ExportPolicy.IMAGE_MAX_DIMENSION) return bitmap
        val ratio = ExportPolicy.IMAGE_MAX_DIMENSION.toFloat() / largest
        val width = max(1, (bitmap.width * ratio).roundToInt())
        val height = max(1, (bitmap.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}

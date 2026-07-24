package com.auroraclima.app.records.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Destino temporal de la captura con la cámara DEL SISTEMA (RF-33, política D0
 * §5.1.ii): un archivo en la caché PRIVADA de la app, expuesto a la app de
 * cámara solo por el FileProvider no exportado con permiso puntual de
 * escritura. La foto NO pasa por la galería ni por almacenamiento compartido;
 * apenas se importa (saneada y cifrada), el temporal se elimina.
 *
 * Riesgo residual documentado (ADR RF-33): mientras la captura está en curso,
 * el JPEG existe en claro en la caché privada (solo legible con root/adb); y
 * algunas cámaras de fabricante pueden guardar una copia propia fuera de
 * nuestro control — la UI lo advierte junto al rastro de galería.
 */
@Singleton
class CaptureBuffer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val directory: File
        get() = File(context.cacheDir, DIRECTORY_NAME)

    /** Prepara un destino nuevo (una captura a la vez) y devuelve su URI para EXTRA_OUTPUT. */
    fun begin(): Uri {
        clear()
        directory.mkdirs()
        val bytes = ByteArray(NAME_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val file = File(directory, bytes.joinToString("") { "%02x".format(it) })
        file.createNewFile()
        return FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    /** La captura pendiente con contenido, si la cámara llegó a escribirla. */
    fun pending(): File? = directory.listFiles()?.firstOrNull { it.length() > 0 }

    /** Elimina todo temporal (cancelación, importación terminada o borrado RF-20). */
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        // Nombres neutros (RNF-05); la autoridad se resuelve con el applicationId
        // real (incluye el sufijo de debug) igual que el manifest.
        const val DIRECTORY_NAME = "media_pending"
        const val AUTHORITY_SUFFIX = ".files"
        const val NAME_SIZE_BYTES = 8
    }
}

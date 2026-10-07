package com.auroraclima.app.records.data.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporal del PDF para «Compartir» (RF-45 criterio 5): un archivo en la
 * caché PRIVADA, expuesto a la app receptora solo por el FileProvider no
 * exportado con permiso puntual de lectura — mismo mecanismo que la captura
 * de cámara ([com.auroraclima.app.records.data.CaptureBuffer]). El archivo
 * queda YA cifrado con la contraseña de la usuaria y se elimina al volver
 * del selector, en cada cierre de sesión y al abrir la pantalla.
 *
 * El nombre visible es el neutro de la política (`documento_AAAA-MM-DD.pdf`):
 * es lo que la app receptora mostrará como adjunto.
 */
@Singleton
class ExportStaging @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ShareUriResolver
) {
    private val directory: File
        get() = File(context.cacheDir, DIRECTORY_NAME)

    /** Archivo nuevo (uno a la vez: borra cualquier resto antes). */
    fun create(fileName: String): File {
        clear()
        directory.mkdirs()
        return File(directory, fileName)
    }

    /** URI compartible del temporal (el permiso de lectura puntual lo concede el intent). */
    fun uriFor(file: File): Uri = resolver.uriFor(file)

    /** Elimina todo temporal de exportación; idempotente. */
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    companion object {
        // Nombre neutro (RNF-05), declarado en res/xml/file_paths.xml.
        const val DIRECTORY_NAME = "doc_out"
    }
}

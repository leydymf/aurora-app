package com.auroraclima.app.records.data

import android.content.Context
import com.auroraclima.app.core.security.MediaCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Almacén de archivos cifrados de los adjuntos (RF-33, RNF-01): un directorio
 * del almacenamiento interno PRIVADO de la app, con un archivo por adjunto,
 * nombre opaco (32 hex de [SecureRandom], sin extensión) y contenido sellado
 * por stream con [MediaCipher] — sin la llave del Keystore, el directorio es
 * una colección de blobs ilegibles sin relación visible entre sí; la relación
 * con cada registro vive cifrada en la BD (`attachments`).
 *
 * El directorio queda excluido del backup por construcción (`allowBackup=false`
 * + reglas de exclusión ya versionadas, P-00/P-03).
 */
@Singleton
class AttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: MediaCipher
) {
    private val directory: File
        get() = File(context.filesDir, DIRECTORY_NAME)

    /** Nombre opaco nuevo; la probabilidad de colisión con 16 bytes aleatorios es despreciable. */
    fun newRef(): String {
        val bytes = ByteArray(REF_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Crea el archivo cifrado de [ref] y entrega a [write] el stream YA cifrante
     * para que vierta el contenido (nunca pasa por memoria completo). Si algo
     * falla, el archivo parcial se elimina antes de relanzar.
     */
    fun write(ref: String, write: (OutputStream) -> Unit) {
        directory.mkdirs()
        val file = File(directory, ref)
        try {
            file.outputStream().use { raw -> cipher.sealing(raw).use(write) }
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    /** Abre el contenido descifrado de [ref] como stream y lo entrega a [read]. */
    fun <T> read(ref: String, read: (InputStream) -> T): T =
        File(directory, ref).inputStream().use { raw -> cipher.opening(raw).use(read) }

    fun delete(ref: String) {
        File(directory, ref).delete()
    }

    /** Tamaño en disco del archivo cifrado (0 si no existe); el sello GCM suma solo unos bytes. */
    fun sizeOf(ref: String): Long = File(directory, ref).length()

    /**
     * Copia temporal descifrada para el visor de PDF (PdfRenderer exige un
     * descriptor posicionable; ver `AttachmentContent.Document`). Quien abre el
     * descriptor elimina el archivo al instante; [clearViewCache] barre los
     * restos de una muerte de proceso en esa ventana.
     */
    fun newViewFile(ref: String): File {
        val dir = viewDirectory
        dir.mkdirs()
        val target = File(dir, ref)
        target.outputStream().use { out -> read(ref) { input -> input.copyTo(out) } }
        return target
    }

    fun clearViewCache() {
        viewDirectory.deleteRecursively()
    }

    /** Eliminación total (RF-20): almacén y caché de visualización dejan de existir. */
    fun deleteAll() {
        directory.deleteRecursively()
        viewDirectory.deleteRecursively()
    }

    private val viewDirectory: File
        get() = File(context.cacheDir, VIEW_DIRECTORY_NAME)

    private companion object {
        // Nombres neutros (RNF-05), mismo criterio que journal_store/store_meta.
        const val DIRECTORY_NAME = "media_store"
        const val VIEW_DIRECTORY_NAME = "media_view"
        const val REF_SIZE_BYTES = 16
    }
}

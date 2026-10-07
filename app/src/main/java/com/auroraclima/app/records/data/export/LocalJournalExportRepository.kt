package com.auroraclima.app.records.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.model.ExportPolicy
import com.auroraclima.app.records.domain.repository.ExportDocumentWriter
import com.auroraclima.app.records.domain.repository.JournalExportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Salida del PDF (RF-45 criterio 5). «Guardar» escribe DIRECTO al documento
 * que la usuaria creó en el selector del sistema: ninguna copia intermedia;
 * si la escritura falla a medias se intenta borrar ese documento para no
 * dejar un archivo roto en su carpeta. «Compartir» escribe al temporal de
 * [ExportStaging] y, si algo falla o la sesión se cerró durante la escritura,
 * lo elimina antes de relanzar. Los restos de una muerte de proceso los barre
 * [ExportStagingSweeper] al arrancar y el cleaner de sesión al cerrar.
 *
 * Todo fallo se traduce a [Result] (§3) y nada se registra en logs: ni la
 * contraseña ni el contenido pasan por ningún mensaje (regla 4).
 */
@Singleton
class LocalJournalExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: ExportDocumentWriter,
    private val staging: ExportStaging,
    private val opener: OutputStreamOpener
) : JournalExportRepository {
    override suspend fun exportTo(targetUri: String, document: ExportDocument, password: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sink = opener.open(targetUri) ?: throw IOException("destination unavailable")
                try {
                    sink.use { writer.write(document, password, it) }
                } catch (error: Throwable) {
                    discardDocument(targetUri)
                    throw error
                }
            }
        }

    override suspend fun stage(document: ExportDocument, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = staging.create(ExportPolicy.fileName(document.createdOn))
                try {
                    file.outputStream().use { writer.write(document, password, it) }
                    // La escritura es bloqueante: si la sesión se cerró mientras
                    // tanto (nube, inactividad), el cleaner ya barrió y este
                    // archivo nacería DESPUÉS del barrido — se elimina aquí.
                    coroutineContext.ensureActive()
                } catch (error: Throwable) {
                    staging.clear()
                    throw error
                }
                staging.uriFor(file).toString()
            }
        }

    override suspend fun discard(targetUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { discardDocument(targetUri) }
    }

    override suspend fun clearStaged(): Result<Unit> = withContext(Dispatchers.IO) { runCatching { staging.clear() } }

    /** Mejor esfuerzo: el documento vacío o a medio escribir no debe quedar en la carpeta elegida. */
    private fun discardDocument(targetUri: String) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(targetUri)) }
    }
}

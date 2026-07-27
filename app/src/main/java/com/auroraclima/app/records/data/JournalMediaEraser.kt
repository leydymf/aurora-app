package com.auroraclima.app.records.data

import android.content.Context
import com.auroraclima.app.core.security.KeystoreMediaCipher
import com.auroraclima.app.core.security.LocalAccountDataEraser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Aporte de records a la eliminación total (RF-20, RF-33): el almacén de
 * adjuntos cifrados, la caché de visualización y cualquier captura temporal
 * dejan de existir. La llave se destruye AQUÍ MISMO como primer paso
 * (auditoría E1 B-1): el multibinding de erasers no promete orden entre
 * dueños, así que «la llave muere antes que los datos» debe garantizarse
 * dentro del propio eraser — si el borrado se interrumpe, lo que quede es
 * texto cifrado sin llave (cripto-borrado). `MediaKeyEraser` (core/security)
 * queda como redundancia idempotente.
 */
@Singleton
class JournalMediaEraser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AttachmentStore,
    private val captureBuffer: CaptureBuffer,
    private val mediaCipher: KeystoreMediaCipher
) : LocalAccountDataEraser {
    override suspend fun erase() {
        withContext(Dispatchers.IO) {
            // Mejor esfuerzo por paso, como el resto de erasers locales; la
            // llave PRIMERO (cripto-borrado estructural).
            runCatching { mediaCipher.destroyKey() }
            runCatching { store.deleteAll() }
            runCatching { captureBuffer.clear() }
            // Temporales solo-cifrados del viaje de evidencias (RF-39,
            // auditoría D2 M-2): RF-20 promete que nada sobreviva.
            runCatching {
                File(context.cacheDir, FirestoreJournalMediaSyncRepository.RELAY_DIRECTORY).deleteRecursively()
            }
        }
    }
}

package com.auroraclima.app.records.data

import android.content.Context
import com.auroraclima.app.core.security.SensitiveStateCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Limpieza de sesión de los temporales de adjuntos (RF-33; auditoría E1 M-2):
 * todo cierre — salida rápida, inactividad, background prolongado — barre la
 * caché de visualización (restos de un visor de PDF interrumpido) y cualquier
 * captura pendiente. Complementa al barrido del editor: sin este cleaner, un
 * resto podía sobrevivir entre sesiones si la usuaria no volvía al editor.
 *
 * Corre en el hilo principal como el resto de cleaners (SessionManager es
 * main-confined); son un par de archivos pequeños en caché privada — mismo
 * costo aceptado que AuthSessionCleaner. Sin logs (regla 4 de CLAUDE.md).
 */
@Singleton
class JournalMediaSessionCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: AttachmentStore,
    private val captureBuffer: CaptureBuffer
) : SensitiveStateCleaner {
    override fun onSessionCleanup() {
        runCatching { store.clearViewCache() }
        runCatching { captureBuffer.clear() }
        // Temporales SOLO-CIFRADOS del viaje de evidencias (RF-39, revisión D2
        // M-5): los finally los borran en el camino normal; esto barre restos
        // de una muerte de proceso.
        runCatching {
            File(context.cacheDir, FirestoreJournalMediaSyncRepository.RELAY_DIRECTORY).deleteRecursively()
        }
    }
}

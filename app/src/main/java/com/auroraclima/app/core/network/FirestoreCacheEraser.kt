package com.auroraclima.app.core.network

import com.auroraclima.app.core.security.LocalAccountDataEraser
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aporte de core/network a la eliminación total (RF-20): la caché persistente
 * del SDK de nube guarda en claro rutas con el uid y los blobs sincronizados
 * (riesgo P-22); aquí se vacía por completo. `clearPersistence` exige detener
 * antes la instancia.
 *
 * Costo aceptado y documentado: la instancia inyectada queda inválida hasta
 * reiniciar el proceso. Tras el borrado la app es solo la fachada de clima (que
 * no usa la nube) y todo consumidor traduce fallos a Result con error genérico,
 * así que la degradación no delata nada; el reingreso normal tras reabrir la
 * app funciona con instancia fresca.
 */
@Singleton
class FirestoreCacheEraser @Inject constructor(
    private val firestore: FirebaseFirestore
) : LocalAccountDataEraser {
    override suspend fun erase() {
        // Un terminate() que falle (p. ej. instancia ya detenida en el reintento
        // del arranque) no debe impedir intentar el vaciado (auditoría H-3).
        runCatching { firestore.terminate().awaitCompletion() }
        firestore.clearPersistence().awaitCompletion()
    }
}

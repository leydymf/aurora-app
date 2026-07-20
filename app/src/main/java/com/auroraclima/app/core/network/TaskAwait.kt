package com.auroraclima.app.core.network

import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Puente Task→coroutine hecho a mano para no sumar la dependencia
 * kotlinx-coroutines-play-services (criterios A2-1; mismo motivo que en el
 * servicio de auth de accounts). A diferencia de la sync de mejor esfuerzo
 * (RF-37), quien llama aquí SÍ necesita la confirmación del servidor — la
 * eliminación total (RF-20) no puede darse por hecha con un encolado local.
 */
suspend fun <T> Task<T>.awaitCompletion(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> continuation.resume(value) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    // Un Task cancelado no pasa por éxito ni fallo: sin esto la corrutina
    // quedaría suspendida para siempre (revisión de arquitectura RF-20).
    addOnCanceledListener { continuation.cancel() }
}

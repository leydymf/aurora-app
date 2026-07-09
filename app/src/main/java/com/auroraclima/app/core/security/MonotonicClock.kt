package com.auroraclima.app.core.security

/**
 * Reloj monótono en milisegundos que sigue avanzando durante la suspensión profunda
 * del dispositivo (en producción, `SystemClock.elapsedRealtime`). Interfaz propia
 * para que [SessionManager] sea comprobable por unidad con tiempo simulado.
 */
fun interface MonotonicClock {
    fun nowMs(): Long
}

package com.auroraclima.app.core.security

/**
 * Conteo puro de pantallas seguras visibles (RNF-02). Durante una transición de
 * navegación entre dos pantallas seguras ambas conviven en composición; sin el
 * conteo, la salida de la primera apagaría la bandera mientras la segunda sigue
 * visible. Separado de la manipulación del Window para poder probarse por unidad.
 */
internal class SecureFlagLedger {
    private var activeCount = 0

    /** La bandera debe estar encendida mientras haya al menos una pantalla segura. */
    val isSecure: Boolean
        @Synchronized get() = activeCount > 0

    @Synchronized
    fun acquire() {
        activeCount++
    }

    @Synchronized
    fun release() {
        if (activeCount > 0) activeCount--
    }
}

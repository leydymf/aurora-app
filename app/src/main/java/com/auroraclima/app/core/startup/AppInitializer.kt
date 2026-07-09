package com.auroraclima.app.core.startup

/**
 * Tarea de arranque de la app; se ejecuta fuera del hilo principal al crearse el
 * proceso. Los build types aportan implementaciones vía multibinding de Hilt.
 */
fun interface AppInitializer {
    suspend fun initialize()
}

package com.auroraclima.app.core.startup

/**
 * Tarea de arranque de la app; se ejecuta fuera del hilo principal al crearse el
 * proceso. Los build types y los dueños de datos (core/security con la
 * recuperación de borrados pendientes, records con el barrido del temporal de
 * exportación) aportan implementaciones vía multibinding de Hilt.
 */
fun interface AppInitializer {
    suspend fun initialize()
}

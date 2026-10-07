package com.auroraclima.app.records.data.export

import com.auroraclima.app.core.startup.AppInitializer
import javax.inject.Inject

/**
 * Barrido de arranque del temporal de exportación (RF-45; auditoría H-1): si
 * el proceso murió con el selector de apps abierto, o la usuaria no volvió a
 * entrar al espacio interno, el PDF (ya cifrado con su contraseña) no debe
 * esperar en la caché a la próxima sesión. Idempotente y sin logs.
 */
class ExportStagingSweeper @Inject constructor(
    private val staging: ExportStaging
) : AppInitializer {
    override suspend fun initialize() {
        runCatching { staging.clear() }
    }
}

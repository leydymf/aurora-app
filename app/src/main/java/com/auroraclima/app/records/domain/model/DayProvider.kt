package com.auroraclima.app.records.domain.model

import java.time.LocalDate

/**
 * Fecha civil de hoy, inyectable para que la exportación (RF-45) sea
 * determinista en tests: nombra el archivo (`documento_AAAA-MM-DD.pdf`) y
 * fecha el PDF. Mismo criterio que el `WallClock` de accounts, redeclarado
 * aquí porque las features no se importan entre sí (§2).
 */
fun interface DayProvider {
    fun today(): LocalDate
}

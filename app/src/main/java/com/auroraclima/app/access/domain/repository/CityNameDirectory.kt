package com.auroraclima.app.access.domain.repository

/**
 * Directorio local de nombres de ciudad para la regla «la clave no puede ser el
 * nombre de una ciudad real» (RF-08 criterio 2). La consulta es completamente
 * offline y el candidato jamás sale del dispositivo: verificarlo contra una API
 * delataría el momento de creación de la clave (P-12, RNF-05).
 */
interface CityNameDirectory {
    /** True si el candidato coincide (sin tildes ni mayúsculas) con una ciudad conocida. */
    suspend fun isRealCity(name: String): Boolean
}

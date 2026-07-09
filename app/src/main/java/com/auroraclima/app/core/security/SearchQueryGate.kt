package com.auroraclima.app.core.security

/**
 * Puerta del espacio interno en la barra de búsqueda de ciudades (RF-05, RF-12).
 *
 * `weather` solo conoce esta interfaz; la implementación vive en `access`
 * (inversión de dependencias: las features no se importan entre sí, §2 de
 * ARQUITECTURA.md). Ante [SearchGateResult.PassThrough] quien consume la
 * interfaz no debe reaccionar de ninguna forma observable: la respuesta
 * visible tiene que ser idéntica a una búsqueda de ciudad normal (RF-13).
 */
interface SearchQueryGate {
    suspend fun inspect(query: String): SearchGateResult
}

sealed interface SearchGateResult {
    /** El texto no es un código: tratar como búsqueda de ciudad normal (RF-13). */
    data object PassThrough : SearchGateResult

    /** Código de activación válido y aún sin código de usuaria: abrir el registro (RF-05). */
    data object OpenActivation : SearchGateResult

    /** Código secreto de la usuaria: abrir el acceso al espacio interno (RF-12). */
    data object OpenAccess : SearchGateResult
}

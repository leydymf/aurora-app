package com.auroraclima.app.core.security

/**
 * Identidad de la cuenta con sesión vigente. Interfaz mínima en core/security
 * para que los componentes de llaves (RF-39) conozcan el `uid` dueño sin
 * importar el SDK del proveedor — la implementación vive junto a las instancias
 * de Firebase en `core/network` (§2).
 */
fun interface AccountIdentity {
    /** `uid` de la sesión vigente, o null si no hay sesión. */
    fun currentUid(): String?
}

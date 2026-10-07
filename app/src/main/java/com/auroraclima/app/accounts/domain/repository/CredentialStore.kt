package com.auroraclima.app.accounts.domain.repository

/**
 * Credenciales de la cuenta guardadas cifradas en el dispositivo (P-31 opción C,
 * acta 2026-08-15): permiten el reingreso silencioso — el código correcto abre
 * el espacio interno y la sesión del proveedor se confirma en segundo plano sin
 * volver a pedir la contraseña — y el acceso local sin red (RNF-08).
 *
 * Contrato de dominio, Kotlin puro (§2): la implementación cifra con una llave
 * no exportable del Keystore y NUNCA persiste nada en claro (el riesgo H-3
 * original era justamente el SDK guardando tokens legibles).
 *
 * Ciclo de vida (diseño aprobado por la usuaria, 2026-08-15): nace en cada
 * confirmación de contraseña (login RF-15, creación RF-07), se re-escribe al
 * cambiar la contraseña, y se destruye en el cierre de sesión explícito (RF-19),
 * en la eliminación de cuenta (RF-20), al detectar credencial inválida y al
 * encontrar un blob ilegible. La salida rápida y la inactividad NO lo tocan.
 */
interface CredentialStore {
    /** Guarda (o reemplaza) las credenciales selladas. Puede lanzar; quien llama decide si es mejor esfuerzo. */
    fun save(email: String, password: String)

    /**
     * Credenciales vigentes, o null si no hay blob o no es legible. Un blob
     * presente que no descifra se DESTRUYE y se trata como ausente — divergencia
     * deliberada del criterio de `LocalDekStore` (que lanza): aquí la ruta de
     * recuperación completa existe (login manual) y no hay pérdida escondida.
     */
    fun load(): StoredCredentials?

    /** True si hay un blob guardado (sin descifrarlo). */
    fun exists(): Boolean

    /** Destruye la llave envolvente y el blob. Idempotente; nunca lanza. */
    fun erase()
}

/** Par correo/contraseña recién descifrado; vive solo en memoria (P-35/P-47b). */
data class StoredCredentials(val email: String, val password: String) {
    /**
     * Nunca imprime sus campos: la plantilla del `toString` generado sobrevive a R8 y
     * un `"$credentials"`, un mensaje de excepción o el depurador filtrarían la
     * contraseña (auditoría 2026-10-05 M-1; mismo criterio que `JournalExportUiState`).
     */
    override fun toString(): String = "StoredCredentials(…)"
}

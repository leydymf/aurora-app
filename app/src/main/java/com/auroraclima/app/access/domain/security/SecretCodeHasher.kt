package com.auroraclima.app.access.domain.security

/**
 * Hash con salt de los códigos de acceso. La implementación usa una llave
 * no exportable del Android Keystore (regla 5 de CLAUDE.md); el dominio solo
 * conoce este contrato para seguir siendo Kotlin puro y testeable.
 */
interface SecretCodeHasher {
    /** Salt aleatorio nuevo para registrar un código. */
    fun newSalt(): ByteArray

    /** Hash del código con el salt dado; determinista para un mismo par (salt, código). */
    fun hash(salt: ByteArray, code: String): ByteArray
}

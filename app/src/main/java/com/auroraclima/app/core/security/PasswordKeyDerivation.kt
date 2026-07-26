package com.auroraclima.app.core.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derivación de una llave a partir de la contraseña de la cuenta (RF-39, P-58).
 *
 * Es una interfaz para que la máquina de estados de llaves se pruebe en JVM con
 * una derivación instantánea: la real cuesta ~1-3 s en gama baja a propósito
 * (ese costo ES la defensa) y multiplicaría el tiempo de la suite.
 */
fun interface PasswordKeyDerivation {
    /**
     * Deriva [KEY_SIZE_BITS] bits desde [password] con la [salt] y las
     * [iterations] indicadas. Determinista: misma entrada, misma llave.
     */
    fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray

    companion object {
        const val KEY_SIZE_BITS = 256
    }
}

/**
 * PBKDF2-HMAC-SHA256 de la plataforma (`javax.crypto`, disponible desde antes
 * de minSdk 26): cero dependencias nuevas — Argon2id habría exigido binarios
 * nativos por ABI (otra `.so` atípica en una app de clima, hermana de P-37a) o
 * BouncyCastle (~7 MB), ambos contra A2-1. Queda documentado como mejora futura
 * en el ADR de RF-39.
 *
 * Las iteraciones viajan junto al sobre (no aquí): el material ya publicado
 * sigue abriéndose si el parámetro por defecto sube en el futuro.
 */
@Singleton
class Pbkdf2PasswordKeyDerivation @Inject constructor() : PasswordKeyDerivation {
    override fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, PasswordKeyDerivation.KEY_SIZE_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            // Higiene: la copia interna de la contraseña no queda viva en el spec.
            spec.clearPassword()
        }
    }

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}

package com.auroraclima.app.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sobre de la llave de contenido (RF-39, esquema de llave envuelta): una DEK
 * aleatoria cifra los registros y aquí se envuelve con la KEK derivada de la
 * contraseña de la cuenta ([PasswordKeyDerivation]) para poder viajar a la nube.
 * Quien conozca la contraseña abre el sobre EN CUALQUIER dispositivo; el
 * proyecto de Firebase solo guarda material indescifrable sin ella.
 *
 * El código secreto NO participa en la derivación: puede cambiarse (RF-09) sin
 * re-envolver nada — misma decisión que desacopló la passphrase de SQLCipher
 * (ADR 2026-07-10).
 *
 * La verificación de la contraseña es el propio tag GCM: una contraseña
 * equivocada y un sobre corrupto fallan de forma indistinguible (sin oráculos).
 * Kotlin puro + `javax.crypto`: se prueba en JVM con vectores conocidos.
 */
class PasswordKeyWrapper(
    private val keyDerivation: PasswordKeyDerivation,
    private val iterations: Int = DEFAULT_ITERATIONS
) {
    /** Envuelve [dek] con una KEK derivada de [password]; salt y nonce frescos por sobre. */
    fun wrap(dek: ByteArray, password: String): WrappedKeyEnvelope {
        val salt = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val kek = keyDerivation.derive(password, salt, iterations)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, KEY_ALGORITHM))
        return WrappedKeyEnvelope(
            ciphertext = cipher.doFinal(dek),
            nonce = cipher.iv,
            salt = salt,
            iterations = iterations
        )
    }

    /**
     * Abre el sobre con [password]. Lanza si la contraseña no corresponde o el
     * material está corrupto — ambos casos son el mismo fallo del tag GCM y el
     * llamador los trata igual: «el sobre no abre».
     */
    fun unwrap(envelope: WrappedKeyEnvelope, password: String): ByteArray {
        val kek = keyDerivation.derive(password, envelope.salt, envelope.iterations)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(kek, KEY_ALGORITHM),
            GCMParameterSpec(TAG_SIZE_BITS, envelope.nonce)
        )
        return cipher.doFinal(envelope.ciphertext)
    }

    companion object {
        /**
         * Recomendación OWASP vigente para PBKDF2-HMAC-SHA256. El costo (~1-3 s
         * en gama baja) se paga solo en eventos raros — primer arranque,
         * dispositivo nuevo, cambio o restablecimiento de contraseña —, nunca en
         * el login corriente (el sobre local del Keystore evita la derivación).
         */
        const val DEFAULT_ITERATIONS = 600_000

        const val SALT_SIZE_BYTES = 16

        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val TAG_SIZE_BITS = 128
    }
}

/**
 * Sobre listo para publicarse: nada de aquí revela contenido ni contraseña —
 * el salt y el nonce son aleatorios públicos y las iteraciones, un parámetro.
 * Es exactamente lo que viaja al documento de llaves de la nube, más nada.
 */
data class WrappedKeyEnvelope(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
    val salt: ByteArray,
    val iterations: Int
) {
    // equals/hashCode por contenido (son ByteArray): útil en pruebas de round-trip.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WrappedKeyEnvelope) return false
        return ciphertext.contentEquals(other.ciphertext) &&
            nonce.contentEquals(other.nonce) &&
            salt.contentEquals(other.salt) &&
            iterations == other.iterations
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + iterations
        return result
    }
}

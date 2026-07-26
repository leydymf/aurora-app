package com.auroraclima.app.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifrado del payload de sync con la DEK del respaldo recuperable (RF-39):
 * reemplaza a `KeystorePayloadCipher` como [PayloadCipher] — la llave ya no
 * está atada al dispositivo, sino gobernada por [SyncKeyManager] (recuperable
 * en cualquier aparato con la contraseña). Mismo perfil criptográfico de la
 * casa: AES/GCM/NoPadding, 256 bits, tag de 128, nonce fresco por operación.
 *
 * Sin DEK establecida (sesión sobreviviente a una muerte de proceso en un
 * dispositivo aún sin sobre) lanza: el repositorio lo traduce a `Result` y el
 * push queda pendiente para el reconciliador del siguiente login (§3).
 */
@Singleton
class DekPayloadCipher @Inject constructor(
    private val keyManager: SyncKeyManager
) : PayloadCipher {
    override fun seal(plaintext: ByteArray): SealedPayload {
        val nonce = ByteArray(NONCE_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, requireKey(), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        return SealedPayload(ciphertext = cipher.doFinal(plaintext), iv = nonce)
    }

    override fun open(sealed: SealedPayload): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, requireKey(), GCMParameterSpec(TAG_SIZE_BITS, sealed.iv))
        return cipher.doFinal(sealed.ciphertext)
    }

    // Excepción neutra y sin mensaje (regla 4): se traduce aguas arriba.
    private fun requireKey(): SecretKeySpec {
        val dek = keyManager.currentDek() ?: throw IllegalStateException()
        return SecretKeySpec(dek, KEY_ALGORITHM)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALGORITHM = "AES"
        const val NONCE_SIZE_BYTES = 12
        const val TAG_SIZE_BITS = 128
    }
}

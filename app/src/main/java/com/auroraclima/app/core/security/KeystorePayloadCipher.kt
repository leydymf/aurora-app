package com.auroraclima.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifrado del payload de sincronización con una llave AES-GCM no exportable del
 * Android Keystore (RF-37, RNF-01). La llave se genera en el primer uso y NUNCA
 * sale del dispositivo: el proyecto de Firebase solo recibe blobs opacos.
 *
 * Mismo patrón criptográfico que `DatabaseKeyManager` (AES/GCM/NoPadding, tag de
 * 128 bits, nonce nuevo por operación) pero con alias propio y separado: la
 * passphrase de la BD y este payload no comparten llave (una responsabilidad,
 * una llave). El alias es neutro (RNF-05), como `local_store_key`.
 *
 * Consecuencia aceptada (decisión de sesión, RF-39 diferido): al estar ligada al
 * dispositivo, el blob en la nube solo es descifrable en este mismo teléfono; una
 * reinstalación o un dispositivo nuevo no puede restaurarlo. La restauración
 * multi-dispositivo (RF-39) requerirá una llave recuperable y queda documentada
 * como trabajo futuro.
 */
@Singleton
class KeystorePayloadCipher @Inject constructor() : PayloadCipher {
    override fun seal(plaintext: ByteArray): SealedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val ciphertext = cipher.doFinal(plaintext)
        return SealedPayload(ciphertext = ciphertext, iv = cipher.iv)
    }

    override fun open(sealed: SealedPayload): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(TAG_SIZE_BITS, sealed.iv))
        return cipher.doFinal(sealed.ciphertext)
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        // Alias neutro y separado del de la BD (RNF-05).
        const val KEY_ALIAS = "local_sync_key"

        const val KEY_SIZE_BITS = 256
        const val TAG_SIZE_BITS = 128
    }
}

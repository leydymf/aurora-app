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
 * LEGADO desde RF-39: los payloads se sellan ahora con la DEK recuperable
 * ([DekPayloadCipher] es el [PayloadCipher] inyectado) porque esta llave, al
 * estar ligada al dispositivo, hacía irrestaurable el respaldo en cualquier
 * otro aparato. La clase permanece solo para que RF-20 destruya el alias en
 * instalaciones que suban desde el esquema v1 (`SyncKeyEraser`); ningún camino
 * de lectura la usa — los blobs v1 se re-suben desde la BD local, no se
 * descifran de la nube.
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

    /**
     * Destruye la llave del payload (RF-20): todo blob restante — incluida la
     * caché local del SDK de nube — queda como texto cifrado sin llave.
     */
    fun destroyKey() {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
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

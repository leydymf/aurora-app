package com.auroraclima.app.access.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.auroraclima.app.access.domain.security.SecretCodeHasher
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HMAC-SHA256 sobre (salt ‖ código) con una llave no exportable generada en el
 * Android Keystore: sin la llave del dispositivo el hash almacenado no permite
 * ataques de diccionario fuera de línea (RNF-01, regla 5 de CLAUDE.md).
 */
@Singleton
class KeystoreSecretCodeHasher @Inject constructor() : SecretCodeHasher {
    override fun newSalt(): ByteArray = ByteArray(SALT_SIZE_BYTES).also { SecureRandom().nextBytes(it) }

    override fun hash(salt: ByteArray, code: String): ByteArray {
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(obtainKey())
        mac.update(salt)
        return mac.doFinal(code.toByteArray(Charsets.UTF_8))
    }

    @Synchronized
    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        // Alias neutro: los alias del keystore son visibles para herramientas de
        // inspección del propio uid (RNF-05).
        private const val KEY_ALIAS = "local_index_key"
        private const val SALT_SIZE_BYTES = 16
    }
}

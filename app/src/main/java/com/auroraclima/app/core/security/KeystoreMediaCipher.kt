package com.auroraclima.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifrado por stream de adjuntos con una llave AES-GCM no exportable del
 * Android Keystore (RF-33, RNF-01). Mismo patrón criptográfico que
 * [KeystorePayloadCipher] y [DatabaseKeyManager] (AES/GCM/NoPadding, tag de
 * 128 bits, nonce nuevo por archivo) pero con alias propio y separado —
 * una responsabilidad, una llave — y streams en vez de arrays: un adjunto
 * puede pesar varios MB y nunca debe cargarse entero en memoria.
 *
 * El formato en disco es `[nonce de 12 bytes][sellado GCM]`. El nonce no es
 * secreto; sin la llave del Keystore el resto es ilegible.
 *
 * Consecuencia aceptada (misma que la BD y la sync): la llave está ligada al
 * dispositivo. Las evidencias son SOLO LOCALES por política (D0 §5.3), así
 * que aquí no hay blob remoto que restaurar — reinstalar las pierde, y eso
 * está documentado como parte de la política.
 */
@Singleton
class KeystoreMediaCipher @Inject constructor() : MediaCipher {
    override fun sealing(target: OutputStream): OutputStream {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        target.write(cipher.iv)
        return CipherOutputStream(target, cipher)
    }

    override fun opening(source: InputStream): InputStream {
        val iv = ByteArray(IV_SIZE_BYTES)
        var read = 0
        while (read < iv.size) {
            val count = source.read(iv, read, iv.size - read)
            if (count < 0) throw EOFException()
            read += count
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
        return CipherInputStream(source, cipher)
    }

    /**
     * Destruye la llave de adjuntos (RF-20): todo archivo restante del almacén
     * queda como texto cifrado sin llave (cripto-borrado).
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

        // Alias neutro y separado de local_store_key/local_sync_key (RNF-05).
        const val KEY_ALIAS = "local_media_key"

        const val KEY_SIZE_BITS = 256
        const val TAG_SIZE_BITS = 128
        const val IV_SIZE_BYTES = 12
    }
}

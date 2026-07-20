package com.auroraclima.app.core.database

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Passphrase de la base de datos cifrada del espacio interno (RNF-01, P-03).
 *
 * Se genera aleatoria en el primer uso (32 bytes de [SecureRandom]) y se guarda
 * envuelta con una llave AES-GCM no exportable del Android Keystore: sin la
 * llave del dispositivo el blob almacenado no sirve de nada. Nunca se deriva de
 * la clave de la usuaria — su clave puede cambiar (RF-09) sin recifrar la BD.
 *
 * Nombres de archivo, claves y alias neutros, como los existentes de `access`
 * (RNF-05): nada delata su propósito al inspeccionar el almacenamiento.
 *
 * Si el blob existe pero no puede descifrarse (corrupción, llave invalidada),
 * se lanza: regenerar la passphrase en silencio dejaría ilegible la BD ya
 * cifrada — es la misma pérdida, pero escondida. Quien consuma la BD (records/)
 * traduce el fallo a `Result` (§3 de ARQUITECTURA.md); la recuperación para
 * ese caso extremo queda con la sincronización (RF-37/RF-39).
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun obtainPassphrase(): ByteArray {
        readStoredPassphrase()?.let { return it }
        val passphrase = ByteArray(PASSPHRASE_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        storePassphrase(passphrase)
        return passphrase
    }

    /**
     * Eliminación total (RF-20): destruye primero la llave envolvente — desde ese
     * instante el blob y la BD son ilegibles aunque el resto del borrado se
     * interrumpa — y después el blob mismo. El siguiente primer uso genera una
     * passphrase nueva, como en una instalación limpia.
     */
    @Synchronized
    fun erase() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
        preferences.edit().clear().commit()
    }

    private fun readStoredPassphrase(): ByteArray? {
        val iv = preferences.getString(IV_KEY, null) ?: return null
        val blob = preferences.getString(BLOB_KEY, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainWrappingKey(), GCMParameterSpec(TAG_SIZE_BITS, decode(iv)))
        return cipher.doFinal(decode(blob))
    }

    private fun storePassphrase(passphrase: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainWrappingKey())
        val blob = cipher.doFinal(passphrase)
        val written = preferences.edit()
            .putString(IV_KEY, encode(cipher.iv))
            .putString(BLOB_KEY, encode(blob))
            .commit()
        // Un fallo silencioso dejaría una BD cifrada con passphrase irrecuperable
        // en el siguiente arranque (mismo criterio que la auditoría D5 H-2).
        if (!written) throw IOException()
    }

    private fun obtainWrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(WRAPPING_KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        // Alias y nombres neutros, mismo criterio que `local_index_key` y
        // `search_index` de access (RNF-05).
        private const val KEY_ALIAS = "local_store_key"
        private const val PREFS_NAME = "store_meta"
        private const val IV_KEY = "s0"
        private const val BLOB_KEY = "s1"

        private const val PASSPHRASE_SIZE_BYTES = 32
        private const val WRAPPING_KEY_SIZE_BITS = 256
        private const val TAG_SIZE_BITS = 128
    }
}

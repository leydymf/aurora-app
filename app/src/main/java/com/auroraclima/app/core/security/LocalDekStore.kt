package com.auroraclima.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copia local de la DEK del respaldo (RF-39, «sobre doble»): la misma llave que
 * viaja a la nube envuelta por la contraseña se guarda aquí envuelta con una
 * llave AES-GCM no exportable del Android Keystore — patrón calcado de
 * `DatabaseKeyManager`. Gracias a esta copia el login corriente NO deriva nada
 * (cero KDF, cero latencia añadida) y la sync funciona también en la sesión que
 * sobrevive a una muerte de proceso, donde no hay contraseña en memoria.
 *
 * Junto a la DEK se guardan la GENERACIÓN del sobre publicado y el `uid` dueño:
 * la generación detecta que la contraseña cambió desde otro lugar (desajuste
 * con el documento de llaves → auto-reparación o re-derivación) y el `uid`
 * impide que la DEK de una cuenta anterior en este teléfono contamine a otra.
 *
 * En reposo la exposición es idéntica a la histórica `local_sync_key` (P-37b:
 * root puede invocar la llave del Keystore) — sin regresión respecto al statu
 * quo. Nombres y alias neutros (RNF-05), como `store_meta`/`sync_meta`.
 *
 * Un blob presente que no descifra LANZA en vez de tratarse como ausente
 * (mismo criterio que `DatabaseKeyManager`): regenerar en silencio publicaría
 * un respaldo nuevo escondiendo la pérdida; quien consume traduce a `Result`.
 */
@Singleton
class LocalDekStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** DEK local con la generación del sobre que la publicó. */
    data class StoredDek(val dek: ByteArray, val generation: Long) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is StoredDek) return false
            return dek.contentEquals(other.dek) && generation == other.generation
        }

        override fun hashCode(): Int = 31 * dek.contentHashCode() + generation.toInt()
    }

    /**
     * DEK guardada para [uid], o null si no hay copia o pertenece a otra cuenta
     * (una cuenta nueva en este teléfono jamás hereda la llave de la anterior).
     */
    @Synchronized
    fun read(uid: String): StoredDek? {
        if (preferences.getString(OWNER_KEY, null) != uid) return null
        val iv = preferences.getString(IV_KEY, null) ?: return null
        val blob = preferences.getString(BLOB_KEY, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainWrappingKey(), GCMParameterSpec(TAG_SIZE_BITS, decode(iv)))
        return StoredDek(
            dek = cipher.doFinal(decode(blob)),
            generation = preferences.getLong(GENERATION_KEY, 0)
        )
    }

    /** Guarda (o reemplaza) la copia local de la DEK de [uid] con su [generation]. */
    @Synchronized
    fun write(uid: String, dek: ByteArray, generation: Long) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainWrappingKey())
        val blob = cipher.doFinal(dek)
        val written = preferences.edit()
            .putString(OWNER_KEY, uid)
            .putString(IV_KEY, encode(cipher.iv))
            .putString(BLOB_KEY, encode(blob))
            .putLong(GENERATION_KEY, generation)
            .commit()
        // Un fallo silencioso dejaría el respaldo remoto adelantado respecto a lo
        // que este dispositivo cree tener (mismo criterio que DatabaseKeyManager).
        if (!written) throw IOException()
    }

    /**
     * Eliminación total (RF-20): destruye primero la llave envolvente — desde
     * ese instante el blob es ilegible aunque el resto se interrumpa — y después
     * el blob mismo. La DEK sigue recuperable desde la nube con la contraseña.
     */
    @Synchronized
    fun erase() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
        preferences.edit().clear().commit()
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

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"

        // Alias y nombres neutros — quinta llave de la casa, una responsabilidad,
        // una llave (RNF-05; mismo criterio que `local_store_key`/`local_sync_key`).
        const val KEY_ALIAS = "local_backup_key"
        const val PREFS_NAME = "backup_meta"
        const val OWNER_KEY = "b0"
        const val IV_KEY = "b1"
        const val BLOB_KEY = "b2"
        const val GENERATION_KEY = "b3"

        const val WRAPPING_KEY_SIZE_BITS = 256
        const val TAG_SIZE_BITS = 128
    }
}

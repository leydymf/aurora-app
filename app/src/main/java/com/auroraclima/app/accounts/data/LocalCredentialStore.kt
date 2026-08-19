package com.auroraclima.app.accounts.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.auroraclima.app.accounts.domain.repository.CredentialStore
import com.auroraclima.app.accounts.domain.repository.StoredCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Credenciales de la cuenta selladas con una llave AES-GCM no exportable del
 * Android Keystore (P-31 opción C) — patrón calcado de `LocalDekStore`: alias y
 * nombres de prefs neutros (RNF-05), blob base64 bajo claves de una letra,
 * indistinguible por forma de `backup_meta`/`store_meta`. Nada es legible ni
 * descifrable sin el Keystore de ESTE dispositivo.
 *
 * Un blob que no descifra o no parsea se DESTRUYE y `load()` devuelve null
 * (degradación a login manual) en lugar de lanzar — divergencia deliberada del
 * criterio de `LocalDekStore`: allí regenerar escondería la pérdida del
 * respaldo; aquí el login manual ES la recuperación completa y un crash o un
 * error visible sería peor (RF-13: nada interno asoma fallos técnicos).
 *
 * Residual documentado (P-70, hermano de P-37b): quien tenga root e invoque la
 * llave del Keystore obtiene la contraseña en claro. Sigue siendo estrictamente
 * mejor que el H-3 original (el SDK persistía tokens en claro sin cifrar).
 */
@Singleton
class LocalCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) : CredentialStore {
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    override fun save(email: String, password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainWrappingKey())
        val blob = cipher.doFinal(serialize(email, password))
        val written = preferences.edit()
            .putString(IV_KEY, encode(cipher.iv))
            .putString(BLOB_KEY, encode(blob))
            .commit()
        // Quien llama decide si el guardado es de mejor esfuerzo; aquí un fallo
        // silencioso dejaría un blob a medias que el siguiente load destruiría.
        if (!written) throw IOException()
    }

    @Synchronized
    override fun load(): StoredCredentials? {
        val iv = preferences.getString(IV_KEY, null) ?: return null
        val blob = preferences.getString(BLOB_KEY, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, obtainWrappingKey(), GCMParameterSpec(TAG_SIZE_BITS, decode(iv)))
            deserialize(cipher.doFinal(decode(blob)))
        } catch (broken: Exception) {
            // Blob adulterado, llave perdida o formato desconocido: se destruye
            // y el flujo degrada a login manual sin error visible (ver KDoc).
            erase()
            null
        }
    }

    @Synchronized
    override fun exists(): Boolean = preferences.contains(BLOB_KEY)

    /**
     * Destruye primero la llave envolvente — desde ese instante el blob es
     * ilegible aunque el resto se interrumpa — y después el blob mismo
     * (cripto-borrado, mismo orden que los erasers de RF-20).
     */
    @Synchronized
    override fun erase() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        }
        preferences.edit().clear().commit()
    }

    private fun serialize(email: String, password: String): ByteArray {
        val emailBytes = email.toByteArray(Charsets.UTF_8)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        return ByteBuffer.allocate(1 + Int.SIZE_BYTES + emailBytes.size + passwordBytes.size)
            .put(SCHEMA_VERSION)
            .putInt(emailBytes.size)
            .put(emailBytes)
            .put(passwordBytes)
            .array()
    }

    private fun deserialize(plain: ByteArray): StoredCredentials {
        val buffer = ByteBuffer.wrap(plain)
        check(buffer.get() == SCHEMA_VERSION)
        val emailBytes = ByteArray(buffer.int).also { buffer.get(it) }
        val passwordBytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return StoredCredentials(
            email = String(emailBytes, Charsets.UTF_8),
            password = String(passwordBytes, Charsets.UTF_8)
        )
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

        // Alias y nombres neutros — sexta llave de la casa, una responsabilidad,
        // una llave (RNF-05; mismo criterio que `local_backup_key`/`backup_meta`).
        const val KEY_ALIAS = "local_account_key"
        const val PREFS_NAME = "account_meta"
        const val IV_KEY = "a0"
        const val BLOB_KEY = "a1"

        /** Versión del contenido del blob, autenticada por el tag GCM. */
        const val SCHEMA_VERSION: Byte = 1

        const val WRAPPING_KEY_SIZE_BITS = 256
        const val TAG_SIZE_BITS = 128
    }
}

package com.auroraclima.app.core.security

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [RecoverableMediaCipher] real: AES/GCM/NoPadding por stream con la DEK de
 * [SyncKeyManager] (RF-39). Mismo formato en tránsito que el almacén local —
 * `[nonce de 12 bytes][sellado GCM]` — pero con llave recuperable: el blob
 * troceado en la nube se abre en cualquier dispositivo que restaure la DEK.
 * A diferencia del Keystore, aquí el nonce se genera explícito ([SecureRandom])
 * porque la llave es un [SecretKeySpec] en memoria.
 */
@Singleton
class DekMediaCipher @Inject constructor(
    private val keyManager: SyncKeyManager
) : RecoverableMediaCipher {
    override fun sealing(target: OutputStream): OutputStream {
        val nonce = ByteArray(NONCE_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, requireKey(), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        target.write(nonce)
        return CipherOutputStream(target, cipher)
    }

    override fun opening(source: InputStream): InputStream {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        var read = 0
        while (read < nonce.size) {
            val count = source.read(nonce, read, nonce.size - read)
            if (count < 0) throw EOFException()
            read += count
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, requireKey(), GCMParameterSpec(TAG_SIZE_BITS, nonce))
        return CipherInputStream(source, cipher)
    }

    // Excepción neutra y sin mensaje (regla 4): se traduce aguas arriba (§3).
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

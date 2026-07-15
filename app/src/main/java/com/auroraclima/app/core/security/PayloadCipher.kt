package com.auroraclima.app.core.security

/**
 * Cifra en el cliente los bytes de un registro antes de que salgan del
 * dispositivo hacia la nube (RF-37, RNF-01): el proyecto de Firebase nunca ve
 * texto en claro. La llave vive en el Android Keystore y no sale del dispositivo
 * (ver `KeystorePayloadCipher`).
 *
 * Es una interfaz para que la capa `data` de records pueda probarse en la JVM
 * con un cifrador falso reversible — el Keystore real no existe en la JVM del
 * host (mismo criterio que `KeystoreSecretCodeHasher`, que se prueba
 * instrumentado).
 */
interface PayloadCipher {
    /** Sella [plaintext] con AES-GCM; el nonce nuevo viaja en [SealedPayload.iv]. */
    fun seal(plaintext: ByteArray): SealedPayload

    /** Abre lo sellado. Lanza si el material está corrupto o la llave se invalidó. */
    fun open(sealed: SealedPayload): ByteArray
}

/**
 * Bytes cifrados y su nonce GCM. Ninguno revela contenido: es exactamente lo que
 * viaja a Firestore (`ciphertext`/`iv` del documento), más nada.
 */
data class SealedPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    // equals/hashCode por contenido (son ByteArray): útil en pruebas de round-trip.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SealedPayload) return false
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + iv.contentHashCode()
}

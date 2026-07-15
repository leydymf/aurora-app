package com.auroraclima.app.records.data

import com.auroraclima.app.core.security.PayloadCipher
import com.auroraclima.app.core.security.SealedPayload

/**
 * Cifrador falso pero reversible para las pruebas JVM: XOR con una máscara fija.
 * El Keystore real no existe en la JVM del host —`KeystorePayloadCipher` se
 * prueba instrumentado—, pero esto basta para verificar que el repositorio (a)
 * NO deja el contenido en claro en el DTO y (b) produce algo que se puede volver
 * a abrir (round-trip).
 */
class ReversiblePayloadCipher : PayloadCipher {
    override fun seal(plaintext: ByteArray): SealedPayload = SealedPayload(plaintext.mask(), IV)

    override fun open(sealed: SealedPayload): ByteArray = sealed.ciphertext.mask()

    private fun ByteArray.mask(): ByteArray = ByteArray(size) {
        (this[it].toInt() xor MASK).toByte()
    }

    private companion object {
        const val MASK = 0x5A
        val IV = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
    }
}

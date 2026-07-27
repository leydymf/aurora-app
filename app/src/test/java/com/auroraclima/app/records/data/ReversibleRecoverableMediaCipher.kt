package com.auroraclima.app.records.data

import com.auroraclima.app.core.security.RecoverableMediaCipher
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Cifrador de viaje falso pero reversible para las pruebas JVM (XOR con máscara
 * fija tras un «nonce» de relleno): el real (`DekMediaCipher`) se prueba aparte
 * con cripto real. Mismo criterio que `ReversibleMediaCipher`.
 */
class ReversibleRecoverableMediaCipher : RecoverableMediaCipher {
    override fun sealing(target: OutputStream): OutputStream {
        target.write(ByteArray(NONCE_SIZE))
        return object : FilterOutputStream(target) {
            override fun write(b: Int) {
                out.write(b xor MASK)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                val masked = ByteArray(len) { (b[off + it].toInt() xor MASK).toByte() }
                out.write(masked, 0, len)
            }
        }
    }

    override fun opening(source: InputStream): InputStream {
        var skipped = 0L
        while (skipped < NONCE_SIZE) {
            val count = source.skip(NONCE_SIZE - skipped)
            if (count <= 0) break
            skipped += count
        }
        return object : FilterInputStream(source) {
            override fun read(): Int {
                val value = `in`.read()
                return if (value < 0) value else value xor MASK
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = `in`.read(b, off, len)
                for (i in 0 until count) {
                    b[off + i] = (b[off + i].toInt() xor MASK).toByte()
                }
                return count
            }
        }
    }

    private companion object {
        const val MASK = 0x33
        const val NONCE_SIZE = 12
    }
}

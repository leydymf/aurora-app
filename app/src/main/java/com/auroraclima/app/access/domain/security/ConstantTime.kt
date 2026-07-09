package com.auroraclima.app.access.domain.security

/**
 * Comparación de arreglos en tiempo constante: recorre siempre la longitud completa,
 * sin cortocircuito en la primera diferencia, para que la duración no revele cuántos
 * bytes coinciden (RF-13; regla de seguridad §4 de ARQUITECTURA.md). La verificación
 * de tamaño no filtra información: ambos operandos son hashes de longitud fija.
 */
fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean {
    if (first.size != second.size) return false
    var difference = 0
    for (index in first.indices) {
        difference = difference or (first[index].toInt() xor second[index].toInt())
    }
    return difference == 0
}

package com.auroraclima.app.access.domain.model

/**
 * Representación almacenable de un código: solo su hash y el salt con que se calculó.
 * El código en claro jamás se persiste ni se registra (RF-08 criterio 3, regla 4 de
 * CLAUDE.md). Clase normal (no data class) para no exponer los arreglos en toString.
 */
class StoredCodeHash(
    val salt: ByteArray,
    val hash: ByteArray
)

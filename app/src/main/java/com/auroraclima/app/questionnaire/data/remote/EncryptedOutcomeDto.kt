package com.auroraclima.app.questionnaire.data.remote

/**
 * Documento que viaja a `/users/{uid}/outcomes` (RF-31 + RF-39): SOLO el
 * sellado AES-GCM del payload, su nonce y la versión de esquema — nada legible
 * (mismo contrato que `EncryptedEntryDto` del historial).
 */
data class EncryptedOutcomeDto(
    val ciphertext: String,
    val iv: String,
    val schemaVersion: Long
)

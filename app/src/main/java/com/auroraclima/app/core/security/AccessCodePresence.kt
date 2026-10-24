package com.auroraclima.app.core.security

/**
 * ¿Ya existe en este dispositivo el código de acceso de la usuaria (RF-08)?
 *
 * Mientras no exista, el dispositivo está en flujo de activación: lo que haya
 * de una cuenta (sesión del proveedor, blob de credenciales, datos restaurados)
 * viene de un registro que no terminó y nadie ha demostrado ser su dueña con un
 * código (hallazgo A de la evaluación v0.3.2). `accounts` y las features con
 * sincronización solo conocen esta interfaz; la implementación vive en `access`
 * (inversión de dependencias, §2 de ARQUITECTURA.md), como [SearchQueryGate].
 *
 * Devuelve `Result` (§3) porque cada consumidor resuelve la duda hacia SU lado
 * seguro: quien borra asume que el código existe (no destruye datos de una
 * usuaria activa) y quien sube asume que no existe (no envía nada a la nube).
 */
fun interface AccessCodePresence {
    suspend fun isSet(): Result<Boolean>
}

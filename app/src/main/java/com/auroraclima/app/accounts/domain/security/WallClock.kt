package com.auroraclima.app.accounts.domain.security

/**
 * Reloj de pared en milisegundos epoch (en producción, `System.currentTimeMillis`).
 * El bloqueo de inicio de sesión usa reloj de pared y no el monótono de
 * `core/security` porque su fecha límite se persiste y debe sobrevivir a la
 * muerte del proceso y al reinicio del dispositivo (el monótono se reinicia en
 * cada arranque). Contrapartida documentada: cambiar la hora del sistema salta
 * el bloqueo — aceptable en TRL 4, el proveedor mantiene su propio límite.
 */
fun interface WallClock {
    fun nowEpochMs(): Long
}

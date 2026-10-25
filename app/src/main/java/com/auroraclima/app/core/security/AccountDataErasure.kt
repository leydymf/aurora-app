package com.auroraclima.app.core.security

/**
 * Puntos de extensión de la eliminación total de la cuenta (RF-20). Cada feature
 * o módulo de core aporta el borrado de lo que posee vía multibinding (mismo
 * patrón que [SensitiveStateCleaner]); el orden entre fases lo impone el caso de
 * uso de accounts: primero TODO lo remoto, después la cuenta del proveedor y al
 * final lo local — lo local es la herramienta para borrar lo remoto (uid, sesión,
 * llaves), así que destruirlo antes dejaría restos imborrables en la nube.
 */

/**
 * Borra los datos de la usuaria en la nube esperando la confirmación del
 * servidor — a diferencia de la sync de mejor esfuerzo (RF-37), aquí un encolado
 * offline no basta: tras eliminar la cuenta el token queda inválido y la mutación
 * pendiente sería rechazada. Un fallo detiene la eliminación (nada local se ha
 * perdido y reintentar es idempotente).
 */
fun interface RemoteAccountDataEraser {
    suspend fun erase(): Result<Unit>
}

/**
 * Borra los datos locales que posee quien lo implementa (archivos, prefs, alias
 * del Keystore). Corre al final, cuando cuenta y nube ya no existen: es mejor
 * esfuerzo — quien lo invoca tolera el fallo de un eraser y continúa con los
 * demás, y ninguno debe registrar nada (regla 4 de CLAUDE.md). Los dueños de
 * material criptográfico destruyen su alias ANTES que sus datos: si el proceso
 * muriera a mitad, lo que quede es texto cifrado sin llave.
 *
 * También corre, con exclusiones, al descartar una activación sin terminar
 * (hallazgo A′ de v0.3.2): ver [keptOnUnfinishedActivation].
 */
fun interface LocalAccountDataEraser {
    suspend fun erase()

    /**
     * True si lo que borra NO debe caer al descartar una activación sin
     * terminar. Ese descarte borra lo que una cuenta dejó antes de que
     * existiera el código de acceso (registros restaurados, llaves, blob),
     * pero no la puerta ni el bloqueo, y nada cuyo borrado inutilice el
     * proceso en curso. Cada excepción declara su causa junto al override (§5:
     * la exención es dato, no cálculo). RF-20 corre TODOS sin mirar esta marca.
     */
    val keptOnUnfinishedActivation: Boolean get() = false
}

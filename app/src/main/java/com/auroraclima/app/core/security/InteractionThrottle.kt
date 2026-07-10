package com.auroraclima.app.core.security

/**
 * Acota la frecuencia con la que el desplazamiento reporta interacción a
 * [SessionManager.registerInteraction] (RNF-03, pendiente P-21): los deltas de
 * scroll llegan una vez por frame y reiniciar el temporizador de inactividad en
 * cada uno sería trabajo inútil — el plazo se mide en minutos. Lógica pura,
 * separada de la UI para probarse por unidad (mismo criterio que SecureFlagLedger).
 */
internal class InteractionThrottle(private val clock: MonotonicClock) {
    private var lastRegisteredMs = Long.MIN_VALUE

    /** `true` a lo sumo una vez por [MIN_INTERVAL_MS]; el primer llamado siempre pasa. */
    fun shouldRegister(): Boolean {
        val now = clock.nowMs()
        if (lastRegisteredMs != Long.MIN_VALUE && now - lastRegisteredMs < MIN_INTERVAL_MS) {
            return false
        }
        lastRegisteredMs = now
        return true
    }

    companion object {
        const val MIN_INTERVAL_MS: Long = 1_000L
    }
}

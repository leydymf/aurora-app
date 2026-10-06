package com.auroraclima.app.accounts.domain.model

/**
 * Desenlace de la solicitud de restablecimiento (RF-46).
 *
 * [Requested] es deliberadamente indistinguible: el correo exista o no, la
 * usuaria ve el mismo mensaje. Decir «esa cuenta no existe» convertiría la
 * pantalla en un oráculo para averiguar quién tiene cuenta aquí, que es lo
 * primero que intentaría quien le revise el teléfono. Es el mismo criterio del
 * inicio de sesión (RF-15 criterio 2, ADR 2026-07-10).
 *
 * [NoConnection] es lo ÚNICO que se distingue, como exige el criterio 3 de
 * RF-46 y con el mismo criterio de RF-20: sin red el correo no salió y hay que
 * poder decirlo, o la usuaria esperaría un mensaje que nunca llega. Cualquier
 * otro fallo del proveedor se pliega en [Requested]: `ERROR_TOO_MANY_REQUESTS`,
 * por ejemplo, solo se dispara con actividad de envío real, es decir cuando la
 * cuenta existe — mostrarlo aparte sería un oráculo débil pero real.
 */
sealed interface PasswordResetResult {
    data object Requested : PasswordResetResult

    data object NoConnection : PasswordResetResult

    /** Demasiados intentos seguidos: pausa temporal, como en RF-15 criterio 3. */
    data class Locked(val remainingMillis: Long) : PasswordResetResult
}

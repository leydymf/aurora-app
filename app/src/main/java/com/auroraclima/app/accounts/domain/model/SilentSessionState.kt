package com.auroraclima.app.accounts.domain.model

/** Estado observable del reingreso silencioso dentro de la sesión interna (P-31 C). */
enum class SilentSessionState {
    /** Sin sesión interna, o aún sin arrancar. */
    Idle,

    /** Confirmación en curso. */
    Checking,

    /** Operando en local: sin red o servicio caído (chip «Sin conexión»). */
    Offline,

    /** Sesión del proveedor confirmada. */
    SignedIn,

    /** Rechazo definitivo: corresponde el login manual (blob ya destruido). */
    LoginRequired
}

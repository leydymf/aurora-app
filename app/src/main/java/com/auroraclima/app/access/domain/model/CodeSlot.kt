package com.auroraclima.app.access.domain.model

/**
 * Ranuras de código de acceso. [ACTIVATION] es el código difundido por canales
 * seguros que abre el registro la primera vez (RF-05); [USER] es el código personal
 * que lo reemplaza cuando la usuaria lo crea (RF-08, RF-12).
 */
enum class CodeSlot {
    ACTIVATION,
    USER
}

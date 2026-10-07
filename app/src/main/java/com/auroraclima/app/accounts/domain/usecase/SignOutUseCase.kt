package com.auroraclima.app.accounts.domain.usecase

import com.auroraclima.app.accounts.domain.repository.CredentialStore
import javax.inject.Inject

/**
 * Cierre de sesión explícito (RF-19; caso 1 del ciclo de vida del blob, P-31
 * opción C): destruye el blob de credenciales ANTES de cerrar la sesión
 * interna, de modo que el siguiente acceso exige código + correo + contraseña
 * (criterio 1) — el comportamiento previo a la opción C. Es la única salida que
 * lo destruye: la salida rápida y la inactividad lo conservan a propósito.
 *
 * El cierre de la sesión del proveedor NO ocurre aquí: converge en la rutina
 * única de cierre (`AuthSessionCleaner` dentro de `closeSession()`), igual que
 * antes.
 */
class SignOutUseCase @Inject constructor(
    private val credentials: CredentialStore
) {
    // Síncrono a propósito: erase() es una escritura mínima (mismo criterio que
    // la marca durable de RF-20) y el orden «blob destruido → cierre» debe ser
    // observable sin carreras.
    operator fun invoke() {
        credentials.erase()
    }
}

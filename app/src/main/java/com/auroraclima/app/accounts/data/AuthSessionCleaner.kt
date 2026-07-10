package com.auroraclima.app.accounts.data

import com.auroraclima.app.core.security.SensitiveStateCleaner
import javax.inject.Inject

/**
 * El SDK de autenticación persiste su sesión (correo, tokens) en almacenamiento
 * propio de la app, en claro: un rastro ajeno a una app de clima ante una
 * inspección del dispositivo. Al cerrar la sesión del espacio interno (salida
 * rápida, inactividad, background) se cierra también la del proveedor para que
 * ese rastro no sobreviva (auditoría D3 H-3). Revisar el balance con RF-15 y
 * RF-12 («pantalla principal si hay sesión activa») al implementar el inicio
 * de sesión.
 */
class AuthSessionCleaner @Inject constructor(
    private val authService: AuthService
) : SensitiveStateCleaner {
    override fun onSessionCleanup() {
        // Nunca lanza por diseño (contrato de SensitiveStateCleaner).
        runCatching { authService.signOut() }
    }
}

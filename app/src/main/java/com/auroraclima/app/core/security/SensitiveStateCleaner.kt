package com.auroraclima.app.core.security

/**
 * Limpieza de estado sensible al cerrar la sesión del espacio interno (RF-16, RNF-03).
 *
 * Punto de extensión: cualquier componente que mantenga datos sensibles en memoria
 * fuera de un ViewModel del grafo seguro (los ViewModels los limpia el pop del back
 * stack) aporta su rutina aquí, de dos formas:
 *
 * - Singletons: `@IntoSet` sobre el multibinding declarado en
 *   [com.auroraclima.app.core.security.di.SessionModule].
 * - Objetos con ciclo de vida más corto (p. ej. un ViewModel que cachea respuestas,
 *   como hará el descarte del cuestionario, RF-28): [SessionManager.registerCleaner]
 *   al crearse y `close()` del registro devuelto en `onCleared()`.
 *
 * La rutina debe ser idempotente y jamás lanzar por diseño; si aun así falla,
 * [SessionManager] la aísla para que el resto de la limpieza continúe.
 */
fun interface SensitiveStateCleaner {
    fun onSessionCleanup()
}

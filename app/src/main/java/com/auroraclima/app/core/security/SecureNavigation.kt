package com.auroraclima.app.core.security

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation

/**
 * Grafo de navegación seguro: el ÚNICO punto donde se aplica FLAG_SECURE (RNF-02).
 * Toda pantalla interna se declara con [SecureGraphScope.screen] dentro de este grafo
 * y queda envuelta automáticamente: no hay forma de registrar una ruta interna sin la
 * bandera, ni de aplicarla pantalla por pantalla a mano ([SecureScreen] es privado).
 */
fun NavGraphBuilder.secureGraph(route: String, startDestination: String, builder: SecureGraphScope.() -> Unit) {
    navigation(startDestination = startDestination, route = route) {
        SecureGraphScope(this).builder()
    }
}

/** Ámbito de declaración de rutas dentro de [secureGraph]. */
class SecureGraphScope internal constructor(private val graph: NavGraphBuilder) {
    fun screen(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
        graph.composable(route) { entry ->
            SecureScreen { content(entry) }
        }
    }
}

/**
 * Mientras al menos una instancia esté en composición, la ventana bloquea capturas
 * de pantalla y oculta su contenido en la vista de recientes. Privado a propósito:
 * las pantallas nunca lo invocan directamente, solo [secureGraph].
 */
@Composable
private fun SecureScreen(content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        window?.let(SecureFlagCounter::acquire)
        onDispose { window?.let(SecureFlagCounter::release) }
    }
    content()
}

/**
 * Aplica el conteo de [SecureFlagLedger] sobre el Window real. `object` manual por
 * excepción registrada en la guía de arquitectura (2026-07-08): es estado del Window,
 * no lógica de negocio.
 */
private object SecureFlagCounter {
    private val ledger = SecureFlagLedger()

    fun acquire(window: Window) {
        ledger.acquire()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        ledger.release()
        if (!ledger.isSecure) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

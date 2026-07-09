package com.auroraclima.app.core.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Envoltorio único de FLAG_SECURE (RNF-02): toda pantalla interna se declara
 * dentro de este componente. Mientras al menos una [SecureScreen] esté en composición,
 * la ventana bloquea capturas de pantalla y oculta su contenido en recientes.
 */
@Composable
fun SecureScreen(content: @Composable () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findWindow()
        window?.let(SecureFlagCounter::acquire)
        onDispose { window?.let(SecureFlagCounter::release) }
    }
    content()
}

/**
 * Conteo de pantallas seguras activas. Durante una transición de navegación entre dos
 * pantallas seguras ambas conviven en composición; sin el conteo, el onDispose de la
 * saliente borraría la bandera mientras la entrante sigue visible. No se inyecta:
 * es estado del [Window], no lógica de negocio.
 */
private object SecureFlagCounter {
    private var activeCount = 0

    @Synchronized
    fun acquire(window: Window) {
        activeCount++
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    @Synchronized
    fun release(window: Window) {
        activeCount--
        if (activeCount <= 0) {
            activeCount = 0
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findWindow(): Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findWindow()
    else -> null
}

package com.auroraclima.app.core.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Observador compartido del paso a segundo plano: notifica cada ON_STOP que no
 * provenga de un cambio de configuración (la rotación no es «irse de la app») y
 * cada ON_START. Lo reutilizan [SecureBackStackGuard] (limpiar la pila) y
 * [SessionGuard] (plazo de gracia de la sesión).
 */
@Composable
internal fun AppBackgroundObserver(onStopWithoutRotation: () -> Unit, onStart: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStop = rememberUpdatedState(onStopWithoutRotation)
    val currentOnStart = rememberUpdatedState(onStart)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> currentOnStart.value()
                Lifecycle.Event.ON_STOP ->
                    if (context.findActivity()?.isChangingConfigurations != true) {
                        currentOnStop.value()
                    }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

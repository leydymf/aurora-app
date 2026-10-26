package com.auroraclima.app.core.ui

import android.content.ContentResolver
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Preferencia del sistema de reducir animaciones. «Quitar animaciones» de
 * Accesibilidad (y la opción equivalente de Opciones de desarrollador) deja la
 * escala de duración de animaciones en 0, la misma que Compose respeta en sus
 * propias animaciones.
 */
fun ContentResolver.prefersReducedMotion(): Boolean {
    val scale = runCatching {
        Settings.Global.getFloat(this, Settings.Global.ANIMATOR_DURATION_SCALE, DEFAULT_SCALE)
    }.getOrDefault(DEFAULT_SCALE)
    return scale == 0f
}

/** Se lee al entrar en composición: cambiar el ajuste a mitad de una pantalla no la reanima. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { resolver.prefersReducedMotion() }
}

private const val DEFAULT_SCALE = 1f

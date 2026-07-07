package com.auroraclima.app.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Esquema único claro: la fachada de clima es siempre clara (mockup P01).
// Sin color dinámico (Material You): reemplazaría la paleta del amanecer.
private val WeatherColorScheme = lightColorScheme(
    primary = SteelDeep,
    onPrimary = CloudWhite,
    secondary = Steel,
    onSecondary = CloudWhite,
    background = Mist,
    onBackground = Night,
    surface = CloudWhite,
    onSurface = Night,
    surfaceVariant = Sky,
    onSurfaceVariant = Night
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WeatherColorScheme,
        typography = Typography,
        content = content
    )
}

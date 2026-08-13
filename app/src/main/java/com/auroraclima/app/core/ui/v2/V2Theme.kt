package com.auroraclima.app.core.ui.v2

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Voz con la que se aplica el tema: decide la tipografía y nada más. La paleta es
 * compartida (los mockups de fachada V2 usan el mismo lila), pero la fachada jamás
 * recibe Marcellus (decisión F1 — camuflaje).
 */
enum class V2Voice { Facade, Interior }

/** Esquema único claro del V2; sin color dinámico, igual que el tema V1. */
private val V2ColorScheme = lightColorScheme(
    primary = V2Palette.primaryAction,
    onPrimary = V2Palette.onPrimaryAction,
    secondary = V2Palette.accentGroup.stroke,
    onSecondary = V2Palette.cardSurface,
    background = V2Palette.screenBackground,
    onBackground = V2Palette.inkPrimary,
    surface = V2Palette.cardSurface,
    onSurface = V2Palette.inkPrimary,
    surfaceVariant = V2Palette.accentGroup.tint,
    onSurfaceVariant = V2Palette.accentGroup.stroke,
    outline = V2Palette.divider,
    // Errores sin rojo de alarma (decisión F2): el rosa profundo de avisos.
    error = V2Palette.accentNotice.stroke,
    onError = V2Palette.cardSurface
)

private val InteriorTypography = Typography(
    headlineMedium = InteriorTypeVoice.screenTitle,
    titleMedium = InteriorTypeVoice.cardTitle,
    bodyLarge = InteriorTypeVoice.body,
    labelLarge = InteriorTypeVoice.bodyStrong,
    labelMedium = InteriorTypeVoice.label,
    bodySmall = InteriorTypeVoice.caption
)

private val FacadeTypography = Typography(
    headlineMedium = FacadeTypeVoice.screenTitle,
    bodyLarge = FacadeTypeVoice.body,
    labelMedium = FacadeTypeVoice.label
)

/**
 * Tema del sistema visual V2 (Fase 1 de PLAN_UI_V2.md). Convive con [com.auroraclima.app.core.ui.theme.AuroraTheme]
 * (V1) hasta que termine la migración: las pantallas se cambian de tema flujo por
 * flujo (ADR 2026-08-11 en docs/ARQUITECTURA.md).
 */
@Composable
fun AuroraV2Theme(voice: V2Voice, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = V2ColorScheme,
        typography = if (voice == V2Voice.Interior) InteriorTypography else FacadeTypography,
        shapes = V2Shapes,
        content = content
    )
}

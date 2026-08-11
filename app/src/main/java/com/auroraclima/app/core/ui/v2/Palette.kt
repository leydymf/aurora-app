package com.auroraclima.app.core.ui.v2

import androidx.compose.ui.graphics.Color

/**
 * Par de un acento del sistema V2 (anexo v2-29, fuente normativa por decisión D7):
 * [tint] es el tinte claro para el fondo del círculo, chip o insignia; [stroke] es
 * la versión profunda para el trazo del icono o el texto que lo acompaña. Regla
 * madre del anexo: nunca se usa el tinte para texto ni el trazo para fondos grandes.
 */
data class AccentPair(val tint: Color, val stroke: Color)

/**
 * Paleta semántica del sistema visual V2 — única fuente de color de la migración
 * (Fase 1 de PLAN_UI_V2.md). Los tokens se nombran por FUNCIÓN y no por color:
 * la regla 1 del anexo fija un significado por matiz («un color por significado,
 * no por decoración») y ese significado no cambia entre pantallas.
 *
 * Ratios de contraste WCAG AA calculados y documentados en
 * `docs/mockups-v2/README.md` §3.6; los pares texto/fondo cumplen AA (≥ 4.5:1).
 */
object V2Palette {
    /** Fondo de pantalla, lila muy claro. */
    val screenBackground = Color(0xFFF3F1F7)

    /** Tarjetas y superficies elevadas: siempre blancas, esquina [V2Corner.card]. */
    val cardSurface = Color(0xFFFFFFFF)

    /** Tinta principal de texto — 13.8:1 sobre fondo, 15.5:1 sobre tarjeta. */
    val inkPrimary = Color(0xFF1B2530)

    /** Texto secundario — 6.4:1 sobre fondo, 7.2:1 sobre tarjeta. */
    val inkSecondary = Color(0xFF5A5568)

    /**
     * Lavanda: fondo del ÚNICO botón lleno por pantalla (regla 2 del anexo).
     * Si dos acciones compiten, la secundaria va en blanco con borde.
     */
    val primaryAction = Color(0xFFB5AECB)

    /** Texto sobre la lavanda de acción — 7.5:1. */
    val onPrimaryAction = Color(0xFF231F31)

    /** Separadores finos y rieles vacíos. */
    val divider = Color(0xFFF0EEF4)

    /** Lila — reconocer la situación (cuestionario). Trazo 8.9:1 sobre tinte. */
    val accentRecognize = AccentPair(tint = Color(0xFFE3E0EC), stroke = Color(0xFF3A3550))

    /** Violeta — EXCLUSIVO de la nube de regreso al clima. Trazo 8.5:1 sobre tinte. */
    val accentCloud = AccentPair(tint = Color(0xFFE7DFEE), stroke = Color(0xFF4A3358))

    /** Azul — protección. Trazo 7.8:1 sobre tinte. */
    val accentProtection = AccentPair(tint = Color(0xFFD5E3EC), stroke = Color(0xFF274456))

    /** Verde — salud, llamar y «listo/cumplido». Trazo 7.7:1 sobre tinte. */
    val accentCare = AccentPair(tint = Color(0xFFDCE7E1), stroke = Color(0xFF2C4A3E))

    /** Rosa — avisos y cuidado (también errores serenos). Trazo 6.6:1 sobre tinte. */
    val accentNotice = AccentPair(tint = Color(0xFFF0E4E8), stroke = Color(0xFF7A3B4D))

    /** Arena — justicia y tiempo. Trazo 7.5:1 sobre tinte. */
    val accentJustice = AccentPair(tint = Color(0xFFEFE5D9), stroke = Color(0xFF5A4327))

    /**
     * Neutro — «contiene varias cosas»: tarjetas o filas que agrupan elementos de
     * significados distintos (regla 5: el color aparece un nivel más adentro).
     * También es el fallback de todo elemento sin acento asignado (decisión D2).
     * Trazo 7.2:1 sobre tinte.
     */
    val accentGroup = AccentPair(tint = Color(0xFFEDEBF3), stroke = Color(0xFF4E4A5E))
}

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

    /**
     * Píldora que marca la pestaña activa en [V2TabBar]. Comparte valor con el
     * tinte lila pero su significado es propio y fijo: «estás aquí» (regla 1).
     * Siempre acompaña a un icono y una etiqueta — nunca informa sola (regla 3).
     */
    val tabIndicator = Color(0xFFE3E0EC)

    // ——— Escala de severidad del resultado orientativo (RF-29) ———
    // Decisión directora 2026-08-14 (reversión informada de P-41, acta §3.2):
    // el nivel del resultado se acompaña de un color semafórico representativo.
    // Escala CERRADA de cuatro pares, ordenada de menor a mayor severidad, con
    // los tintes en la suavidad del resto de la paleta y el nombre del nivel
    // siempre en palabras encima (regla 3: el color nunca informa solo). Léxico
    // de avisos meteorológicos (verde/ámbar/naranja/rojo), plausible en una app
    // de clima (RNF-05). NO pertenecen a la biblioteca seleccionable del
    // contenido (`ContentVisual`): solo los mapea el resultado por su orden.

    /** Severidad 1 — verde sereno. Trazo 8.6:1 sobre tinte. */
    val accentSeverity1 = AccentPair(tint = Color(0xFFDEEBE2), stroke = Color(0xFF1F4634))

    /** Severidad 2 — ámbar. Trazo 7.4:1 sobre tinte. */
    val accentSeverity2 = AccentPair(tint = Color(0xFFF3E8CD), stroke = Color(0xFF5E440D))

    /** Severidad 3 — naranja. Trazo 7.3:1 sobre tinte. */
    val accentSeverity3 = AccentPair(tint = Color(0xFFF5E1D3), stroke = Color(0xFF77330A))

    /** Severidad 4 — rojo profundo. Trazo 8.5:1 sobre tinte. */
    val accentSeverity4 = AccentPair(tint = Color(0xFFF3DCDC), stroke = Color(0xFF6E1F2A))

    // ——— Fachada de clima (P01–P03, mockups v2-01..v2-03) ———
    // La fachada comparte la paleta lila del V2 pero suma el cielo de su portada.
    // Estos tokens son EXCLUSIVOS de weather/ui: identidad 100 % clima (RNF-05);
    // el espacio interno no los usa. Ratios en docs/mockups-v2/README.md §3.6.

    /** Tope del degradado del cielo de P01 — blanco encima: 9.7:1. */
    val skyTop = Color(0xFF474061)

    /** Base del degradado del cielo de P01 — blanco encima: 5.8:1. */
    val skyBottom = Color(0xFF676086)

    /** Texto e iconos sobre el cielo. */
    val onSky = Color(0xFFFFFFFF)

    /**
     * Extremos frío y cálido de la barra de temperatura del pronóstico (P03).
     * Por debajo de 3:1 sobre la tarjeta: la barra es refuerzo visual y va
     * SIEMPRE flanqueada por las cifras de mínima y máxima (regla 3), el mismo
     * criterio ya aceptado para la barra de progreso lavanda (§3.6).
     */
    val temperatureCold = Color(0xFFA69FB7)
    val temperatureWarm = Color(0xFFC5A36D)
}

package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.theme.PlexSans

/**
 * Colores de la guía de Aurora (v0.3.4), aparte de [V2Palette] a propósito: la guía
 * debe distinguirse de la app que explica (una tarjeta de la guía no se confunde con
 * una de Inicio) y a la vez combinar con ella, por eso es un lavanda más intenso del
 * mismo matiz. Nada de la app usa estos tokens, y la guía no usa los acentos de la app.
 *
 * Los pares de texto cumplen AA sobre [surface] (lo fija `PaletteContrastTest`).
 */
object GuidePalette {
    /** Fondo de las tarjetas de la guía (las pantallas completas usan el del interior). */
    val surface = Color(0xFFECE6F8)

    /** Texto principal: títulos y frases. */
    val ink = Color(0xFF2B2148)

    /** Texto secundario. */
    val inkSoft = Color(0xFF4A4266)

    /** Etiqueta de paso de las tarjetas («Paso N de 8»). */
    val label = Color(0xFF5B4A9E)

    /** Botón de la guía: el único lleno en pantalla mientras está abierta. */
    val action = Color(0xFF6E5DB0)

    /** Texto del botón de la guía. */
    val onAction = Color(0xFFFFFFFF)

    /** Flechas y círculos a mano sobre fondo claro (la guía misma). */
    val strokeOnLight = Color(0xFF6E5DB0)

    /** Flechas y círculos a mano sobre la capa oscura ([V2Palette.spotlightScrim]). */
    val strokeOnScrim = Color(0xFFCBBEF5)
}

/**
 * Letra a mano de la guía (Patrick Hand, licencia OFL; copia de la licencia en
 * `docs/licencias/patrick-hand-OFL.txt`). Solo existe aquí: la usa [GuideTypeVoice] y
 * nada más, así que no puede aparecer en la app ni en la fachada.
 */
private val PatrickHand = FontFamily(Font(R.font.patrick_hand_regular, FontWeight.Normal))

/**
 * La voz de las tarjetas de la guía: la letra a mano en el título y en la etiqueta de
 * paso, y el cuerpo del interior (Plex Sans) en las frases y los botones, para que se
 * lean igual que el resto. Las pantallas completas («Conoce Aurora», «¡Listo!») no la
 * usan: van con [InteriorTypeVoice]. Todo en sp: respeta el tamaño de letra del sistema.
 */
object GuideTypeVoice {
    /** Título de una tarjeta. */
    val cardTitle = TextStyle(
        fontFamily = PatrickHand,
        fontSize = 27.sp,
        lineHeight = 32.sp
    )

    /** Etiqueta de paso («Paso N de 8»). */
    val stepLabel = TextStyle(
        fontFamily = PatrickHand,
        fontSize = 19.sp,
        lineHeight = 24.sp
    )

    /** Frase: el cuerpo del interior. */
    val body = TextStyle(
        fontFamily = PlexSans,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )

    /** Frase secundaria de una tarjeta, más pequeña que [body]. */
    val note = TextStyle(
        fontFamily = PlexSans,
        fontSize = 13.sp,
        lineHeight = 19.sp
    )

    /** Texto de los botones y del enlace «Saltar». */
    val action = TextStyle(
        fontFamily = PlexSans,
        fontWeight = InteriorTypeVoice.emphasisWeight,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )
}

/** Forma de las tarjetas de la guía: derechas, esquinas de 22 dp. */
object GuideCorner {
    val card = RoundedCornerShape(22.dp)
    val button = RoundedCornerShape(16.dp)
}

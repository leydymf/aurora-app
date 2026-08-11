package com.auroraclima.app.core.ui.v2

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.auroraclima.app.core.ui.theme.Marcellus
import com.auroraclima.app.core.ui.theme.PlexSans

/**
 * Las dos voces tipográficas del V2, separadas a propósito para que sea imposible
 * confundirlas (decisión F1 de A2-6 y regla explícita del documento V2):
 *
 * - [InteriorTypeVoice] — SOLO espacio interno: Marcellus en el saludo o título de
 *   pantalla, IBM Plex Sans en todo lo demás (lámina v2-04).
 * - [FacadeTypeVoice] — SOLO fachada de clima: la sans neutra del sistema. No
 *   referencia Marcellus ni Plex; una serif en la fachada sería un distinguidor.
 *
 * Todos los tamaños van en sp: la V2 respeta el tamaño de fuente del sistema (D5).
 * Los estilos no llevan color: el color sale de [V2Palette] en el punto de uso.
 */
object InteriorTypeVoice {
    /** Serif de titulares. Prohibida fuera del saludo/título de pantalla. */
    val titleFamily: FontFamily = Marcellus

    /** Sans de lectura de todo el espacio interno. */
    val bodyFamily: FontFamily = PlexSans

    /** Saludo del Inicio (P09). */
    val greeting = TextStyle(
        fontFamily = Marcellus,
        fontSize = 32.sp,
        lineHeight = 40.sp
    )

    /** Título de pantalla. */
    val screenTitle = TextStyle(
        fontFamily = Marcellus,
        fontSize = 30.sp,
        lineHeight = 38.sp
    )

    /** Título de tarjeta o de sección dentro de una pantalla. */
    val cardTitle = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp
    )

    /** Cuerpo base de lectura (15–16 px según v2-04). */
    val body = TextStyle(
        fontFamily = PlexSans,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )

    /** Cuerpo con énfasis: títulos de fila, texto de botones. */
    val bodyStrong = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )

    /** Etiquetas cortas: chips, estados, «Paso N de M». */
    val label = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    /** Texto de apoyo bajo un título de fila o campo. */
    val caption = TextStyle(
        fontFamily = PlexSans,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )

    /** Número protagonista (líneas de atención, v2-16). */
    val numberLarge = TextStyle(
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    )
}

/** Voz de la fachada: solo la sans del sistema, en los mismos cuerpos base. */
object FacadeTypeVoice {
    /** Única familia permitida en la fachada. */
    val family: FontFamily = FontFamily.Default

    val screenTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )

    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )

    val label = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
}

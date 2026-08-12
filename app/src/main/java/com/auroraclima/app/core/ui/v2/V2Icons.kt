package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R

/**
 * Librería CERRADA de iconografía del espacio interno (anexo v2-29): estos 18
 * iconos de contorno — trazo 1.9, extremos redondeados, pensados para ir a 21
 * sobre círculo de 46 ([AccentBadge]) — son los ÚNICOS permitidos en las
 * pantallas internas. Las pantallas no importan iconos por fuera de este objeto
 * (`VisualSystemGuardTest` veda `androidx.compose.material.icons` en features);
 * la fachada conserva su set meteorológico propio (`ic_weather_*`).
 *
 * Regla 4 del anexo: contorno, nunca rellenos ni ilustrados; sin candados,
 * sirenas, puños ni símbolos policiales — por eso el código se dibuja como
 * llave y no como candado. Los nombres de recurso y de propiedad describen el
 * DIBUJO o una función neutra: quedan legibles en el APK (RNF-05).
 *
 * Cada icono tiene significado FIJO; el acento con el que se pinta también
 * (regla 1). El emparejamiento icono+acento se decide en el punto de uso con
 * los pares de [V2Palette].
 */
object V2Icons {
    /** Persona — tipo «física» de la Guía. */
    @DrawableRes val figure = R.drawable.ic_figure

    /** Dos lóbulos — tipo «psicológica» de la Guía. */
    @DrawableRes val lobes = R.drawable.ic_lobes

    /** Círculo con barra (límite) — tipo «sexual» de la Guía. */
    @DrawableRes val slashCircle = R.drawable.ic_slash_circle

    /** Tarjeta bancaria — tipo «económica» de la Guía. */
    @DrawableRes val card = R.drawable.ic_card

    /** Teléfono móvil — tipo «digital» de la Guía. */
    @DrawableRes val device = R.drawable.ic_device

    /** Cruz suave — salud (sector de rutas y acciones de cuidado). */
    @DrawableRes val health = R.drawable.ic_cross_soft

    /** Escudo — protección (sector de rutas). */
    @DrawableRes val protection = R.drawable.ic_shield

    /** Balanza — justicia (sector de rutas). */
    @DrawableRes val justice = R.drawable.ic_scales

    /** Auricular — llamar (líneas de atención, acceso rápido). */
    @DrawableRes val call = R.drawable.ic_phone_call

    /** Casa — pestaña Inicio del espacio interno (v2-10; fuera de la lista del
     * anexo v2-29, que omitió el icono que su propia barra de pestañas usa). */
    @DrawableRes val home = R.drawable.ic_home_soft

    /** Libro abierto — pestaña y portada de la Guía. */
    @DrawableRes val guide = R.drawable.ic_book_open

    /** Cuaderno — pestaña Mi registro. */
    @DrawableRes val journal = R.drawable.ic_notebook

    /** Nube de contorno — el regreso al clima (RF-16); acento violeta exclusivo. */
    @DrawableRes val cloud = R.drawable.ic_cloud_soft

    /** Calendario — fecha de una entrada. */
    @DrawableRes val date = R.drawable.ic_calendar

    /** Fotografía — adjuntos de una entrada (léxico neutro «media», RF-33). */
    @DrawableRes val media = R.drawable.ic_picture

    /** Visto en círculo — sincronizado / guardado / regla cumplida. */
    @DrawableRes val synced = R.drawable.ic_check_circle

    /** Reloj — en espera / pendiente. */
    @DrawableRes val waiting = R.drawable.ic_clock

    /** Llave — la clave personal (nunca candado, regla 4). */
    @DrawableRes val code = R.drawable.ic_key

    /** Papelera — eliminar. */
    @DrawableRes val delete = R.drawable.ic_bin
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun V2IconsPreview() {
    val rows = listOf(
        listOf(
            V2Icons.figure to V2Palette.accentGroup,
            V2Icons.lobes to V2Palette.accentGroup,
            V2Icons.slashCircle to V2Palette.accentGroup,
            V2Icons.card to V2Palette.accentGroup,
            V2Icons.device to V2Palette.accentGroup,
            V2Icons.health to V2Palette.accentCare
        ),
        listOf(
            V2Icons.protection to V2Palette.accentProtection,
            V2Icons.justice to V2Palette.accentJustice,
            V2Icons.call to V2Palette.accentCare,
            V2Icons.guide to V2Palette.accentGroup,
            V2Icons.journal to V2Palette.accentGroup,
            V2Icons.cloud to V2Palette.accentCloud
        ),
        listOf(
            V2Icons.date to V2Palette.accentJustice,
            V2Icons.media to V2Palette.accentGroup,
            V2Icons.synced to V2Palette.accentCare,
            V2Icons.waiting to V2Palette.accentJustice,
            V2Icons.code to V2Palette.accentRecognize,
            V2Icons.delete to V2Palette.accentNotice
        )
    )
    AuroraV2Theme(V2Voice.Interior) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (iconRes, accent) -> AccentBadge(iconRes = iconRes, accent = accent) }
                }
            }
        }
    }
}

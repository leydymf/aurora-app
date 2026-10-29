package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R

/**
 * Piezas de la pantalla de clima (v2-01) que también pinta la práctica de entrada de la
 * guía: el cielo, la barra en reposo y el bloque de ciudad, temperatura y condición.
 * Viven aquí para que las dos usen exactamente lo mismo sin que un feature importe al
 * otro (§2). Solo forma: el campo de texto lo construye cada pantalla dentro de su
 * `SecureTextEntry`, y ninguna pieza guarda estado.
 */
object FacadeSky {
    /** El cielo degradado de P01. */
    val brush: Brush = Brush.verticalGradient(listOf(V2Palette.skyTop, V2Palette.skyBottom))
}

/**
 * Teclado de la barra de búsqueda: sin autocorrección y con «Buscar». Sin autocorrección
 * porque es plausible en una búsqueda de nombres propios de ciudades, y punto. NO protege
 * de que el teclado aprenda: se midió el EditorInfo con `dumpsys input_method` y esta
 * bandera solo RETIRA `TYPE_TEXT_FLAG_AUTO_CORRECT`. De que no aprenda se encarga
 * `SecureTextEntry`, que envuelve cada barra (RF-14, H-7, P-94).
 */
val facadeSearchKeyboardOptions = KeyboardOptions(
    autoCorrectEnabled = false,
    imeAction = ImeAction.Search
)

/** Colores de la píldora de búsqueda: blanca, sin línea indicadora. */
@Composable
fun facadeSearchFieldColors(): TextFieldColors = TextFieldDefaults.colors(
    focusedContainerColor = V2Palette.cardSurface,
    unfocusedContainerColor = V2Palette.cardSurface,
    focusedTextColor = V2Palette.inkPrimary,
    unfocusedTextColor = V2Palette.inkPrimary,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = V2Palette.inkPrimary
)

/** Texto de ayuda de la barra: «Buscar ciudad…». */
@Composable
fun FacadeSearchPlaceholder() {
    Text(
        text = stringResource(R.string.weather_search_hint),
        style = FacadeTypeVoice.body,
        color = V2Palette.inkSecondary
    )
}

/** Lupa de la barra en reposo. */
@Composable
fun FacadeSearchIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_weather_search),
        contentDescription = null,
        tint = V2Palette.inkSecondary,
        modifier = Modifier.size(20.dp)
    )
}

/** Marcador de ubicación al final de la barra en reposo. */
@Composable
fun FacadePinIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_weather_pin),
        contentDescription = null,
        tint = V2Palette.inkSecondary,
        modifier = Modifier.size(20.dp)
    )
}

/**
 * Bloque protagonista de P01: ciudad, temperatura y condición en blanco sobre el cielo.
 * [details] va pegado debajo (la fachada pone ahí, tras 4 dp, máximas, mínimas y la
 * hora del dato).
 */
@Composable
fun FacadeConditionsSummary(
    city: String,
    temperature: String,
    condition: String,
    modifier: Modifier = Modifier,
    details: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = city, style = FacadeTypeVoice.bodyStrong, color = V2Palette.onSky)
        Text(text = temperature, style = FacadeTypeVoice.displayTemperature, color = V2Palette.onSky)
        Text(text = condition, style = FacadeTypeVoice.body, color = V2Palette.onSky)
        details()
    }
}

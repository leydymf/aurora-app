package com.auroraclima.app.core.ui.v2

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified

/**
 * Una etiqueta corta que no parte palabras (v0.3.3). Con letra grande, «Informarme» se
 * partía en «Infor / ma…» en las tarjetas de Acciones rápidas. Ahora conserva la letra
 * elegida si cada palabra cabe entera en [availableWidthPx] y el texto entra en
 * [maxLines] líneas; si no, la letra se reduce solo lo necesario (dentro de un paso de
 * [SHRINK_STEP]). Las líneas se cortan entre palabras y el texto se ve entero, salvo en
 * anchos extremos fuera de lo probado, donde los puntos suspensivos quedan como último
 * recurso.
 *
 * La reducción es una red, no la disposición: quien la usa decide antes con
 * [fitsWithoutShrinking] cómo acomodarse para que no haga falta (Acciones rápidas se
 * apilan en lugar de reducir la letra, WCAG 1.4.4).
 *
 * Recibe el ancho en píxeles en lugar de medirlo: quien la usa puede estar dentro de
 * una medida intrínseca (la fila de Acciones rápidas iguala alturas así), donde un
 * `BoxWithConstraints` no se admite. Aplica el estilo y [maxLines] juntos, para que no
 * puedan desacoplarse. Vive en `core/ui/v2` aunque hoy la use una sola pantalla: es
 * presentación genérica, junto a las primitivas de `TextFit.kt` que comparte con las
 * etiquetas de pestaña (que conservan su regla de una línea, `TabLabel`).
 */
@Composable
fun WordSafeText(
    text: String,
    style: TextStyle,
    availableWidthPx: Int,
    maxLines: Int,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null
) {
    // El medidor cambia con la densidad, la escala de letra y la dirección.
    val measurer = rememberTextMeasurer()
    val fitted = remember(text, style, availableWidthPx, maxLines, measurer) {
        wordSafeStyle(measurer, text, style, availableWidthPx, maxLines)
    }
    Text(
        text = text,
        style = fitted,
        color = color,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Empieza por la escala que haría caber la palabra más larga ([oneLineScale]) y busca
 * con [firstFittingScale] la primera que de verdad cumple: cada palabra entera, el
 * texto dentro de [maxLines] y cada corte en un espacio.
 */
internal fun wordSafeStyle(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    availableWidthPx: Int,
    maxLines: Int
): TextStyle {
    if (availableWidthPx <= 0 || !style.fontSize.isSpecified) return style
    val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
    val widestWord = words.maxOfOrNull { wordWidth(measurer, it, style) } ?: return style
    val scale = firstFittingScale(oneLineScale(widestWord, availableWidthPx)) { candidate ->
        fitsWithoutBreakingWords(measurer, text, words, style.scaledBy(candidate), availableWidthPx, maxLines)
    }
    return style.scaledBy(scale)
}

/**
 * Si [text] cabe en [availableWidthPx] con [style] tal cual, sin reducir la letra: cada
 * palabra entera, dentro de [maxLines] y con los cortes entre palabras. Sirve para
 * decidir la disposición antes de que [WordSafeText] tenga que reducir.
 */
internal fun fitsWithoutShrinking(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    availableWidthPx: Int,
    maxLines: Int
): Boolean {
    if (availableWidthPx <= 0) return false
    val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
    return fitsWithoutBreakingWords(measurer, text, words, style, availableWidthPx, maxLines)
}

private fun fitsWithoutBreakingWords(
    measurer: TextMeasurer,
    text: String,
    words: List<String>,
    style: TextStyle,
    availableWidthPx: Int,
    maxLines: Int
): Boolean {
    if (words.any { wordWidth(measurer, it, style) > availableWidthPx }) return false
    val layout = measurer.measure(
        text = text,
        style = style,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = availableWidthPx)
    )
    return !layout.hasVisualOverflow && breaksOnlyAtSpaces(text, (0 until layout.lineCount).map(layout::getLineEnd))
}

private fun wordWidth(measurer: TextMeasurer, word: String, style: TextStyle): Int =
    measurer.measure(word, style, maxLines = 1, softWrap = false).size.width

private val WHITESPACE = Regex("\\s+")

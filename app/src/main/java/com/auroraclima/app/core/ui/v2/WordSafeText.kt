package com.auroraclima.app.core.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified

/**
 * Estilo para una etiqueta corta que no parte palabras (v0.3.3). Con letra grande,
 * «Informarme» se partía en «Infor / ma…» en las tarjetas de Acciones rápidas. Ahora
 * la etiqueta conserva la letra elegida si su palabra más larga cabe en
 * [availableWidth] (píxeles) y el texto entero entra en [maxLines] líneas; si no, la
 * letra se reduce solo lo necesario. Las líneas se cortan entre palabras y el texto
 * nunca se corta ni se esconde.
 *
 * Recibe el ancho en lugar de medirlo: quien la usa puede estar dentro de una medida
 * intrínseca (la fila de Acciones rápidas iguala alturas así), donde un
 * `BoxWithConstraints` no se admite. Las etiquetas de pestaña tienen su propia regla,
 * de una línea (`TabLabel`).
 */
@Composable
fun rememberWordSafeStyle(text: String, style: TextStyle, availableWidth: Int, maxLines: Int): TextStyle {
    // El medidor cambia con la densidad, la escala de letra y la dirección.
    val measurer = rememberTextMeasurer()
    return remember(text, style, availableWidth, maxLines, measurer) {
        wordSafeStyle(measurer, text, style, availableWidth, maxLines)
    }
}

/**
 * La primera reducción apunta a que la palabra más larga quepa en una línea
 * ([oneLineScale]), pero el ancho del texto no escala en proporción a la letra
 * (medido: «Llamar» ocupa 209 px a 13 sp y 203 px a 12 sp, no 193). Por eso cada
 * candidato se comprueba midiendo ([fitsWithoutBreakingWords]) y, si no sirve, se
 * reduce en pasos pequeños.
 */
internal fun wordSafeStyle(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    availableWidth: Int,
    maxLines: Int
): TextStyle {
    if (availableWidth <= 0 || !style.fontSize.isSpecified) return style
    val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
    val widestWord = words.maxOfOrNull { wordWidth(measurer, it, style) } ?: return style
    var scale = oneLineScale(widestWord, availableWidth)
    repeat(MAX_STEPS) {
        val candidate = style.scaledBy(scale)
        if (fitsWithoutBreakingWords(measurer, text, words, candidate, availableWidth, maxLines)) return candidate
        scale *= STEP
    }
    return style.scaledBy(scale)
}

/**
 * Cada palabra cabe entera en el ancho, el texto entra en [maxLines] líneas y cada
 * corte de línea cae en un espacio.
 */
private fun fitsWithoutBreakingWords(
    measurer: TextMeasurer,
    text: String,
    words: List<String>,
    style: TextStyle,
    availableWidth: Int,
    maxLines: Int
): Boolean {
    if (words.any { wordWidth(measurer, it, style) > availableWidth }) return false
    val layout = measurer.measure(
        text = text,
        style = style,
        maxLines = maxLines,
        constraints = Constraints(maxWidth = availableWidth)
    )
    if (layout.hasVisualOverflow) return false
    return (0 until layout.lineCount - 1).all { line ->
        val end = layout.getLineEnd(line)
        end in 1..text.length && text[end - 1].isWhitespace()
    }
}

private fun wordWidth(measurer: TextMeasurer, word: String, style: TextStyle): Int =
    measurer.measure(word, style, maxLines = 1, softWrap = false).size.width

private fun TextStyle.scaledBy(scale: Float): TextStyle = when {
    scale >= 1f -> this
    // Un interlineado sin especificar no se puede escalar: queda el de la letra.
    lineHeight.isSpecified -> copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    else -> copy(fontSize = fontSize * scale)
}

private val WHITESPACE = Regex("\\s+")
private const val MAX_STEPS = 12
private const val STEP = 0.92f

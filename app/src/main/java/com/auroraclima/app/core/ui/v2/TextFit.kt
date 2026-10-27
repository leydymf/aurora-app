package com.auroraclima.app.core.ui.v2

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

/*
 * Primitivas puras para ajustar una etiqueta corta a su ancho (v0.3.3), compartidas por
 * las etiquetas de pestaña (`TabLabel`, una línea) y las de Acciones rápidas
 * (`WordSafeText`, sin partir palabras). Puras para probarlas en la JVM.
 */

/**
 * Cuánto reducir una etiqueta de [textWidth] para que quepa en [available] (ambos en
 * píxeles): 1 si ya cabe. Un poco por debajo del cociente exacto, para que el redondeo
 * de la medida no la vuelva a partir. Supone que el ancho escala en proporción a la
 * letra, lo que solo es aproximado (ver [firstFittingScale]).
 */
internal fun oneLineScale(textWidth: Int, available: Int): Float =
    if (textWidth <= available || textWidth <= 0 || available <= 0) 1f else available.toFloat() / textWidth * FIT_MARGIN

/**
 * La primera escala de la serie [start], [start]·[SHRINK_STEP], [start]·[SHRINK_STEP]², …
 * que cumple [fits]. El ancho del texto no escala en proporción a la letra (medido:
 * «Llamar» ocupa 209 px a 13 sp y 203 px a 12 sp, no 193), así que cada candidato se
 * comprueba en vez de suponerlo. Prueba los [MAX_SHRINK_STEPS] − 1 primeros; si ninguno
 * cumple, devuelve el siguiente de la serie sin probarlo, el más pequeño: es el último
 * recurso de anchos extremos.
 */
internal fun firstFittingScale(start: Float, fits: (Float) -> Boolean): Float {
    var scale = start.coerceAtMost(1f)
    repeat(MAX_SHRINK_STEPS - 1) {
        if (fits(scale)) return scale
        scale *= SHRINK_STEP
    }
    return scale
}

/** Cada corte de línea (todas las [lineEnds] salvo la última) cae justo después de un espacio. */
internal fun breaksOnlyAtSpaces(text: String, lineEnds: List<Int>): Boolean =
    lineEnds.dropLast(1).all { end -> end in 1..text.length && text[end - 1].isWhitespace() }

/** La letra (y su interlineado, si lo tiene) por [scale]; igual si [scale] es 1 o más. */
internal fun TextStyle.scaledBy(scale: Float): TextStyle = when {
    scale >= 1f || !fontSize.isSpecified -> this
    // Un interlineado sin especificar no se puede escalar: queda el de la letra.
    lineHeight.isSpecified -> copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    else -> copy(fontSize = fontSize * scale)
}

private const val FIT_MARGIN = 0.97f

/** Paso de reducción: pequeño, para que «solo lo necesario» se cumpla dentro de un 4 %. */
internal const val SHRINK_STEP = 0.96f

/** Tope de candidatos: 0,96²⁴ ≈ 0,38 de la primera estimación. */
internal const val MAX_SHRINK_STEPS = 25

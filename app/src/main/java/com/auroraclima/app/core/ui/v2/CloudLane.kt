package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Respiro entre los botones de una barra y la nube de salida rápida. */
private val CloudLaneGap = 12.dp

/** Lo que la barra deja a su derecha: la nube y un respiro, menos el margen que la pantalla ya pone. */
private fun laneEnd(parentEndPadding: Dp): Dp =
    (CloudReturnClearance + CloudLaneGap - parentEndPadding).coerceAtLeast(0.dp)

/**
 * Una fila con el «carril» de la nube de salida rápida (RF-16) reservado, para una
 * barra inferior fija: la nube flota anclada abajo a la derecha (`SecureNavigation`,
 * sin cambios) y la barra le deja su sitio al final para que los botones queden en la
 * MISMA línea, centrados con ella y sin taparse.
 *
 * La geometría sale de las medidas de la nube ([CloudReturnSize], [CloudReturnMargin]):
 * el círculo mide lo que un botón de la V2 (52 dp) y la barra lo separa del borde lo
 * que la nube, así que ambos centros coinciden. Solo lo usan las pantallas cuya acción
 * va abajo a la altura de la nube (cuestionario, v0.3.2); el resto sigue con su aire
 * final y la nube no se mueve en ninguna. [parentEndPadding] es el margen horizontal
 * que la pantalla ya aplica a su columna.
 */
@Composable
fun CloudLaneBar(
    modifier: Modifier = Modifier,
    parentEndPadding: Dp = 24.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = laneEnd(parentEndPadding), bottom = CloudReturnMargin)
            .heightIn(min = CloudReturnSize),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Dos acciones en el carril: lado a lado cuando caben —cada una con la mitad— y, si
 * el texto es largo, la pantalla es estrecha o la fuente es grande, una sobre otra
 * con la segunda a la altura de la nube; nunca un botón con el texto cortado.
 */
@Composable
fun CloudLanePair(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    parentEndPadding: Dp = 24.dp
) {
    Layout(
        content = {
            first()
            second()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(end = laneEnd(parentEndPadding), bottom = CloudReturnMargin)
    ) { measurables, constraints ->
        val gap = CloudLaneGap.roundToPx()
        val half = (constraints.maxWidth - gap) / 2
        val fits = measurables.all { it.maxIntrinsicWidth(CloudReturnSize.roundToPx()) <= half }
        if (fits) placeSideBySide(measurables, constraints, half, gap) else placeStacked(measurables, constraints)
    }
}

private fun MeasureScope.placeSideBySide(
    measurables: List<Measurable>,
    constraints: Constraints,
    half: Int,
    gap: Int
): MeasureResult {
    val placeables = measurables.map { it.measure(constraints.copy(minWidth = half, maxWidth = half)) }
    val height = placeables.maxOf { it.height }
    return layout(constraints.maxWidth, height) {
        placeables[0].place(0, (height - placeables[0].height) / 2)
        placeables[1].place(half + gap, (height - placeables[1].height) / 2)
    }
}

private fun MeasureScope.placeStacked(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
    val placeables = measurables.map { it.measure(constraints.copy(minWidth = constraints.maxWidth)) }
    val stackGap = STACK_GAP.roundToPx()
    return layout(constraints.maxWidth, placeables.sumOf { it.height } + stackGap) {
        placeables[0].place(0, 0)
        placeables[1].place(0, placeables[0].height + stackGap)
    }
}

private val STACK_GAP = 8.dp

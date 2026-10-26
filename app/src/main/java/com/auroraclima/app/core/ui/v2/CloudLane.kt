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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * «Carril» de la nube de salida rápida (RF-16) en una barra inferior fija: la nube
 * flota anclada abajo a la derecha (`SecureNavigation`, sin cambios) y la barra le
 * deja su sitio al final para que los botones queden en la MISMA línea, centrados
 * con ella y sin taparse.
 *
 * La geometría sale de las medidas de la nube ([CloudReturnSize], [CloudReturnMargin]):
 * el círculo mide lo que un botón de la V2 (52 dp) y la barra lo separa del borde lo
 * que la nube, así que ambos centros coinciden. Solo lo usan las pantallas cuya acción
 * va abajo a la altura de la nube (cuestionario, v0.3.2); el resto de pantallas sigue
 * con su aire final y la nube no se mueve en ninguna.
 */
val CloudLaneGap = 12.dp

/** Espacio que la barra deja a su derecha: lo que ocupa la nube más un respiro, menos el margen que ya pone la pantalla. */
private fun laneEnd(parentEndPadding: Dp): Dp =
    (CloudReturnClearance + CloudLaneGap - parentEndPadding).coerceAtLeast(0.dp)

/**
 * Una fila con el carril de la nube reservado. [parentEndPadding] es el margen
 * horizontal que la pantalla ya aplica a su columna.
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
        val stackGap = STACK_GAP.roundToPx()
        val half = (constraints.maxWidth - gap) / 2
        val fits = measurables.all { it.maxIntrinsicWidth(CloudReturnSize.roundToPx()) <= half }
        if (fits) {
            val placeables = measurables.map { it.measure(constraints.copy(minWidth = half, maxWidth = half)) }
            val height = placeables.maxOf { it.height }
            layout(constraints.maxWidth, height) {
                placeables[0].place(0, (height - placeables[0].height) / 2)
                placeables[1].place(half + gap, (height - placeables[1].height) / 2)
            }
        } else {
            val full = constraints.copy(minWidth = constraints.maxWidth)
            val placeables = measurables.map { it.measure(full) }
            val height = placeables.sumOf { it.height } + stackGap
            layout(constraints.maxWidth, height) {
                placeables[0].place(0, 0)
                placeables[1].place(0, placeables[0].height + stackGap)
            }
        }
    }
}

private val STACK_GAP = 8.dp

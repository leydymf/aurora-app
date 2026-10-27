package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Respiro entre la nube de salida rápida y lo que se le acerca: los botones de una
 * barra y, en Inicio, el final de la zona que se desplaza (`reserveCloudLane`).
 */
val CloudLaneGap = 12.dp

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
 * va abajo a la altura de la nube (cuestionario y bienvenida, v0.3.2); el resto sigue con su aire
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
 * Dos acciones una sobre otra en el carril: la primera arriba y la segunda en la
 * MISMA línea que la nube, a la que no tapa (el carril está reservado a su derecha).
 * Siempre en vertical —así lo pidió la usuaria tras probar la barra lado a lado— y
 * con cualquier tamaño de letra: nunca un botón con el texto cortado. La segunda
 * ocupa al menos el alto de la nube y se centra en él, así que un botón de texto
 * (p. ej. «Ahora no») también queda centrado con ella.
 */
@Composable
fun CloudLaneStack(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    parentEndPadding: Dp = 24.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = laneEnd(parentEndPadding), bottom = CloudReturnMargin),
        verticalArrangement = Arrangement.spacedBy(STACK_GAP)
    ) {
        first()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CloudReturnSize),
            contentAlignment = Alignment.Center
        ) {
            second()
        }
    }
}

private val STACK_GAP = 8.dp

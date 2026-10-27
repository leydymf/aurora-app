package com.auroraclima.app.core.ui.v2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize

/**
 * Dónde está una capa en la ventana (v0.3.3), para dibujar en ella lo que llega en
 * coordenadas de ventana: la nube del grafo seguro o un elemento que el recorrido
 * ilumina. La capa se marca con [trackWindowFrame]; hasta su primera colocación no
 * hay origen y [toLocal] devuelve null.
 */
@Stable
class WindowFrame {
    var origin: Offset? by mutableStateOf(null)
        private set

    var size: IntSize by mutableStateOf(IntSize.Zero)
        private set

    /** [rect], en coordenadas de ventana, en las de esta capa. */
    fun toLocal(rect: Rect): Rect? = origin?.let { rect.translate(-it) }

    internal fun update(origin: Offset, size: IntSize) {
        this.origin = origin
        this.size = size
    }
}

@Composable
fun rememberWindowFrame(): WindowFrame = remember { WindowFrame() }

/** Mantiene [frame] al día con la posición y el tamaño de este elemento en la ventana. */
fun Modifier.trackWindowFrame(frame: WindowFrame): Modifier = onGloballyPositioned {
    frame.update(it.positionInWindow(), it.size)
}

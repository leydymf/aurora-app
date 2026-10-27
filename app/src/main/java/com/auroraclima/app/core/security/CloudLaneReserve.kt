package com.auroraclima.app.core.security

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.auroraclima.app.core.ui.v2.CloudLaneGap

/**
 * Deja libre al pie de este elemento la franja de la nube de la pantalla (v0.3.3): si
 * el elemento baja más allá del borde superior de la nube (menos [CloudLaneGap]), su
 * contenido termina antes. Aplicado por fuera de una zona que se desplaza, ninguna
 * tarjeta ni botón pasa por debajo de la nube en ningún punto del desplazamiento; la
 * franja queda con el fondo del elemento.
 *
 * La medida sale de la nube real ([LocalCloudAnchor]), así que vale con cualquier
 * tamaño de letra y de barra, y la nube no se mueve ni cambia de tamaño (RF-16). Fuera
 * del grafo seguro, o antes de que la nube se coloque, no agrega nada. Es la misma
 * idea que el carril de las barras inferiores (`CloudLane.kt`) para una pantalla que
 * se desplaza entera.
 */
fun Modifier.reserveCloudLane(): Modifier = composed {
    val cloudTop = LocalCloudAnchor.current?.bounds?.top ?: return@composed Modifier
    // Borde inferior del elemento en la ventana: no depende del margen que se le
    // agrega por dentro, así que medirlo y aplicarlo no se retroalimenta.
    var bottom by remember { mutableStateOf<Float?>(null) }
    val lane = with(LocalDensity.current) {
        bottom?.let { (it - (cloudTop - CloudLaneGap.toPx())).coerceAtLeast(0f).toDp() } ?: 0.dp
    }
    Modifier
        .onGloballyPositioned { bottom = it.positionInWindow().y + it.size.height }
        .padding(bottom = lane)
}

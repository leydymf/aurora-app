package com.auroraclima.app.core.security

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import com.auroraclima.app.core.ui.v2.CloudLaneGap
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull

/**
 * Deja libre al pie de este elemento la franja de la nube de la pantalla (v0.3.3): si
 * el elemento baja más allá del borde superior de la nube (menos [CloudLaneGap]), su
 * contenido se mide y se coloca más corto. Aplicado por fuera de una zona que se
 * desplaza, ninguna tarjeta ni botón pasa por debajo de la nube en ningún punto del
 * desplazamiento; la franja queda con el fondo del elemento.
 *
 * La medida sale de la nube real ([LocalCloudAnchor]), así que vale con cualquier
 * tamaño de letra y de barra, y la nube no se mueve ni cambia de tamaño (RF-16). Fuera
 * del grafo seguro, dentro de una ventana propia (`SecureWindow` provee el ancla en
 * null; ahí la franja la da `CloudReturnClearance`) o antes de que la nube se coloque,
 * no agrega nada. Es la idea del carril de las barras inferiores (`CloudLane.kt`) para
 * una pantalla que se desplaza entera.
 *
 * **Solo con alto fijo:** el alto del elemento lo fija su padre (`fillMaxSize`,
 * `weight` o un alto fijo), y así la franja no mueve el borde que se mide. Si el alto
 * no viene fijo (un elemento que envuelve su contenido, o uno dentro de otra zona que
 * se desplaza) se mide con sus restricciones intactas, como si el modificador no
 * estuviera (lo prueba `CloudLaneReserveLayoutTest`).
 *
 * Se calcula al medir, no al componer: la nube y la posición se leen dentro de
 * `layout`, así que si la nube se mueve (con el teclado, por ejemplo) solo se vuelve
 * a medir. La posición se guarda con `rememberSaveable` para que, al volver a una
 * pestaña, la primera medida ya tenga la franja: sin ella el desplazamiento guardado
 * se recortaba a un máximo menor; y recuerda el último borde de la nube para el primer
 * cuadro al volver de otra ruta. `composed` porque el ancla es un CompositionLocal.
 */
internal fun Modifier.reserveCloudLane(): Modifier = composed {
    val anchor = LocalCloudAnchor.current ?: return@composed Modifier
    // Borde superior del elemento en la ventana, de la colocación anterior: con alto
    // fijo no depende de la franja, así que la medida converge en un paso.
    val top = rememberSaveable { mutableStateOf<Float?>(null) }
    // Último borde superior conocido de la nube. Al volver de otra ruta el grafo pinta
    // una nube nueva que aún no se colocó en la primera medida; sin este valor la franja
    // faltaría ese cuadro y el desplazamiento guardado se recortaría.
    val lastCloudTop = rememberSaveable { mutableStateOf<Float?>(null) }
    LaunchedEffect(anchor) {
        snapshotFlow { anchor.bounds?.top }.filterNotNull().collect { lastCloudTop.value = it }
    }
    Modifier
        .onGloballyPositioned { top.value = it.positionInWindow().y }
        .layout { measurable, constraints ->
            val cloudTop = anchor.bounds?.top ?: lastCloudTop.value
            if (!constraints.hasFixedHeight || cloudTop == null) {
                // Sin alto fijo (o sin nube) no toca nada: se mide como si no estuviera.
                val placeable = measurable.measure(constraints)
                return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            val bottom = top.value?.let { it + constraints.maxHeight }
            val lane = cloudLaneHeight(bottom, cloudTop, CloudLaneGap.toPx()).roundToInt()
                .coerceAtMost(constraints.maxHeight)
            val inner = constraints.maxHeight - lane
            val placeable = measurable.measure(constraints.copy(minHeight = inner, maxHeight = inner))
            layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
        }
}

/**
 * Cuánto hay que acortar un elemento que termina en [elementBottom] (en la ventana;
 * null si aún no se midió) para que acabe [gap] por encima de [cloudTop]: cero si ya
 * termina antes. Pura, para probarla en la JVM.
 */
internal fun cloudLaneHeight(elementBottom: Float?, cloudTop: Float, gap: Float): Float =
    if (elementBottom == null) 0f else (elementBottom - (cloudTop - gap)).coerceAtLeast(0f)

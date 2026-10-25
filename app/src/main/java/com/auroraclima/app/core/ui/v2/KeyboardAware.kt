package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Ajuste de una pantalla con campos de texto al teclado (evaluación heurística,
 * v0.3.2). Reemplaza a `systemBarsPadding()` en la raíz de esas pantallas.
 *
 * Por qué hace falta: con edge-to-edge (`enableEdgeToEdge` en MainActivity) la
 * ventana YA NO se achica cuando aparece el teclado; el sistema solo informa su
 * alto como inset y es la app la que debe apartarse. Sin esto la pantalla cree
 * tener el alto completo: el contenido se dibuja bajo el teclado y el
 * desplazamiento no tiene nada que recorrer, así que el botón final queda
 * inalcanzable. `adjustResize` en el manifest es lo que hace llegar ese inset en
 * API 26–29.
 *
 * Hace dos cosas, siempre juntas para que no se ponga una y se olvide la otra:
 *  1. aparta las barras del sistema y el teclado — `imePadding` descuenta lo que
 *     `systemBarsPadding` ya consumió, así que abajo queda el mayor de los dos;
 *  2. un toque en una zona vacía quita el foco, y con él se cierra el teclado.
 *     Solo reacciona a los toques que ningún hijo consumió: botones y campos
 *     reciben el suyo como siempre, y un arrastre sigue desplazando (no cierra el
 *     teclado — decisión de la usuaria).
 *
 * La nube de salida rápida no depende de esto: `SecureNavigation` la ancla con
 * el mismo par de insets por su cuenta (RF-16).
 */
fun Modifier.keyboardAware(): Modifier = this
    .clearFocusOnTap()
    .systemBarsPadding()
    .imePadding()

/**
 * Solo la mitad «tocar fuera quita el foco» de [keyboardAware], para la fachada:
 * allí el inset del teclado va únicamente sobre los resultados, para que la barra
 * de pestañas no salte al enfocar la búsqueda.
 */
fun Modifier.clearFocusOnTap(): Modifier = this then ClearFocusOnTapElement

private data object ClearFocusOnTapElement : ModifierNodeElement<ClearFocusOnTapNode>() {
    override fun create() = ClearFocusOnTapNode()

    override fun update(node: ClearFocusOnTapNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "clearFocusOnTap"
    }
}

/**
 * Nodo y no `pointerInput` + `LocalFocusManager.current`: así el modificador no
 * necesita ser `@Composable` y lee el gestor de foco del sitio donde se aplica.
 */
private class ClearFocusOnTapNode :
    DelegatingNode(),
    CompositionLocalConsumerModifierNode {
    init {
        delegate(
            SuspendingPointerInputModifierNode {
                detectTapGestures(onTap = { currentValueOf(LocalFocusManager).clearFocus() })
            }
        )
    }
}

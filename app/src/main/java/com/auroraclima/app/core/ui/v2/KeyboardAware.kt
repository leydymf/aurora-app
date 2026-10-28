package com.auroraclima.app.core.ui.v2

import android.os.Build
import android.view.ViewGroup
import android.view.Window
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

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
 * Hace tres cosas, siempre juntas para que no se ponga una y se olvide otra:
 *  1. aparta las barras del sistema y el teclado — `imePadding` descuenta lo que
 *     `systemBarsPadding` ya consumió, así que abajo queda el mayor de los dos;
 *  2. con el teclado abierto reserva además la franja de la nube de salida
 *     rápida. `SecureNavigation` la ancla justo sobre el teclado (RF-16) y, sin la
 *     franja, el campo enfocado —que el desplazamiento deja en el borde inferior—
 *     quedaba debajo de la nube, con el ojo de la contraseña tapado (corrida en el
 *     emulador, v0.3.2). Es lo que dibuja el mockup v2-06: la nube en su propia
 *     franja, al lado del botón. Sin teclado no hace falta: cada pantalla ya deja
 *     su aire final para la nube. La franja ([CloudReturnClearance]) supone la nube
 *     en su anclaje base: una ruta con `cloudBottomInset` (hoy solo el panel, que
 *     no tiene campos) la eleva por encima;
 *  3. un toque en una zona vacía quita el foco, y con él se cierra el teclado.
 *     Solo reacciona a los toques que ningún hijo consumió: botones y campos
 *     reciben el suyo como siempre, y un arrastre sigue desplazando (no cierra el
 *     teclado — decisión de la usuaria).
 *
 * `composed` y no un nodo como [clearFocusOnTap]: el inset del teclado solo se lee
 * en composición (`WindowInsets.ime` es un getter `@Composable`).
 */
fun Modifier.keyboardAware(): Modifier = this
    .clearFocusOnTap()
    .systemBarsPadding()
    .imePadding()
    .composed {
        // derivedStateOf: solo recompone al abrirse o cerrarse el teclado, no en cada
        // cuadro de su animación (gama baja, RNF-07).
        val ime = WindowInsets.ime
        val density = LocalDensity.current
        val keyboardOpen by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
        Modifier.padding(bottom = if (keyboardOpen) CloudReturnClearance else 0.dp)
    }

/**
 * Si el teclado está abierto ahora (su alto como inset es mayor que cero), para leerlo
 * desde una corrutina. Es la misma condición que usa [keyboardAware]; la puerta de la
 * fachada la espera antes de entrar (v0.3.4).
 */
@Composable
fun rememberKeyboardOpen(): () -> Boolean {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    return remember(ime, density) { { ime.getBottom(density) > 0 } }
}

/**
 * Solo la parte «tocar fuera quita el foco» de [keyboardAware], para la fachada:
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

/**
 * Que soltar el foco cierre el teclado también en API 26–27 (corrida en el emulador
 * de API 26, v0.3.2). Antes de Android 9, cuando una vista suelta el foco en modo
 * táctil, el sistema se lo devuelve al primer elemento enfocable de la ventana: la
 * vista de Compose lo recupera, Compose se lo da al primer campo y el teclado vuelve
 * a abrirse. Así «tocar fuera», «Listo» y «Buscar» no cerraban nada. Con el
 * contenedor raíz enfocable y por delante de sus hijos, ese foco devuelto se queda
 * en él y el campo lo suelta de verdad. Tocar un campo sigue pidiendo el foco para
 * Compose como siempre. Desde API 28 el sistema ya no reasigna el foco: no se toca.
 *
 * Se aplica a cada ventana con campos: la de MainActivity (y la Activity de las
 * pruebas del teclado, para que midan la misma) y las propias de `SecureDialog` y
 * `SecureSheet`, porque un diálogo es otra ventana y el sistema le devuelve el foco
 * igual (RF-20, RF-46).
 */
fun Window.keepReleasedFocusOutOfFields() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return
    findViewById<ViewGroup>(android.R.id.content)?.apply {
        isFocusableInTouchMode = true
        descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    }
}

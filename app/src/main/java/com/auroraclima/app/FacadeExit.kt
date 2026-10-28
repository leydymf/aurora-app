package com.auroraclima.app

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Salida de la fachada por la puerta de búsqueda (RF-12, v0.3.4): antes de mostrar el
 * espacio privado se quita el foco de la barra, se cierra el teclado y se espera a que
 * termine de cerrarse. Sin esto, el teclado de la barra seguía arriba sobre la marca
 * de entrada y sobre Inicio (hallazgo de la corrida de v0.3.4, también sin la guía).
 *
 * Solo la usan los dos caminos que abren algo (código de acceso y activación). Un
 * código que no coincide no pasa por aquí: el teclado queda como en cualquier
 * búsqueda de ciudad (RF-13). La espera tiene tope ([KEYBOARD_CLOSE_WAIT_MILLIS]): en
 * API 26 la plataforma informa a veces un alto de teclado sin teclado (P-138), y eso no
 * debe retener la entrada.
 *
 * **Después de la espera se vuelve a preguntar [canEnter]** (la app sigue al frente y
 * en la fachada) y, si no, no se navega: el evento se pierde, que es el lado seguro.
 * Si alguien pulsa Inicio o el botón de encendido en esa espera, navegar con la app en
 * segundo plano dejaba el interior abierto para quien volviera desde recientes, sin
 * código (RF-06; hallazgo A-1 de las tres revisoras). Nunca se aplaza la navegación
 * hasta volver al frente. Vive en el paquete raíz porque solo la usa la navegación.
 */
internal suspend fun leaveFacade(
    keyboard: SoftwareKeyboardController?,
    focusManager: FocusManager,
    keyboardOpen: () -> Boolean,
    canEnter: () -> Boolean,
    navigate: () -> Unit
) {
    focusManager.clearFocus(force = true)
    keyboard?.hide()
    withTimeoutOrNull(KEYBOARD_CLOSE_WAIT_MILLIS) { snapshotFlow(keyboardOpen).first { open -> !open } }
    if (canEnter()) navigate()
}

/** Lo más que se espera a que el teclado termine de cerrarse antes de entrar. */
internal const val KEYBOARD_CLOSE_WAIT_MILLIS = 500L

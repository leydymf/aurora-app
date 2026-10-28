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
 * debe retener la entrada. Vive en el paquete raíz, junto a la navegación, porque la
 * fachada no conoce el espacio interno (§2).
 */
internal suspend fun leaveFacade(
    keyboard: SoftwareKeyboardController?,
    focusManager: FocusManager,
    keyboardOpen: () -> Boolean,
    navigate: () -> Unit
) {
    focusManager.clearFocus(force = true)
    keyboard?.hide()
    withTimeoutOrNull(KEYBOARD_CLOSE_WAIT_MILLIS) { snapshotFlow(keyboardOpen).first { open -> !open } }
    navigate()
}

/** Lo más que se espera a que el teclado termine de cerrarse antes de entrar. */
internal const val KEYBOARD_CLOSE_WAIT_MILLIS = 500L

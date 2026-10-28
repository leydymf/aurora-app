package com.auroraclima.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.SoftwareKeyboardController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Salida de la fachada por la puerta (v0.3.4): quitar el foco y cerrar el teclado antes
 * de entrar, esperar a que se cierre con tope, y no entrar si en esa espera la app dejó
 * de estar al frente (hallazgo A-1 de las revisoras: se abría el interior en segundo
 * plano y quedaba abierto desde recientes).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FacadeExitTest {
    private val events = mutableListOf<String>()
    private val keyboardOpen = mutableStateOf(true)
    private var inFront = true

    private val keyboard = object : SoftwareKeyboardController {
        override fun show() = Unit
        override fun hide() {
            events += "cerrar teclado"
        }
    }

    private val focus = object : FocusManager {
        override fun clearFocus(force: Boolean) {
            events += "quitar foco"
        }
        override fun moveFocus(focusDirection: FocusDirection): Boolean = false
    }

    private fun closeKeyboard() {
        keyboardOpen.value = false
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `quita el foco y cierra el teclado antes de entrar y espera a que se cierre`() = runTest {
        launch { leaveFacade(keyboard, focus, { keyboardOpen.value }, { inFront }) { events += "entrar" } }
        runCurrent()
        assertEquals(listOf("quitar foco", "cerrar teclado"), events)

        advanceTimeBy(100)
        closeKeyboard()
        runCurrent()

        assertEquals(listOf("quitar foco", "cerrar teclado", "entrar"), events)
    }

    @Test
    fun `si el teclado no informa su cierre entra al vencer el tope`() = runTest {
        // API 26 informa a veces un alto de teclado sin teclado (P-138).
        launch { leaveFacade(keyboard, focus, { keyboardOpen.value }, { inFront }) { events += "entrar" } }
        advanceTimeBy(KEYBOARD_CLOSE_WAIT_MILLIS - 1)
        runCurrent()
        assertTrue("todavía no", "entrar" !in events)

        advanceTimeBy(2)
        runCurrent()
        assertTrue("entra con el tope", "entrar" in events)
    }

    @Test
    fun `si en la espera la app paso a segundo plano no entra`() = runTest {
        launch { leaveFacade(keyboard, focus, { keyboardOpen.value }, { inFront }) { events += "entrar" } }
        runCurrent()
        // Inicio o el botón de encendido mientras el teclado se cierra.
        inFront = false
        closeKeyboard()
        advanceUntilIdle()

        assertTrue("no debe abrir el interior en segundo plano: $events", "entrar" !in events)
    }

    @Test
    fun `con el teclado ya cerrado entra sin esperar`() = runTest {
        keyboardOpen.value = false
        launch { leaveFacade(keyboard, focus, { keyboardOpen.value }, { inFront }) { events += "entrar" } }
        runCurrent()

        assertTrue("entrar" in events)
        assertEquals(0L, currentTime)
    }
}

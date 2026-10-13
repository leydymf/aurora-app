package com.auroraclima.app.core.security

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comportamiento real del envoltorio de los campos sensibles (H-7, P-94).
 *
 * `SensitiveFieldsGuardTest`, en la JVM, fija la ESTRUCTURA: que los campos pasen
 * por el envoltorio y que el envoltorio instale sus dos protecciones. Lo que no
 * puede hacer es ejecutarlo. Esta prueba sí: compone `SecureTextEntry`, pide el
 * menú de selección desde dentro y mira qué recibe la barra de verdad. Si alguien
 * reescribe el envoltorio de una forma equivalente en el texto pero rota en el
 * comportamiento —o si una subida de Compose cambia la semántica de `showMenu`—,
 * cae aquí.
 *
 * Lo que sigue necesitando una persona: que el menú que DIBUJA el sistema operativo
 * no ofrezca «copiar». Esto comprueba lo que Compose le pasa al menú; que el menú
 * obedezca es cosa de la plataforma, y de eso se encarga la comprobación manual.
 */
@RunWith(AndroidJUnit4::class)
class SecureTextEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Barra de selección que no dibuja nada: solo anota con qué la llamaron. */
    private class RecordingTextToolbar : TextToolbar {
        override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
        var llamada = false
        var copiar: (() -> Unit)? = null
        var pegar: (() -> Unit)? = null
        var cortar: (() -> Unit)? = null
        var seleccionarTodo: (() -> Unit)? = null

        override fun hide() = Unit

        override fun showMenu(
            rect: Rect,
            onCopyRequested: (() -> Unit)?,
            onPasteRequested: (() -> Unit)?,
            onCutRequested: (() -> Unit)?,
            onSelectAllRequested: (() -> Unit)?
        ) {
            llamada = true
            copiar = onCopyRequested
            pegar = onPasteRequested
            cortar = onCutRequested
            seleccionarTodo = onSelectAllRequested
        }
    }

    @Test
    fun dentroDelEnvoltorioElMenuPierdeCopiarYCortarYConservaPegar() {
        val barraReal = pedirMenu(envuelto = true)

        assertTrue("No se llegó a pedir el menú: la prueba no comprobó nada", barraReal.llamada)
        assertNull(
            "El menú de selección volvió a ofrecer «copiar»: el relato puede salir al " +
                "portapapeles (H-7)",
            barraReal.copiar
        )
        assertNull("El menú de selección volvió a ofrecer «cortar» (H-7)", barraReal.cortar)
        assertNotNull(
            "«pegar» debe seguir disponible: la usuaria lo necesita en el relato y en los correos",
            barraReal.pegar
        )
        assertNotNull(
            "«seleccionar todo» debe seguir disponible: sin poder copiar no saca nada de la app",
            barraReal.seleccionarTodo
        )
    }

    @Test
    fun sinElEnvoltorioElMenuSiOfreceCopiar() {
        // Control: demuestra que lo anterior lo consigue el envoltorio y no el
        // montaje de la prueba. Es el estado en que estaba el relato antes de H-7.
        val barraReal = pedirMenu(envuelto = false)

        assertTrue("No se llegó a pedir el menú", barraReal.llamada)
        assertNotNull("Sin envoltorio, «copiar» debe llegar: si no, la prueba es vacua", barraReal.copiar)
        assertNotNull("Sin envoltorio, «cortar» debe llegar", barraReal.cortar)
    }

    /**
     * Compone el contenido —dentro o fuera del envoltorio— y pide desde ahí el menú
     * de selección con las cuatro opciones disponibles, como haría un campo de texto
     * al seleccionar. Devuelve la barra de abajo para ver qué le llegó.
     */
    private fun pedirMenu(envuelto: Boolean): RecordingTextToolbar {
        val barraReal = RecordingTextToolbar()
        composeRule.setContent {
            CompositionLocalProvider(LocalTextToolbar provides barraReal) {
                if (envuelto) {
                    SecureTextEntry { PedirMenu() }
                } else {
                    PedirMenu()
                }
            }
        }
        composeRule.waitForIdle()
        return barraReal
    }
}

@androidx.compose.runtime.Composable
private fun PedirMenu() {
    val barra = LocalTextToolbar.current
    LaunchedEffect(barra) {
        barra.showMenu(
            rect = Rect.Zero,
            onCopyRequested = {},
            onPasteRequested = {},
            onCutRequested = {},
            onSelectAllRequested = {}
        )
    }
}

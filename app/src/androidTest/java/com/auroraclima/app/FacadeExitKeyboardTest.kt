package com.auroraclima.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.core.ui.v2.awaitIme
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import com.auroraclima.app.core.ui.v2.rememberKeyboardOpen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Entrar desde la fachada cierra el teclado antes de mostrar el espacio privado
 * (v0.3.4): con el teclado de la barra abierto, [leaveFacade] quita el foco, lo cierra
 * y solo navega cuando ya no está. Es la misma función que usa la puerta en
 * `AuroraApp.kt` para el código de acceso y para la activación.
 */
@RunWith(AndroidJUnit4::class)
class FacadeExitKeyboardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun elTecladoSeCierraAntesDeEntrar() {
        var text by mutableStateOf("")
        lateinit var scope: CoroutineScope
        lateinit var leave: suspend (onNavigate: (Boolean) -> Unit) -> Unit
        composeRule.setContent {
            scope = rememberCoroutineScope()
            val keyboard = LocalSoftwareKeyboardController.current
            val focusManager = LocalFocusManager.current
            val keyboardOpen = rememberKeyboardOpen()
            leave = { onNavigate ->
                leaveFacade(keyboard, focusManager, keyboardOpen, canEnter = { true }) { onNavigate(keyboardOpen()) }
            }
            Column(modifier = Modifier.fillMaxSize()) {
                TextField(value = text, onValueChange = { text = it }, modifier = Modifier.testTag(FIELD))
            }
        }
        composeRule.onNodeWithTag(FIELD).performClick()
        composeRule.awaitIme(visible = true)

        val openWhenNavigating = mutableListOf<Boolean>()
        composeRule.runOnUiThread { scope.launch { leave { openWhenNavigating += it } } }
        composeRule.waitUntil(WAIT_MILLIS) { openWhenNavigating.isNotEmpty() }

        assertEquals("navega una sola vez", 1, openWhenNavigating.size)
        assertFalse("al navegar el teclado ya está cerrado", openWhenNavigating.single())
        composeRule.awaitIme(visible = false)
    }

    private companion object {
        const val FIELD = "campo"
        const val WAIT_MILLIS = 5_000L
    }
}

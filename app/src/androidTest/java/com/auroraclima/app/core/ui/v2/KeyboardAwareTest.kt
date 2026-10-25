package com.auroraclima.app.core.ui.v2

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Teclado en las pantallas con campos (evaluación heurística, v0.3.2): con el
 * teclado abierto todo sigue alcanzable, tocar fuera lo cierra, «Siguiente» pasa
 * de campo, «Listo» lo cierra y el gesto atrás lo cierra ANTES de navegar.
 *
 * Requiere un teclado en pantalla real: corre en el emulador, no en la JVM.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardAwareTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var submits = 0
    private var finalClicks = 0
    private var backs = 0

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun tocarFueraQuitaElFocoYCierraElTeclado() {
        composeRule.setContent { FormLikeScreen() }
        openKeyboardOn(EMAIL)

        composeRule.onNodeWithTag(EMPTY_AREA).performClick()

        composeRule.awaitIme(visible = false)
        composeRule.onNodeWithTag(EMAIL).assertIsNotFocused()
    }

    @Test
    fun siguientePasaAlCampoSiguienteYListoCierraElTeclado() {
        composeRule.setContent { FormLikeScreen() }
        openKeyboardOn(EMAIL)

        composeRule.onNodeWithTag(EMAIL).performImeAction()
        composeRule.onNodeWithTag(PASSWORD).assertIsFocused()
        // De contraseña a contraseña: el ojo de visibilidad no debe robar el foco.
        composeRule.onNodeWithTag(PASSWORD).performImeAction()
        composeRule.onNodeWithTag(CONFIRM).assertIsFocused()

        composeRule.onNodeWithTag(CONFIRM).performImeAction()
        composeRule.awaitIme(visible = false)
        composeRule.onNodeWithTag(CONFIRM).assertIsNotFocused()
        assertEquals("«Listo» debe enviar el formulario una vez", 1, submits)
    }

    @Test
    fun elBotonFinalSeAlcanzaYQuedaSobreElTeclado() {
        composeRule.setContent { FormLikeScreen() }
        openKeyboardOn(EMAIL)

        composeRule.scrollIntoViewAbove(FINAL_BUTTON) { composeRule.windowHeightPx() - composeRule.imeBottomPx() }
        composeRule.onNodeWithTag(FINAL_BUTTON).performClick()
        assertEquals(1, finalClicks)
    }

    @Test
    fun elPrimerAtrasCierraElTecladoYElSegundoNavega() {
        composeRule.setContent {
            BackHandler { backs++ }
            FormLikeScreen()
        }
        openKeyboardOn(EMAIL)

        pressBack()
        composeRule.awaitIme(visible = false)
        composeRule.waitForIdle()
        assertEquals("El primer atrás solo debe cerrar el teclado", 0, backs)

        pressBack()
        composeRule.waitUntil(IME_TIMEOUT_MS) { backs == 1 }
    }

    private fun openKeyboardOn(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.awaitIme(visible = true)
        composeRule.onNodeWithTag(tag).assertIsFocused()
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }

    /**
     * Réplica de la forma de CreateAccountScreen: raíz con [keyboardAware], todo
     * desplazable, tres campos del kit y el botón al final, alto suficiente para
     * que el teclado lo tape si la pantalla no se ajusta.
     */
    @Composable
    private fun FormLikeScreen() {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        AuroraV2Theme(V2Voice.Interior) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .keyboardAware()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag(EMPTY_AREA)
                )
                V2PlainField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.testTag(EMAIL),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                )
                V2SecretField(
                    value = password,
                    onValueChange = { password = it },
                    isVisible = false,
                    onToggleVisibility = {},
                    isError = false,
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(),
                    toggleDescription = "",
                    modifier = Modifier.testTag(PASSWORD)
                )
                V2SecretField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    isVisible = false,
                    onToggleVisibility = {},
                    isError = false,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submits++ }),
                    toggleDescription = "",
                    modifier = Modifier.testTag(CONFIRM)
                )
                Spacer(modifier = Modifier.height(320.dp))
                V2PrimaryButton(
                    text = "Continuar",
                    onClick = { finalClicks++ },
                    modifier = Modifier.testTag(FINAL_BUTTON)
                )
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    private companion object {
        const val EMPTY_AREA = "empty"
        const val EMAIL = "email"
        const val PASSWORD = "password"
        const val CONFIRM = "confirm"
        const val FINAL_BUTTON = "final"
    }
}

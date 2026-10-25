package com.auroraclima.app.core.security

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2PlainField
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SheetCard
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.awaitIme
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import com.auroraclima.app.core.ui.v2.imeBottomPx
import com.auroraclima.app.core.ui.v2.windowHeightPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Las ventanas propias del espacio interno con un campo dentro (v0.3.2): la hoja de
 * restablecer contraseña (RF-46) y el diálogo con contraseña de eliminar la cuenta
 * (RF-20). En la corrida del emulador el teclado tapaba el campo y el botón de la
 * hoja, y dejaba cortado el último botón del diálogo.
 *
 * Se mide en coordenadas de PANTALLA: el diálogo y la hoja son ventanas propias con
 * su origen, y la Activity (edge-to-edge) empieza en el borde superior de la pantalla.
 */
@RunWith(AndroidJUnit4::class)
class SecureDialogKeyboardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var clicks = 0

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun laHojaQuedaSobreElTecladoConSuCampoYSuBoton() {
        composeRule.setContent {
            AuroraV2Theme(V2Voice.Interior) {
                SecureSheet(onDismissRequest = {}) {
                    V2SheetCard { FieldAndButton(gapDp = 120) }
                }
            }
        }
        assertButtonReachableOverKeyboard()
    }

    @Test
    fun elUltimoBotonDelDialogoSeAlcanzaConElTeclado() {
        composeRule.setContent {
            AuroraV2Theme(V2Voice.Interior) {
                SecureDialog(onDismissRequest = {}) {
                    // Alto suficiente para que, sin desplazamiento, el teclado deje el
                    // botón cortado, como en la confirmación de RF-20.
                    SoftCard { FieldAndButton(gapDp = 420) }
                }
            }
        }
        assertButtonReachableOverKeyboard()
    }

    private fun assertButtonReachableOverKeyboard() {
        composeRule.onNodeWithTag(FIELD).performClick()
        composeRule.awaitIme(visible = true)
        composeRule.waitForIdle()

        val visibleBottom = composeRule.windowHeightPx() - composeRule.imeBottomPx()
        val fieldBottom = screenBottomOf(FIELD)
        assertTrue(
            "El campo termina en $fieldBottom px y el teclado empieza en $visibleBottom px",
            fieldBottom <= visibleBottom
        )

        composeRule.onNodeWithTag(BUTTON).performScrollTo()
        composeRule.waitForIdle()
        val buttonBottom = screenBottomOf(BUTTON)
        assertTrue(
            "El botón termina en $buttonBottom px y el teclado empieza en $visibleBottom px",
            buttonBottom <= visibleBottom
        )
        composeRule.onNodeWithTag(BUTTON).performClick()
        assertEquals(1, clicks)
    }

    private fun screenBottomOf(tag: String): Float {
        val node = composeRule.onNodeWithTag(tag).fetchSemanticsNode()
        return node.positionOnScreen.y + node.size.height
    }

    @Composable
    private fun FieldAndButton(gapDp: Int) {
        var text by remember { mutableStateOf("") }
        Column {
            V2PlainField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.testTag(FIELD),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Spacer(modifier = Modifier.height(gapDp.dp))
            V2PrimaryButton(text = "Enviar", onClick = { clicks++ }, modifier = Modifier.testTag(BUTTON))
        }
    }

    private companion object {
        const val FIELD = "field"
        const val BUTTON = "button"
    }
}

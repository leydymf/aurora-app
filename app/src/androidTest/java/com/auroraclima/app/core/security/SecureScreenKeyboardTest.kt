package com.auroraclima.app.core.security

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.PIXEL_ROUNDING
import com.auroraclima.app.core.ui.v2.V2PlainField
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.awaitIme
import com.auroraclima.app.core.ui.v2.awaitLayout
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import com.auroraclima.app.core.ui.v2.imeBottomPx
import com.auroraclima.app.core.ui.v2.keyboardAware
import com.auroraclima.app.core.ui.v2.windowHeightPx
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La nube de salida rápida (RF-16 criterio 1) sigue visible y pulsable con el
 * teclado abierto, sin tapar el campo enfocado. Antes de v0.3.2 se anclaba solo
 * sobre la barra de navegación y, con edge-to-edge, el teclado la tapaba entera.
 *
 * Se arma el grafo seguro real (`secureGraph`/`screen`) con la sesión de prueba
 * ([TestSession]): lo que se prueba es la posición y el toque, no la limpieza.
 */
@RunWith(AndroidJUnit4::class)
class SecureScreenKeyboardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun laNubeQuedaSobreElTecladoYCierraLaSesion() {
        session.setContent(composeRule) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = GRAPH) {
                secureGraph(route = GRAPH, startDestination = SCREEN) {
                    screen(SCREEN) {
                        var text by remember { mutableStateOf("") }
                        AuroraV2Theme(V2Voice.Interior) {
                            // El campo va abajo, donde lo deja el desplazamiento al
                            // abrirse el teclado: ahí es donde la nube lo tapaba.
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .keyboardAware()
                                    .verticalScroll(rememberScrollState())
                                    .padding(24.dp)
                            ) {
                                Spacer(modifier = Modifier.height(560.dp))
                                V2PlainField(
                                    value = text,
                                    onValueChange = { text = it },
                                    modifier = Modifier.testTag(FIELD),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                                )
                                Spacer(modifier = Modifier.height(200.dp))
                            }
                        }
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.onNodeWithTag(FIELD).performClick()
        composeRule.awaitIme(visible = true)
        composeRule.waitForIdle()

        val cloud = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.access_back_to_weather)
        )

        // La nube queda sobre el teclado y el campo enfocado sobre la nube
        // (keyboardAware le reserva su franja): antes le tapaba el extremo derecho,
        // donde va el ojo. Se espera a que el diseño termine de ajustarse al teclado.
        fun cloudBottom() = cloud.fetchSemanticsNode().boundsInWindow.bottom
        fun cloudTop() = cloud.fetchSemanticsNode().boundsInWindow.top
        fun fieldBottom() = composeRule.onNodeWithTag(FIELD).fetchSemanticsNode().boundsInWindow.bottom
        fun visibleBottom() = composeRule.windowHeightPx() - composeRule.imeBottomPx()
        // La misma tolerancia de redondeo en las dos comparaciones (ImeTestSupport).
        composeRule.awaitLayout(
            message = {
                "La nube va de ${cloudTop()} a ${cloudBottom()} px, el campo termina en ${fieldBottom()} px " +
                    "y el teclado empieza en ${visibleBottom()} px"
            }
        ) {
            cloudBottom() <= visibleBottom() + PIXEL_ROUNDING && fieldBottom() <= cloudTop() + PIXEL_ROUNDING
        }

        cloud.performClick()
        composeRule.waitForIdle()
        assertFalse(
            "Tocar la nube con el teclado abierto debe cerrar la sesión",
            session.sessionManager.isSessionActive.value
        )
    }

    private companion object {
        const val GRAPH = "g"
        const val SCREEN = "s"
        const val FIELD = "field"
    }
}

package com.auroraclima.app.core.security

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.PIXEL_ROUNDING
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.awaitLayout
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import java.time.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La nube y la tarjeta del selector de fecha leen los mismos insets, así que nunca se
 * cruzan (RF-16; v0.3.3).
 *
 * El defecto: la nube lleva `imePadding()` y la tarjeta no. En API 26 la plataforma
 * reporta a veces un inset de teclado de la altura de la barra de navegación aunque
 * no haya teclado; entonces la nube subía 95 px y cubría la mitad de «Aceptar», que
 * está en su misma esquina, y un toque en esa mitad salía al clima. Aparecía en ~3 %
 * de las aperturas, de modo que una sola no lo atrapa: la prueba abre el selector
 * varias veces con la misma ventana.
 *
 * Son [OPENINGS] aperturas por defecto; para una corrida larga:
 * `adb shell am instrument -w -e aperturas 150 -e class <esta clase> <paquete>.test/…`.
 * En API 30 no hay inset espurio y pasa de cualquier modo: es API 26 la que lo pone a
 * prueba. La parte estructural (todas las ventanas llevan `imePadding()`) la fija
 * `SecureWindowInsetsGuardTest`.
 */
@RunWith(AndroidJUnit4::class)
class SecureDayPickerInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private var isOpen by mutableStateOf(false)

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun elSelectorDeFechaNuncaQuedaDebajoDeLaNube() {
        session.setContent(composeRule) {
            AuroraV2Theme(V2Voice.Interior) {
                if (isOpen) {
                    SecureDayPicker(
                        initialDay = LocalDate.of(2026, 10, 25),
                        confirmText = CONFIRM,
                        dismissText = DISMISS,
                        onConfirm = {},
                        onDismiss = {}
                    )
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()

        val cloudLabel = composeRule.activity.getString(R.string.access_back_to_weather)
        fun cloudTop() = composeRule.onNodeWithContentDescription(cloudLabel)
            .fetchSemanticsNode().positionOnScreen.y
        fun confirmBottom() = composeRule.onNodeWithText(CONFIRM).fetchSemanticsNode()
            .let { it.positionOnScreen.y + it.size.height }

        repeat(openings()) { attempt ->
            composeRule.runOnUiThread { isOpen = true }
            composeRule.waitForIdle()
            composeRule.awaitLayout(
                message = {
                    "Apertura ${attempt + 1}: «$CONFIRM» termina en ${confirmBottom()} px y la nube " +
                        "empieza en ${cloudTop()} px"
                }
            ) {
                confirmBottom() <= cloudTop() + PIXEL_ROUNDING
            }
            composeRule.runOnUiThread { isOpen = false }
            composeRule.waitForIdle()
        }
    }

    private fun openings() = InstrumentationRegistry.getArguments().getString(ARGUMENT)
        ?.toIntOrNull()
        ?.coerceAtLeast(1)
        ?: OPENINGS

    private companion object {
        const val ARGUMENT = "aperturas"
        const val OPENINGS = 120
        const val CONFIRM = "Aceptar"
        const val DISMISS = "Cancelar"
    }
}

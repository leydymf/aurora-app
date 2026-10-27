package com.auroraclima.app.access.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tarjeta del cuestionario en Inicio (v0.3.3, evaluación heurística): una persona
 * tocó «Reconocer tu situación» y no pasó nada, porque solo respondía «Empezar»,
 * debajo y fuera de la tarjeta. Ahora toda la tarjeta lo abre, como «Resultados
 * guardados», y «Empezar» va dentro, al pie. Cada toque abre UNA vez.
 */
@RunWith(AndroidJUnit4::class)
class PanelQuizCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val title = context.getString(R.string.access_panel_guide_title)
    private val supporting = context.getString(R.string.access_panel_guide_body)
    private val start = context.getString(R.string.access_home_start)

    private var started = 0

    @Before
    fun setUp() {
        composeRule.setContent {
            AuroraV2Theme(V2Voice.Interior) {
                PanelScreen(
                    onStartGuidance = { started++ },
                    onOpenHistory = {},
                    onOpenSettings = {},
                    onCall = {},
                    onSaveNote = {},
                    onLearn = {}
                )
            }
        }
    }

    @Test
    fun tocarElTituloDeLaTarjetaAbreElCuestionario() {
        composeRule.onNodeWithText(title, useUnmergedTree = true).performClick()

        assertEquals(1, started)
    }

    @Test
    fun tocarElTextoDeApoyoAbreElCuestionario() {
        composeRule.onNodeWithText(supporting, useUnmergedTree = true).performClick()

        assertEquals(1, started)
    }

    @Test
    fun tocarElMargenDeLaTarjetaAbreElCuestionario() {
        // La esquina superior izquierda: margen de la tarjeta, ni texto ni botón.
        card().performTouchInput {
            val inset = 10.dp.toPx()
            click(Offset(inset, inset))
        }

        assertEquals(1, started)
    }

    @Test
    fun empezarVaDentroDeLaTarjetaYAbreUnaSolaVez() {
        val cardBounds = card().fetchSemanticsNode().boundsInRoot
        val button = composeRule.onNodeWithText(start)
        val buttonBounds = button.fetchSemanticsNode().boundsInRoot

        assertTrue(
            "«Empezar» ($buttonBounds) dentro de la tarjeta ($cardBounds)",
            buttonBounds.left >= cardBounds.left && buttonBounds.right <= cardBounds.right &&
                buttonBounds.top >= cardBounds.top && buttonBounds.bottom <= cardBounds.bottom
        )
        button.performClick()
        assertEquals(1, started)
    }

    /** La tarjeta: el nodo pulsable que reúne el título (el botón es un nodo aparte). */
    private fun card() = composeRule.onNode(hasClickAction() and hasText(title))
}

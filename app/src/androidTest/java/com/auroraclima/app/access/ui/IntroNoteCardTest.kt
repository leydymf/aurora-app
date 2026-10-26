package com.auroraclima.app.access.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tarjeta de bienvenida del Inicio para quien saltó el tutorial: texto, dos acciones, y nada si no toca. */
@RunWith(AndroidJUnit4::class)
class IntroNoteCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val noteText = context.getString(R.string.access_intro_note_text)
    private val openLabel = context.getString(R.string.access_intro_note_open)
    private val dismissLabel = context.getString(R.string.access_intro_note_dismiss)

    private var opened = 0
    private var dismissed = 0

    private fun show(showIntroNote: Boolean) {
        composeRule.setContent {
            AuroraV2Theme(V2Voice.Interior) {
                PanelScreen(
                    onStartGuidance = {},
                    onOpenHistory = {},
                    onOpenSettings = {},
                    onCall = {},
                    onSaveNote = {},
                    onLearn = {},
                    showIntroNote = showIntroNote,
                    onOpenIntroNoteTutorial = { opened++ },
                    onDismissIntroNote = { dismissed++ }
                )
            }
        }
    }

    @Test
    fun conElAvisoPendienteMuestraElTextoYLasDosAcciones() {
        show(showIntroNote = true)

        composeRule.onNodeWithText(noteText).assertIsDisplayed()
        composeRule.onNodeWithText(openLabel).assertIsDisplayed()
        composeRule.onNodeWithText(dismissLabel).assertIsDisplayed()
    }

    @Test
    fun verElTutorialYEntendidoEmitenSusEventos() {
        show(showIntroNote = true)

        composeRule.onNodeWithText(openLabel).performClick()
        assertEquals(1, opened)
        composeRule.onNodeWithText(dismissLabel).performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun sinAvisoPendienteElInicioNoMuestraNada() {
        show(showIntroNote = false)

        composeRule.onNodeWithText(noteText).assertDoesNotExist()
    }
}

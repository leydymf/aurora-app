package com.auroraclima.app.records.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Salir del editor con cambios sin guardar (v0.3.2): el gesto atrás y la flecha
 * piden lo mismo, y la pregunta se muestra antes de salir. Sin cambios, el gesto
 * queda libre para el pop normal de la navegación.
 *
 * El estado lo maneja aquí la prueba (la pantalla no tiene estado propio): las
 * reglas de cuándo hay cambios viven en `JournalEditorViewModelTest`.
 */
@RunWith(AndroidJUnit4::class)
class JournalEditorBackTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private var backRequests = 0
    private var leaves = 0

    private val today = LocalDate.of(2026, 10, 25)
    private val unchanged = JournalEditorUiState(
        occurredOn = today,
        today = today,
        isLoadingKinds = false,
        baseline = EditorBaseline(today, kindId = null, detail = "")
    )

    @Test
    fun conCambiosElGestoAtrasYLaFlechaPreguntanAntesDeSalir() {
        setEditor(unchanged.copy(detail = "algo"))

        assertTrue(hasBackCallback())
        pressSystemBack()
        composeRule.onNodeWithText(string(R.string.journal_leave_title)).assertExists()

        composeRule.onNodeWithText(string(R.string.journal_leave_keep)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.common_back)).performClick()
        assertEquals("el gesto y la flecha piden salir del mismo modo", 2, backRequests)

        composeRule.onNodeWithText(string(R.string.journal_leave_confirm)).performClick()
        assertEquals(1, leaves)
    }

    @Test
    fun sinCambiosElGestoAtrasQuedaLibre() {
        setEditor(unchanged)

        assertFalse("sin cambios el atrás es el pop normal", hasBackCallback())
        composeRule.onNodeWithContentDescription(string(R.string.common_back)).performClick()
        assertEquals(1, backRequests)
    }

    /** Réplica mínima del ViewModel: pedir salir con cambios abre la pregunta. */
    private fun setEditor(initial: JournalEditorUiState) {
        session.setContent(composeRule) {
            var state by remember { mutableStateOf(initial) }
            AuroraV2Theme(V2Voice.Interior) {
                JournalEditorScreen(
                    state = state,
                    onBack = {
                        backRequests++
                        if (state.hasUnsavedChanges) state = state.copy(isLeavePromptVisible = true)
                    },
                    onLeaveConfirmed = {
                        leaves++
                        state = state.copy(isLeavePromptVisible = false)
                    },
                    onLeaveDismissed = { state = state.copy(isLeavePromptVisible = false) },
                    onRetryLoad = {},
                    onOpenDatePicker = {},
                    onDismissDatePicker = {},
                    onDaySelected = {},
                    onKindSelected = {},
                    onRetryKinds = {},
                    onDetailChange = {},
                    onPickGallery = {},
                    onPickCamera = {},
                    onPickDocument = {},
                    onRemoveAttachment = {},
                    onSubmit = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun hasBackCallback(): Boolean {
        var enabled = false
        composeRule.runOnUiThread { enabled = composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() }
        return enabled
    }

    private fun pressSystemBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun string(id: Int) = composeRule.activity.getString(id)
}

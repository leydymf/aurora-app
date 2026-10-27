package com.auroraclima.app.records.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Adjunto que no quedó íntegro al guardarse (v0.3.2, defecto del Keystore de
 * Android 8.0): la persona sigue en el editor, con lo escrito, y ve un aviso
 * claro y neutro para intentarlo de nuevo. La app no se cierra.
 *
 * Qué fallo lleva a este aviso lo prueban `JournalEditorViewModelTest`,
 * `LocalJournalAttachmentRepositoryTest` y `AttachmentStoreTest`.
 */
@RunWith(AndroidJUnit4::class)
class JournalEditorMediaErrorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun elAvisoDeAdjuntoNoGuardadoSeVeEnElEditorSinPerderLoEscrito() {
        val state = JournalEditorUiState(
            occurredOn = today,
            today = today,
            isLoadingKinds = false,
            detail = "lo escrito",
            baseline = EditorBaseline(today, kindId = null, detail = ""),
            mediaErrorRes = R.string.journal_media_save_error
        )
        session.setContent(composeRule) {
            AuroraV2Theme(V2Voice.Interior) {
                JournalEditorScreen(
                    state = state,
                    onBack = {},
                    onLeaveConfirmed = {},
                    onLeaveDismissed = {},
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
                    onPickerTipConfirmed = {},
                    onPickerTipDismissed = {},
                    onRemoveAttachment = {},
                    onSubmit = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No se pudo guardar el adjunto. Inténtalo de nuevo.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("lo escrito").assertExists()
        assertFalse("la pantalla no se cierra", composeRule.activity.isFinishing)
        assertEquals(Lifecycle.State.RESUMED, composeRule.activity.lifecycle.currentState)
    }
}

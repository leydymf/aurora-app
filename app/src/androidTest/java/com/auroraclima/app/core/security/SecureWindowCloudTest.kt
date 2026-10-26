package com.auroraclima.app.core.security

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.accounts.ui.DeleteAccountScreen
import com.auroraclima.app.accounts.ui.DeleteAccountStep
import com.auroraclima.app.accounts.ui.DeleteAccountUiState
import com.auroraclima.app.accounts.ui.LoginScreen
import com.auroraclima.app.accounts.ui.LoginUiState
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.PIXEL_ROUNDING
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2SheetCard
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.awaitIme
import com.auroraclima.app.core.ui.v2.awaitLayout
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import com.auroraclima.app.core.ui.v2.imeBottomPx
import com.auroraclima.app.core.ui.v2.windowHeightPx
import com.auroraclima.app.questionnaire.ui.GuidanceScreen
import com.auroraclima.app.questionnaire.ui.GuidanceUiState
import com.auroraclima.app.records.ui.EditorBaseline
import com.auroraclima.app.records.ui.JournalEditorScreen
import com.auroraclima.app.records.ui.JournalEditorUiState
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La salida rápida es inmediata con UN toque en cualquier estado (RF-16), también con
 * una hoja, un diálogo o el selector de fecha abiertos (v0.3.2, P-122). Esas ventanas
 * cubren la de la Activity, así que cada una dibuja su propia nube (`SecureWindow`).
 *
 * Una prueba por tipo de ventana (hoja, diálogo, selector de fecha) y una por cada
 * confirmación concreta: «¿Salir sin guardar?» del editor, «¿Salir ahora?» del
 * cuestionario, las dos de eliminar la cuenta y la hoja de restablecer contraseña. En
 * las dos con campo se prueba además con el teclado abierto. En cada una: la nube se
 * ve entera y por encima del teclado, y un toque cierra la sesión. El selector de
 * fecha se prueba además en una pantalla baja: sus botones no se pierden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class SecureWindowCloudTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    // Por tipo de ventana.

    @Test
    fun conUnaHojaAbiertaUnToqueALaNubeSale() {
        show { SecureSheet(onDismissRequest = {}) { V2SheetCard { Text(SAMPLE) } } }
        assertOneTapOnCloudCloses()
    }

    @Test
    fun conUnDialogoAbiertoUnToqueALaNubeSale() {
        show {
            SecureDialog(onDismissRequest = {}) {
                SoftCard {
                    Column {
                        Text(SAMPLE)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        assertOneTapOnCloudCloses()
    }

    @Test
    fun conElSelectorDeFechaAbiertoUnToqueALaNubeSale() {
        show {
            SecureDayPicker(
                initialDay = LocalDate.of(2026, 10, 25),
                confirmText = CONFIRM,
                dismissText = DISMISS,
                onConfirm = {},
                onDismiss = {}
            )
        }
        // La nube no tapa la tarjeta: va en la franja que la tarjeta le deja abajo. Se
        // espera al diseño final: en API 26 la nube llega a medirse más arriba durante
        // unos cuadros.
        fun cloudTop() = cloudNode().fetchSemanticsNode().positionOnScreen.y
        fun confirmBottom() = composeRule.onNodeWithText(CONFIRM).fetchSemanticsNode()
            .let { it.positionOnScreen.y + it.size.height }
        composeRule.awaitLayout(
            message = { "«$CONFIRM» termina en ${confirmBottom()} px y la nube empieza en ${cloudTop()} px" }
        ) {
            confirmBottom() <= cloudTop() + PIXEL_ROUNDING
        }
        assertOneTapOnCloudCloses()
    }

    /**
     * En una pantalla baja (o en horizontal) el calendario cede su alto y los botones
     * siguen a la vista: antes quedaban fuera de la tarjeta (verificadora, 360×640 dp).
     */
    @Test
    fun enUnaPantallaBajaElSelectorConservaSusBotones() {
        show {
            Box(modifier = Modifier.height(SHORT_SCREEN).testTag(SHORT)) {
                DayPickerCard(confirmText = CONFIRM, dismissText = DISMISS, onConfirm = {}, onDismiss = {}) {
                    DatePicker(state = rememberDatePickerState(), showModeToggle = false)
                }
            }
        }
        val shortBottom = composeRule.onNodeWithTag(SHORT).fetchSemanticsNode().boundsInRoot.bottom
        listOf(CONFIRM, DISMISS).forEach { label ->
            val button = composeRule.onNodeWithText(label)
            button.assertIsDisplayed()
            val bottom = button.fetchSemanticsNode().boundsInRoot.bottom
            assertTrue(
                "«$label» termina en $bottom px, fuera de $shortBottom px",
                bottom <= shortBottom + PIXEL_ROUNDING
            )
        }
    }

    // Por confirmación concreta.

    @Test
    fun conSalirSinGuardarAbiertoUnToqueALaNubeSale() {
        val today = LocalDate.of(2026, 10, 25)
        show {
            JournalEditorScreen(
                state = JournalEditorUiState(
                    occurredOn = today,
                    today = today,
                    isLoadingKinds = false,
                    detail = "algo",
                    baseline = EditorBaseline(today, kindId = null, detail = ""),
                    isLeavePromptVisible = true
                ),
                onBack = {}, onLeaveConfirmed = {}, onLeaveDismissed = {}, onRetryLoad = {},
                onOpenDatePicker = {}, onDismissDatePicker = {}, onDaySelected = {}, onKindSelected = {},
                onRetryKinds = {}, onDetailChange = {}, onPickGallery = {}, onPickCamera = {},
                onPickDocument = {},
                onPickerTipConfirmed = {},
                onPickerTipDismissed = {}, onRemoveAttachment = {}, onSubmit = {}
            )
        }
        composeRule.onNodeWithText(string(R.string.journal_leave_title)).assertExists()
        assertOneTapOnCloudCloses()
    }

    @Test
    fun conSalirAhoraAbiertoUnToqueALaNubeSale() {
        show {
            GuidanceScreen(
                state = GuidanceUiState(isLoading = false, answers = mapOf("q1" to "s1"), isLeavePromptVisible = true),
                onStart = {}, onFinish = {}, onLeaveRequested = {}, onLeaveConfirmed = {}, onLeaveDismissed = {},
                onOptionSelected = {}, onNext = {}, onPrevious = {}, onMunicipalitySelected = {},
                onOpenRoutes = {}, onRetry = {}, onSaveRequested = {}, onSaveConfirmed = {}, onSaveDismissed = {}
            )
        }
        composeRule.onNodeWithText(string(R.string.guidance_leave_title)).assertExists()
        assertOneTapOnCloudCloses()
    }

    @Test
    fun conLaPrimeraConfirmacionDeEliminarLaCuentaUnToqueALaNubeSale() {
        showDeleteAccount(DeleteAccountStep.ConfirmScope)
        assertOneTapOnCloudCloses()
    }

    @Test
    fun conLaContrasenaDeEliminarLaCuentaYElTecladoAbiertoUnToqueALaNubeSale() {
        showDeleteAccount(DeleteAccountStep.ConfirmPassword)
        openKeyboardInWindow()
        assertOneTapOnCloudCloses()
    }

    @Test
    fun conRestablecerLaContrasenaYElTecladoAbiertoUnToqueALaNubeSale() {
        show {
            LoginScreen(
                state = LoginUiState(isCheckingSession = false, isResetSheetVisible = true),
                onBack = null, onEmailChange = {}, onPasswordChange = {}, onTogglePasswordVisibility = {},
                onSubmit = {}, onResetRequested = {}, onResetDismissed = {}, onResetEmailChange = {},
                onResetSubmit = {}
            )
        }
        composeRule.onNodeWithText(string(R.string.account_reset_title)).assertExists()
        openKeyboardInWindow()
        assertOneTapOnCloudCloses()
    }

    private fun showDeleteAccount(step: DeleteAccountStep) = show {
        DeleteAccountScreen(
            state = DeleteAccountUiState(step = step),
            onBack = {},
            onStartConfirmation = {},
            onScopeConfirmed = {},
            onDismissDialogs = {},
            onPasswordChange = {},
            onTogglePasswordVisibility = {},
            onSubmit = {}
        )
    }

    private fun show(content: @Composable () -> Unit) {
        session.setContent(composeRule) { AuroraV2Theme(V2Voice.Interior) { content() } }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    private fun cloudNode() = composeRule.onNodeWithContentDescription(string(R.string.access_back_to_weather))

    /** Abre el teclado en el campo de la ventana propia (no en los de la pantalla de atrás). */
    private fun openKeyboardInWindow() {
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performClick()
        composeRule.awaitIme(visible = true)
        composeRule.waitForIdle()
    }

    /**
     * La nube de la ventana se ve entera, por encima del teclado si está abierto, y un
     * solo toque cierra la sesión: la salida rápida no pasa por cerrar antes la hoja.
     */
    private fun assertOneTapOnCloudCloses() {
        val cloud = cloudNode()
        fun cloudBottom(): Float {
            val node = cloud.fetchSemanticsNode()
            return node.positionOnScreen.y + node.size.height
        }
        fun visibleBottom() = composeRule.windowHeightPx() - composeRule.imeBottomPx()
        composeRule.awaitLayout(
            message = { "La nube termina en ${cloudBottom()} px y lo visible en ${visibleBottom()} px" }
        ) {
            cloudBottom() <= visibleBottom()
        }

        cloud.performClick()
        composeRule.waitForIdle()
        assertFalse(
            "Un toque a la nube con la ventana abierta debe cerrar la sesión",
            session.sessionManager.isSessionActive.value
        )
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private companion object {
        const val SAMPLE = "contenido"
        const val CONFIRM = "Aceptar"
        const val DISMISS = "Cancelar"
        const val SHORT = "short"

        // Bajo a propósito: menos de lo que mide la tarjeta (568 dp), como en horizontal.
        val SHORT_SCREEN = 280.dp
    }
}

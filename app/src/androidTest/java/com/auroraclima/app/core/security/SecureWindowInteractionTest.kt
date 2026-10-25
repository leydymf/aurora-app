package com.auroraclima.app.core.security

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2SheetCard
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Usar una hoja abierta es usar la app (RNF-03, v0.3.2): los toques dentro de una
 * ventana propia no llegan a la de la Activity, así que `SecureWindow` registra la
 * interacción por su cuenta. El plazo de inactividad corre en un reloj virtual.
 *
 * Fija además lo que el toque dentro del contenido no debe hacer: descartar la hoja
 * ni impedir que se desplace.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SecureWindowInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scheduler = TestCoroutineScheduler()

    @get:Rule
    val session = TestSession(StandardTestDispatcher(scheduler))

    private var dismissals = 0

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
        session.setContent(composeRule) {
            AuroraV2Theme(V2Voice.Interior) {
                SecureSheet(onDismissRequest = { dismissals++ }) {
                    V2SheetCard {
                        Text(TOP)
                        // Más alto que la pantalla: la hoja tiene que desplazarse.
                        Spacer(modifier = Modifier.height(1600.dp))
                        Text(BOTTOM)
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    @Test
    fun unToqueDentroDeLaHojaReiniciaLaInactividad() {
        advanceBy(SessionManager.INACTIVITY_TIMEOUT_MS - ONE_MINUTE_MS)
        composeRule.onNodeWithText(TOP).performClick()
        composeRule.waitForIdle()

        advanceBy(2 * ONE_MINUTE_MS)
        assertTrue(
            "Tocar dentro de la hoja es interacción: la sesión no debe cerrarse a los 5 minutos del inicio",
            isActive()
        )
        assertEquals("Tocar el contenido no descarta la hoja", 0, dismissals)

        advanceBy(SessionManager.INACTIVITY_TIMEOUT_MS)
        assertFalse("Sin más toques, la inactividad sigue cerrando la sesión", isActive())
    }

    @Test
    fun laHojaSeDesplazaConElDedo() {
        composeRule.onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
            .performTouchInput { repeat(SWIPES) { swipeUp() } }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(BOTTOM).assertIsDisplayed()
        assertEquals("Desplazar no descarta la hoja", 0, dismissals)
    }

    /** Avanza el reloj virtual en el hilo principal, al que está confinada la sesión. */
    private fun advanceBy(millis: Long) {
        composeRule.runOnUiThread {
            scheduler.advanceTimeBy(millis)
            scheduler.runCurrent()
        }
    }

    private fun isActive(): Boolean = session.sessionManager.isSessionActive.value

    private companion object {
        const val TOP = "arriba"
        const val BOTTOM = "abajo"
        const val ONE_MINUTE_MS = 60_000L
        const val SWIPES = 4
    }
}

package com.auroraclima.app.accounts.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.QuickExitCloud
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La bienvenida (v0.3.2) es la primera pantalla del primer uso: logo, descripción,
 * «Siguiente» fijo en el carril de la nube y la nube de salida rápida (RF-16) a un
 * toque; sin flecha, porque detrás solo está el clima. Detrás del consentimiento
 * está ella: la flecha y el gesto atrás regresan.
 *
 * Las réplicas de la ruta segura son la pantalla más la nube anclada con
 * [QuickExitCloud] y el mismo `NavHost` de pantallas que arma `AuroraApp`.
 */
@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun muestraLogoDescripcionYSegundaLinea() {
        showWelcome()

        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.welcome_what)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.welcome_hint)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.welcome_next)).assertIsDisplayed()
    }

    @Test
    fun noTieneFlechaDeVolver() {
        showWelcome()

        composeRule.onNodeWithContentDescription(string(R.string.common_back)).assertDoesNotExist()
    }

    @Test
    fun siguienteInvocaElCallback() {
        var taps = 0
        showWelcome(onNext = { taps++ })

        composeRule.onNodeWithText(string(R.string.welcome_next)).performClick()

        assertEquals(1, taps)
    }

    @Test
    fun laNubeSacaAlClimaConUnToque() {
        showWelcome()
        assertTrue(session.sessionManager.isSessionActive.value)

        composeRule.onNodeWithContentDescription(string(R.string.access_back_to_weather)).performClick()
        composeRule.waitForIdle()

        assertFalse(session.sessionManager.isSessionActive.value)
    }

    @Test
    fun siguienteYLaNubeQuedanEnUnaMismaLineaSinTaparse() {
        showWelcome()

        val next = rect(string(R.string.welcome_next))
        val cloud = rect(string(R.string.access_back_to_weather), byDescription = true)
        assertTrue("se tapan: $next y $cloud", !next.overlaps(cloud))
        assertEquals(next.center.y, cloud.center.y, LINE_TOLERANCE_PX)
    }

    @Test
    fun conFuenteGrandeEnPantallaPequenaSiguienteSigueVisibleYElContenidoSeDesplaza() {
        showWelcome(size = SMALL, fontScale = 2f)

        // El botón es fijo: se ve sin desplazarse, aunque el contenido no quepa.
        composeRule.onNodeWithText(string(R.string.welcome_next)).assertIsDisplayed()
        // El contenido sí se desplaza hasta la segunda línea.
        composeRule.onNodeWithText(string(R.string.welcome_hint)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.welcome_next)).assertIsDisplayed()
    }

    @Test
    fun siguienteAbreElConsentimientoYLaFlechaVuelveALaBienvenida() {
        showFlow()
        composeRule.onNodeWithText(string(R.string.welcome_what)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.welcome_next)).performClick()
        composeRule.onNodeWithText(string(R.string.account_intro_title)).assertIsDisplayed()
        // La descripción ya no se repite en el consentimiento.
        composeRule.onNodeWithText(string(R.string.welcome_what)).assertDoesNotExist()

        composeRule.onNodeWithContentDescription(string(R.string.common_back)).performClick()
        composeRule.onNodeWithText(string(R.string.welcome_what)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_intro_title)).assertDoesNotExist()
    }

    @Test
    fun elGestoAtrasDelConsentimientoVuelveALaBienvenida() {
        showFlow()
        composeRule.onNodeWithText(string(R.string.welcome_next)).performClick()
        composeRule.onNodeWithText(string(R.string.account_intro_title)).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.welcome_what)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.account_intro_title)).assertDoesNotExist()
    }

    private fun showWelcome(size: DpSize? = null, fontScale: Float = 1f, onNext: () -> Unit = {}) {
        session.setContent(composeRule) {
            AuroraV2Theme(V2Voice.Interior) {
                Scaled(fontScale) {
                    Box(modifier = if (size != null) Modifier.requiredSize(size) else Modifier.fillMaxSize()) {
                        WelcomeScreen(onNext = onNext)
                        QuickExitCloud(modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    /** Las dos pantallas con el cableado de `AuroraApp`: Siguiente navega, la flecha hace pop. */
    private fun showFlow() {
        session.setContent(composeRule) {
            AuroraV2Theme(V2Voice.Interior) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "welcome") {
                    composable("welcome") { WelcomeScreen(onNext = { navController.navigate("intro") }) }
                    composable("intro") {
                        ConsentScreen(
                            state = ConsentUiState(),
                            onAcceptanceChange = {},
                            onAccept = {},
                            onReject = {},
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Scaled(fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
    }

    private fun rect(text: String, byDescription: Boolean = false): Rect {
        val node = if (byDescription) {
            composeRule.onNodeWithContentDescription(text)
        } else {
            composeRule.onNodeWithText(text)
        }
        val semantics = node.fetchSemanticsNode()
        val origin = semantics.positionOnScreen
        return Rect(origin.x, origin.y, origin.x + semantics.size.width, origin.y + semantics.size.height)
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private companion object {
        val SMALL = DpSize(320.dp, 568.dp)
        const val LINE_TOLERANCE_PX = 2f
    }
}

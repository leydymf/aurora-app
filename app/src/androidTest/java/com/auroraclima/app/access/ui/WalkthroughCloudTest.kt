package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.SpotlightTargets
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * La nube sale al clima con UN toque en cada paso y pantalla de la guía (RF-16,
 * v0.3.4). La capa vive en el contenido de la ruta y la nube la dibuja `SecureScreen`
 * después, por encima: si algo de la guía la tapara, el toque simulado —que cae en el
 * centro de la nube— lo recibiría la guía y la sesión seguiría abierta. Una corrida
 * por paso, con letra normal, con letra 2.0 y con letra 2.0 en una pantalla pequeña.
 *
 * De paso fija dos reglas del cuadro: ningún botón de la guía queda debajo de la nube
 * (sería una salida por accidente) y, en la parte 2, la tarjeta no tapa el elemento
 * iluminado.
 */
@RunWith(Parameterized::class)
class WalkthroughCloudTest(
    private val step: WalkthroughStep,
    private val fontScale: Float,
    private val smallScreen: Boolean
) {
    private val case get() = "En $step (letra $fontScale${if (smallScreen) ", pantalla de $SMALL" else ""})"

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun unToqueALaNubeSaleEnEstePaso() {
        val targets = SpotlightTargets()
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = if (smallScreen) Modifier.requiredSize(SMALL) else Modifier.fillMaxSize()) {
                        WalkthroughHarness(state = WalkthroughUiState(step = step), targets = targets)
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
        val context = composeRule.activity
        val title = context.getString(step.titleRes)
        // A la vista de verdad: con alto, dentro de la pantalla.
        composeRule.onNode(hasText(title)).assertIsDisplayed()
        val cloud = composeRule.onNodeWithContentDescription(context.getString(R.string.access_back_to_weather))
        val cloudBounds = cloud.fetchSemanticsNode().boundsInWindow

        val buttons = guideButtons.map(context::getString).flatMap {
            composeRule.onAllNodesWithText(it).fetchSemanticsNodes()
        }
        buttons.forEach { button ->
            assertFalse(
                "$case la nube ($cloudBounds) tapa un botón (${button.boundsInWindow})",
                button.boundsInWindow.overlaps(cloudBounds)
            )
        }
        if (step.part == WalkthroughStep.SPACE_PART) {
            val lit = requireNotNull(targets.boundsOf(step)) { "$case sin elemento iluminado" }
            (composeRule.onAllNodesWithText(title).fetchSemanticsNodes() + buttons).forEach { node ->
                assertFalse(
                    "$case la tarjeta (${node.boundsInWindow}) tapa el elemento iluminado ($lit)",
                    node.boundsInWindow.overlaps(lit)
                )
            }
        }

        cloud.assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertFalse("$case un toque a la nube debe cerrar la sesión", session.sessionManager.isSessionActive.value)
    }

    companion object {
        private val SMALL = DpSize(320.dp, 568.dp)

        private val guideButtons = listOf(
            R.string.access_walk_start,
            R.string.access_walk_not_now,
            R.string.access_walk_next,
            R.string.access_walk_back,
            R.string.access_walk_later,
            R.string.access_walk_skip,
            R.string.access_walk_finish
        )

        // Un `DpSize` en el constructor no sirve: Kotlin le agrega un parámetro
        // sintético por ser clase de valor y JUnit no encuentra el constructor.
        @JvmStatic
        @Parameterized.Parameters(name = "{0}, letra {1}, pantalla pequeña {2}")
        fun cases(): List<Array<Any>> = listOf(1f to false, 2f to false, 2f to true).flatMap { (scale, small) ->
            WalkthroughStep.entries.map { arrayOf(it, scale, small) }
        }
    }
}

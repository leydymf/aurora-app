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
 * La nube sale al clima con UN toque en cada paso del recorrido (RF-16, v0.3.3). El
 * velo vive en el contenido de la ruta y la nube la dibuja `SecureScreen` después,
 * por encima: si el velo la tapara, el toque simulado —que cae en el centro de la
 * nube— lo recibiría el velo y la sesión seguiría abierta. Una corrida por paso,
 * incluidos los intermedios, con letra normal y con letra 2.0 (botones apilados y
 * globo pegado al borde, donde más se acerca a la nube), y con letra 2.0 en una
 * pantalla pequeña, donde Acciones rápidas va apilada y su paso es el más alto.
 */
@RunWith(Parameterized::class)
class HomeTourCloudTest(
    private val step: HomeTourStep,
    private val fontScale: Float,
    private val smallScreen: Boolean
) {
    private val screen get() = if (smallScreen) ", pantalla de $SMALL" else ""
    private val case get() = "En el paso ${step.number} (letra $fontScale$screen)"

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
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = if (smallScreen) Modifier.requiredSize(SMALL) else Modifier.fillMaxSize()) {
                        HomeTourHarness(step = step, onNext = {}, onSkip = {}, targets = SpotlightTargets())
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
        // El recorrido está a la vista en este paso.
        val (title, body, counter) = tourTexts(composeRule.activity, step)
        composeRule.onNode(hasText(title) and hasText(body) and hasText(counter)).assertIsDisplayed()
        val cloud = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.access_back_to_weather)
        )

        // Ningún botón del globo queda debajo de la nube: un toque ahí sería una
        // salida por accidente (visto en la revisión visual de v0.3.3).
        val cloudBounds = cloud.fetchSemanticsNode().boundsInWindow
        val buttons = listOf(R.string.access_tour_next, R.string.access_tour_skip, R.string.access_tour_done)
            .map { composeRule.activity.getString(it) }
            .flatMap { composeRule.onAllNodesWithText(it).fetchSemanticsNodes() }
        buttons.forEach { button ->
            assertFalse(
                "$case la nube ($cloudBounds) tapa un botón " +
                    "(${button.boundsInWindow})",
                button.boundsInWindow.overlaps(cloudBounds)
            )
        }

        cloud.assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertFalse(
            "$case un toque a la nube debe cerrar la sesión",
            session.sessionManager.isSessionActive.value
        )
    }

    companion object {
        private val SMALL = DpSize(320.dp, 568.dp)

        // Un `DpSize` en el constructor no sirve: Kotlin le agrega un parámetro
        // sintético por ser clase de valor y JUnit no encuentra el constructor.
        @JvmStatic
        @Parameterized.Parameters(name = "paso {0}, letra {1}, pantalla pequeña {2}")
        fun cases(): List<Array<Any>> = listOf(1f to false, 2f to false, 2f to true).flatMap { (scale, small) ->
            HomeTourStep.entries.map { arrayOf(it, scale, small) }
        }
    }
}

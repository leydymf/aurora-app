package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import com.auroraclima.app.R
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.SpotlightTargets
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * En Inicio la nube no queda encima de ningún botón ni tarjeta (v0.3.3), con letra
 * normal y con letra grande, en reposo y en cualquier punto del desplazamiento. La
 * nube no se mueve ni cambia de tamaño: es el contenido el que le deja su franja.
 *
 * Se arma el Inicio real con su barra de pestañas dentro del grafo seguro, con el
 * inset del panel, así que la nube queda donde queda en la app. Se comprueba lo que
 * se ve (límites recortados por la vista): la zona que se desplaza termina antes de la
 * nube, y ningún elemento tocable —tarjetas, botones, engranaje, pestañas— se cruza
 * con ella, desplazando hasta el final.
 */
@RunWith(Parameterized::class)
class PanelCloudClearanceTest(private val fontScale: Float) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun laNubeNoTapaNingunBotonNiTarjetaDeInicio() {
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                HomeTourHarness(step = null, onNext = {}, onSkip = {}, targets = SpotlightTargets())
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
        val cloud = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.access_back_to_weather)
        ).fetchSemanticsNode().boundsInWindow

        var position = "en reposo"
        repeat(MAX_SWIPES) {
            assertClearOf(cloud, position)
            val scroller = composeRule.onNode(hasScrollAction())
            val range = scroller.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                ?: return
            if (range.value() >= range.maxValue()) return
            scroller.performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            position = "tras desplazar ${it + 1} vez/veces"
        }
    }

    /** Lo visible que se desplaza termina antes de la nube y nada tocable se cruza con ella. */
    private fun assertClearOf(cloud: Rect, position: String) {
        val scrollArea = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInWindow
        assertTrue(
            "Letra $fontScale, $position: la zona desplazable termina en ${scrollArea.bottom} y la nube " +
                "empieza en ${cloud.top}",
            scrollArea.bottom <= cloud.top
        )
        val touchable = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.boundsInWindow != cloud }
        assertTrue("Hay elementos tocables que revisar", touchable.isNotEmpty())
        touchable.forEach { node ->
            assertTrue(
                "Letra $fontScale, $position: la nube ($cloud) se cruza con ${node.config} (${node.boundsInWindow})",
                !node.boundsInWindow.overlaps(cloud)
            )
        }
    }

    companion object {
        private const val MAX_SWIPES = 12

        @JvmStatic
        @Parameterized.Parameters(name = "letra {0}")
        fun scales(): List<Float> = listOf(1f, 2f)
    }
}

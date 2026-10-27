package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.CloudReturnSize
import com.auroraclima.app.core.ui.v2.SpotlightTargets
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * En Inicio la nube no queda encima de ningún botón ni tarjeta (v0.3.3), con letra
 * normal y con letra grande, también en una pantalla pequeña, en reposo y en cada
 * punto del desplazamiento hasta el final. La nube no se mueve ni cambia de tamaño:
 * es el contenido el que le deja su franja.
 *
 * Se arma el Inicio real con su barra de pestañas dentro del grafo seguro, con el
 * inset del panel, así que la nube queda donde queda en la app. Se comprueba lo que
 * se ve (límites recortados por la vista, que son también los que reciben toques): la
 * zona que se desplaza termina antes de la nube, y ningún elemento tocable —tarjetas,
 * botones, engranaje, pestañas— se cruza con ella. Para que la prueba no pase vacía,
 * exige ver el engranaje y las tres pestañas en reposo, y la tarjeta «Informarme»
 * (la que la nube tapaba) al final.
 */
@RunWith(Parameterized::class)
class PanelCloudClearanceTest(private val fontScale: Float, private val smallScreen: Boolean) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val context get() = composeRule.activity
    private val case get() = "letra $fontScale" + if (smallScreen) ", pantalla de $SMALL" else ""

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun laNubeNoTapaNingunBotonNiTarjetaDeInicio() {
        show()
        val cloud = composeRule.onNodeWithContentDescription(context.getString(R.string.access_back_to_weather))
            .fetchSemanticsNode()
        // La nube conserva su tamaño: la franja la deja el contenido, no ella.
        assertEquals(with(composeRule.density) { CloudReturnSize.toPx() }, cloud.boundsInWindow.width, 1f)

        val atRest = assertClearOf(cloud, "en reposo")
        assertVisible(atRest, "el engranaje", hasContentDescription(context.getString(R.string.access_panel_settings)))
        listOf(R.string.access_tab_home, R.string.access_tab_guide, R.string.access_tab_journal).forEach {
            assertVisible(atRest, "la pestaña ${context.getString(it)}", hasText(context.getString(it)))
        }

        var swipes = 0
        while (!atEnd()) {
            if (swipes == MAX_SWIPES) fail("$case: no se llegó al final de Inicio en $MAX_SWIPES deslizamientos")
            composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            swipes++
            assertClearOf(cloud, "tras $swipes deslizamiento(s)")
        }
        val atTheEnd = assertClearOf(cloud, "al final")
        assertVisible(atTheEnd, "la tarjeta «Informarme»", hasText(context.getString(R.string.access_home_learn)))
        // Al final la tarjeta que la nube tapaba se ve entera, no solo un borde.
        val learn = atTheEnd.first { hasText(context.getString(R.string.access_home_learn)).matches(it) }
        assertEquals(
            "$case: «Informarme» entera al final",
            learn.size.height.toFloat(),
            learn.boundsInWindow.height,
            1f
        )
    }

    private fun show() {
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = if (smallScreen) Modifier.requiredSize(SMALL) else Modifier.fillMaxSize()) {
                        HomeTourHarness(step = null, onNext = {}, onSkip = {}, targets = SpotlightTargets())
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    private fun atEnd(): Boolean {
        val range = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?: throw AssertionError("$case: Inicio sin eje de desplazamiento vertical")
        return range.value() >= range.maxValue()
    }

    /**
     * Lo visible que se desplaza termina antes de la nube y nada tocable se cruza con
     * ella. La nube se excluye por identidad: es la referencia, no un sujeto.
     */
    private fun assertClearOf(cloud: SemanticsNode, position: String): List<SemanticsNode> {
        val cloudBounds = cloud.boundsInWindow
        val scrollArea = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInWindow
        assertTrue(
            "$case, $position: la zona desplazable termina en ${scrollArea.bottom} y la nube empieza en " +
                "${cloudBounds.top}",
            scrollArea.bottom <= cloudBounds.top
        )
        val touchable = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.id != cloud.id }
        touchable.forEach { node ->
            assertTrue(
                "$case, $position: la nube ($cloudBounds) se cruza con ${node.config} (${node.boundsInWindow})",
                !node.boundsInWindow.overlaps(cloudBounds)
            )
        }
        return touchable
    }

    /** [what] está entre los tocables revisados y se ve de verdad (alto visible mayor que cero). */
    private fun assertVisible(touchable: List<SemanticsNode>, what: String, matcher: SemanticsMatcher) {
        val node = touchable.firstOrNull { matcher.matches(it) }
        assertTrue("$case: no se revisó $what", node != null && node.boundsInWindow.visible())
    }

    private fun Rect.visible(): Boolean = height > 0f && width > 0f

    companion object {
        private const val MAX_SWIPES = 12
        private val SMALL = DpSize(320.dp, 568.dp)

        @JvmStatic
        // Un `DpSize` en el constructor no sirve: Kotlin le agrega un parámetro
        // sintético por ser clase de valor y JUnit no encuentra el constructor.
        @Parameterized.Parameters(name = "letra {0}, pantalla pequeña {1}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(1f, false),
            arrayOf(2f, false),
            arrayOf(2f, true)
        )
    }
}

package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.auroraclima.app.InnerTabBar
import com.auroraclima.app.R
import com.auroraclima.app.content.ui.GuideHomeScreen
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.security.secureGraph
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.CloudReturnSize
import com.auroraclima.app.core.ui.v2.V2TabBarHeight
import com.auroraclima.app.core.ui.v2.V2Voice
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
 * En Inicio y en la portada de la Guía la nube no queda encima de ningún botón ni
 * tarjeta (v0.3.3), con letra normal y con letra grande, también en una pantalla
 * pequeña, en reposo y en cada punto del desplazamiento hasta el final. La nube no se
 * mueve ni cambia de tamaño: es el contenido el que le deja su franja.
 *
 * Se arma la pestaña real con la barra de pestañas de la app dentro del grafo seguro,
 * con el inset del panel, así que la nube queda donde queda en la app. Se comprueba lo que
 * se ve (límites recortados por la vista, que son también los que reciben toques): la
 * zona que se desplaza termina antes de la nube, y ningún elemento tocable —tarjetas,
 * botones, engranaje, pestañas— se cruza con ella. Para que la prueba no pase vacía,
 * exige ver las tres pestañas (y en Inicio el engranaje) en reposo, y al final la
 * última tarjeta entera: «Informarme» en Inicio (la que la nube tapaba) y «Líneas de
 * atención» en la Guía.
 */
@RunWith(Parameterized::class)
class PanelCloudClearanceTest(private val tab: Int, private val fontScale: Float, private val smallScreen: Boolean) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val context get() = composeRule.activity
    private val case get() = (if (tab == GUIDE_TAB) "Guía" else "Inicio") + ", letra $fontScale" +
        if (smallScreen) ", pantalla de $SMALL" else ""

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun laNubeNoTapaNingunBotonNiTarjetaDeLaPestana() {
        show()
        val cloud = composeRule.onNodeWithContentDescription(context.getString(R.string.access_back_to_weather))
            .fetchSemanticsNode()
        // La nube conserva su tamaño: la franja la deja el contenido, no ella.
        assertEquals(with(composeRule.density) { CloudReturnSize.toPx() }, cloud.boundsInWindow.width, 1f)

        val atRest = assertClearOf(cloud, "en reposo")
        if (tab == HOME_TAB) {
            assertVisible(
                atRest,
                "el engranaje",
                hasContentDescription(context.getString(R.string.access_panel_settings))
            )
        }
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
        val last = context.getString(
            if (tab == GUIDE_TAB) R.string.content_topics_lines_button else R.string.access_home_learn
        )
        assertVisible(atTheEnd, "la tarjeta «$last»", hasText(last))
        // Al final la última tarjeta se ve entera, no solo un borde.
        val lastCard = atTheEnd.first { hasText(last).matches(it) }
        assertEquals(
            "$case: «$last» entera al final",
            lastCard.size.height.toFloat(),
            lastCard.boundsInWindow.height,
            1f
        )
    }

    private fun show() {
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = if (smallScreen) Modifier.requiredSize(SMALL) else Modifier.fillMaxSize()) {
                        TabInSecureGraph()
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    /** La pestaña con la barra de la app, en una ruta del grafo seguro con el inset del panel. */
    @Composable
    private fun TabInSecureGraph() {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = GRAPH) {
            secureGraph(route = GRAPH, startDestination = SCREEN) {
                screen(SCREEN, cloudBottomInset = V2TabBarHeight) {
                    AuroraV2Theme(V2Voice.Interior) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (tab == GUIDE_TAB) {
                                    GuideHomeScreen(onOpenTopics = {}, onOpenRoutes = {}, onOpenLines = {})
                                } else {
                                    PanelScreen({}, {}, {}, {}, {}, {})
                                }
                            }
                            InnerTabBar(selectedTab = tab, onSelect = {})
                        }
                    }
                }
            }
        }
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
        private const val HOME_TAB = 0
        private const val GUIDE_TAB = 1
        private const val GRAPH = "g"
        private const val SCREEN = "s"
        private val SMALL = DpSize(320.dp, 568.dp)

        @JvmStatic
        // Un `DpSize` en el constructor no sirve: Kotlin le agrega un parámetro
        // sintético por ser clase de valor y JUnit no encuentra el constructor.
        @Parameterized.Parameters(name = "pestaña {0}, letra {1}, pantalla pequeña {2}")
        fun cases(): List<Array<Any>> = listOf(HOME_TAB, GUIDE_TAB).flatMap { tab ->
            listOf(
                arrayOf<Any>(tab, 1f, false),
                arrayOf<Any>(tab, 2f, false),
                arrayOf<Any>(tab, 2f, true)
            )
        }
    }
}

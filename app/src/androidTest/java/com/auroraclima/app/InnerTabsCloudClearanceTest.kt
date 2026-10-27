package com.auroraclima.app

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.auroraclima.app.access.ui.PanelScreen
import com.auroraclima.app.content.ui.GuideHomeScreen
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.CloudReturnSize
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import kotlin.math.abs
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
 * Vive en el paquete raíz porque prueba la composición de pestañas, que es de él
 * (`InnerTabsLayout`): las pantallas de dos features en la maquetación real, dentro
 * del grafo seguro con el inset del panel (`InPanelRoute`).
 *
 * Se comprueba lo que se ve (límites recortados por la vista, que son también los que
 * reciben toques): la zona que se desplaza termina antes de la nube, ningún elemento
 * tocable —tarjetas, botones, engranaje, pestañas— se cruza con ella y la nube sigue
 * en el mismo lugar y con el mismo tamaño. Para que la prueba no pase vacía, exige ver
 * las tres pestañas (y en Inicio el engranaje) en reposo, y la última tarjeta entera y
 * libre de la nube con ella a la vista.
 */
@RunWith(Parameterized::class)
class InnerTabsCloudClearanceTest(
    private val tab: TabCase,
    private val fontScale: Float,
    private val smallScreen: Boolean
) {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val context get() = composeRule.activity
    private val case get() = "$tab, letra $fontScale" + if (smallScreen) ", pantalla de $SMALL" else ""

    /** Dónde estaba la nube en reposo: no debe moverse al desplazar. */
    private lateinit var cloudAtRest: Rect

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun laNubeNoTapaNingunBotonNiTarjetaDeLaPestana() {
        show()
        val cloud = composeRule.onNodeWithContentDescription(context.getString(R.string.access_back_to_weather))
            .fetchSemanticsNode()
        cloudAtRest = cloud.boundsInWindow
        // La nube conserva su tamaño: la franja la deja el contenido, no ella.
        assertEquals(with(composeRule.density) { CloudReturnSize.toPx() }, cloudAtRest.width, 1f)

        val atRest = assertClearOf(cloud, "en reposo")
        if (tab.hasSettings) {
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
            if (swipes == MAX_SWIPES) fail("$case: no se llegó al final en $MAX_SWIPES deslizamientos")
            composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            swipes++
            assertClearOf(cloud, "tras $swipes deslizamiento(s)")
        }
        assertClearOf(cloud, "al final")
        assertLastCardWhole(cloud)
    }

    /**
     * La última tarjeta, traída a la vista: con letra grande en una pantalla pequeña no
     * siempre cabe entera junto con lo que la sigue (en la Guía, la nota sin conexión),
     * así que se exige entera y libre de la nube con ella a la vista, no al final.
     */
    private fun assertLastCardWhole(cloud: SemanticsNode) {
        val last = context.getString(tab.lastCard)
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(last))
        composeRule.waitForIdle()
        val withLast = assertClearOf(cloud, "con «$last» a la vista")
        assertVisible(withLast, "la tarjeta «$last»", hasText(last))
        val lastCard = withLast.first { hasText(last).matches(it) }
        assertEquals("$case: «$last» entera", lastCard.size.height.toFloat(), lastCard.boundsInWindow.height, 1f)
    }

    private fun show() {
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = if (smallScreen) Modifier.requiredSize(SMALL) else Modifier.fillMaxSize()) {
                        InPanelRoute {
                            InnerTabsLayout(selectedTab = tab.index, onSelectTab = {}) { TabPage() }
                        }
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    @Composable
    private fun TabPage() {
        when (tab) {
            TabCase.INICIO -> PanelScreen({}, {}, {}, {}, {}, {})
            TabCase.GUIA -> GuideHomeScreen(onOpenTopics = {}, onOpenRoutes = {}, onOpenLines = {})
        }
    }

    private fun atEnd(): Boolean {
        val range = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?: throw AssertionError("$case: sin eje de desplazamiento vertical")
        return range.value() >= range.maxValue()
    }

    /**
     * La nube no se movió, lo visible que se desplaza termina antes de ella y nada
     * tocable se cruza con ella. La nube se excluye por identidad: es la referencia,
     * no un sujeto.
     */
    private fun assertClearOf(cloud: SemanticsNode, position: String): List<SemanticsNode> {
        val cloudBounds = composeRule.onNodeWithContentDescription(context.getString(R.string.access_back_to_weather))
            .fetchSemanticsNode().boundsInWindow
        assertTrue(
            "$case, $position: la nube se movió de $cloudAtRest a $cloudBounds",
            abs(cloudBounds.left - cloudAtRest.left) <= 1f && abs(cloudBounds.top - cloudAtRest.top) <= 1f
        )
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

    /** Las dos pestañas que se desplazan, con su última tarjeta y si tienen engranaje. */
    enum class TabCase(val index: Int, @StringRes val lastCard: Int, val hasSettings: Boolean) {
        INICIO(HOME_TAB, R.string.access_home_learn, hasSettings = true),
        GUIA(GUIDE_TAB, R.string.content_topics_lines_button, hasSettings = false)
    }

    companion object {
        private const val MAX_SWIPES = 12
        private val SMALL = DpSize(320.dp, 568.dp)

        // Un `DpSize` en el constructor no sirve: Kotlin le agrega un parámetro
        // sintético por ser clase de valor y JUnit no encuentra el constructor.
        @JvmStatic
        @Parameterized.Parameters(name = "{0}, letra {1}, pantalla pequeña {2}")
        fun cases(): List<Array<Any>> = TabCase.entries.flatMap { tab ->
            listOf(
                arrayOf<Any>(tab, 1f, false),
                arrayOf<Any>(tab, 2f, false),
                arrayOf<Any>(tab, 2f, true)
            )
        }
    }
}

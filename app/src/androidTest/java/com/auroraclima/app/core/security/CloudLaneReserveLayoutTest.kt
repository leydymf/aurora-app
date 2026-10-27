package com.auroraclima.app.core.security

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.CloudLaneGap
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La franja de la nube (`reserveCloudLane`, v0.3.3) hace lo que promete su KDoc, en el
 * grafo seguro real: con alto fijo, lo de adentro termina antes de la nube; sin alto
 * fijo no toca nada (un elemento que envuelve su contenido conserva su alto, y dentro
 * de otra zona que se desplaza no falla); y al volver de otra ruta el desplazamiento
 * guardado al final sigue al final, aunque la nube nueva aún no se haya colocado.
 */
@RunWith(AndroidJUnit4::class)
class CloudLaneReserveLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun conAltoFijoLoDeAdentroTerminaAntesDeLaNube() {
        show {
            Box(modifier = Modifier.fillMaxSize().reserveCloudLane()) {
                Box(modifier = Modifier.fillMaxSize().testTag(CONTENT))
            }
        }
        val gap = with(composeRule.density) { CloudLaneGap.toPx() }
        val content = composeRule.onNodeWithTag(CONTENT).fetchSemanticsNode()
        val bottom = content.positionInWindow.y + content.size.height
        assertTrue(
            "El contenido termina en $bottom y la nube empieza en ${cloudTop()}",
            bottom <= cloudTop() - gap + 1
        )
    }

    @Test
    fun sinAltoFijoUnElementoQueEnvuelveSuContenidoConservaSuAlto() {
        show {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().reserveCloudLane().testTag(CONTENT)) {
                    Box(modifier = Modifier.height(WRAPPED_HEIGHT))
                }
            }
        }
        val expected = with(composeRule.density) { WRAPPED_HEIGHT.toPx() }
        assertEquals(expected, composeRule.onNodeWithTag(CONTENT).fetchSemanticsNode().size.height.toFloat(), 1f)
    }

    @Test
    fun dentroDeOtraZonaQueSeDesplazaNoFallaYConservaSuAlto() {
        show {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth().reserveCloudLane().testTag(CONTENT)) {
                    Box(modifier = Modifier.height(TALL_HEIGHT))
                }
            }
        }
        val expected = with(composeRule.density) { TALL_HEIGHT.toPx() }
        assertEquals(expected, composeRule.onNodeWithTag(CONTENT).fetchSemanticsNode().size.height.toFloat(), 1f)
    }

    @Test
    fun alVolverDeOtraRutaElDesplazamientoSigueAlFinal() {
        show {
            Column(modifier = Modifier.fillMaxSize().reserveCloudLane().verticalScroll(rememberScrollState())) {
                repeat(ROWS) { Text(text = "fila $it", modifier = Modifier.height(64.dp)) }
            }
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("fila ${ROWS - 1}"))
        composeRule.waitForIdle()
        assertTrue("Precondición: al final", isAtEnd())

        composeRule.runOnUiThread { navController.navigate(OTHER) }
        composeRule.waitForIdle()
        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()

        assertTrue("Al volver, el desplazamiento sigue al final", isAtEnd())
    }

    /** [content] como única pantalla del grafo seguro, con una segunda ruta a la que ir y volver. */
    private fun show(content: @Composable () -> Unit) {
        session.setContent(composeRule) {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = GRAPH) {
                secureGraph(route = GRAPH, startDestination = SCREEN) {
                    screen(SCREEN) { content() }
                    screen(OTHER) { Text("otra") }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    private fun cloudTop(): Float = composeRule.onNodeWithContentDescription(
        composeRule.activity.getString(R.string.access_back_to_weather)
    ).fetchSemanticsNode().boundsInWindow.top

    private fun isAtEnd(): Boolean {
        val range = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)
            ?: throw AssertionError("sin eje de desplazamiento vertical")
        return range.value() >= range.maxValue() - 1
    }

    private companion object {
        const val GRAPH = "g"
        const val SCREEN = "s"
        const val OTHER = "o"
        const val CONTENT = "content"
        const val ROWS = 40
        val WRAPPED_HEIGHT = 120.dp
        val TALL_HEIGHT = 3_000.dp
    }
}

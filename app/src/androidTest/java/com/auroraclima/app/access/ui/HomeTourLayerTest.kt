package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.PIXEL_ROUNDING
import com.auroraclima.app.core.ui.v2.SpotlightTargets
import com.auroraclima.app.core.ui.v2.awaitLayout
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El recorrido por Inicio (v0.3.3): los siete pasos en orden con su contador, «Saltar»
 * y el gesto atrás lo cierran, TalkBack lee título, frase y contador juntos, los
 * botones miden al menos 48 dp y, con letra grande, lo que está fuera de la vista se
 * desplaza a la vista antes de iluminarse. Se arma sobre el grafo seguro real.
 */
@RunWith(AndroidJUnit4::class)
class HomeTourLayerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val context get() = composeRule.activity
    private val next get() = context.getString(R.string.access_tour_next)
    private val skip get() = context.getString(R.string.access_tour_skip)
    private val done get() = context.getString(R.string.access_tour_done)

    private var step by mutableStateOf<HomeTourStep?>(null)
    private val targets = SpotlightTargets()

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    private fun show(
        initial: HomeTourStep?,
        fontScale: Float = 1f,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr
    ) {
        step = initial
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale),
                LocalLayoutDirection provides layoutDirection
            ) {
                HomeTourHarness(
                    step = step,
                    onNext = { step = step?.next },
                    onSkip = { step = null },
                    targets = targets
                )
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    @Test
    fun recorreLosSietePasosEnOrdenConSuContador() {
        show(HomeTourStep.first)

        HomeTourStep.entries.forEach { expected ->
            val (title, body, counter) = tourTexts(context, expected)
            bubble(title, body, counter).assertIsDisplayed()
            if (expected.isLast) {
                composeRule.onAllNodesWithText(skip).assertCountEquals(0)
                composeRule.onNodeWithText(done).performClick()
            } else {
                composeRule.onNodeWithText(skip).assertIsDisplayed()
                composeRule.onNodeWithText(next).performClick()
            }
            composeRule.waitForIdle()
        }

        assertNull("«Entendido» lo termina", step)
        composeRule.onAllNodesWithText(next).assertCountEquals(0)
        // Sin recorrido, el Inicio vuelve a estar al alcance de TalkBack.
        composeRule.onNodeWithText(context.getString(R.string.access_panel_title)).assertExists()
    }

    @Test
    fun saltarLoCierraDesdeUnPasoIntermedio() {
        show(HomeTourStep.GUIDE)

        composeRule.onNodeWithText(skip).performClick()
        composeRule.waitForIdle()

        assertNull(step)
        composeRule.onAllNodesWithText(skip).assertCountEquals(0)
    }

    @Test
    fun elGestoAtrasEquivaleASaltar() {
        show(HomeTourStep.SAVED)

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertNull(step)
        assertTrue("Atrás no saca del espacio interno", session.sessionManager.isSessionActive.value)
    }

    @Test
    fun talkBackLeeTituloFraseYContadorJuntosEnCadaPaso() {
        show(HomeTourStep.first)
        // Lo de abajo no le llega a TalkBack mientras el recorrido está a la vista
        // (el árbol fusionado es el que recorre la accesibilidad).
        composeRule.onAllNodesWithText(context.getString(R.string.access_panel_title)).assertCountEquals(0)

        HomeTourStep.entries.forEach { expected ->
            val (title, body, counter) = tourTexts(context, expected)
            bubble(title, body, counter)
                .assertExists()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            // El título del globo es un encabezado (el árbol sin fusionar conserva
            // también los nodos ocultos del Inicio, que no lo son).
            composeRule.onNode(
                hasText(title) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
                useUnmergedTree = true
            ).assertExists()
            composeRule.onNodeWithText(if (expected.isLast) done else next).performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun losBotonesMidenAlMenos48dp() {
        show(HomeTourStep.first)
        composeRule.onNodeWithText(next).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(skip).assertHeightIsAtLeast(48.dp)

        composeRule.runOnUiThread { step = HomeTourStep.CLOUD }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(done).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun conLetraGrandeLoQueEstaFueraDeLaVistaSeDesplazaAntesDeIluminarse() {
        show(initial = null, fontScale = LARGE_FONT)
        // Sin recorrido, lo de abajo se lee: con letra 2.0 «Acciones rápidas» queda
        // fuera de la vista. Su extensión completa: del rótulo al pie de la tarjeta más
        // baja (con letra 2.0 las tarjetas pueden ir apiladas).
        val label = composeRule.onNodeWithText(context.getString(R.string.access_home_section_quick))
            .fetchSemanticsNode()
        val bottom = listOf(R.string.access_home_call, R.string.access_home_note, R.string.access_home_learn)
            .maxOf { id ->
                val card = composeRule.onNodeWithText(context.getString(id)).fetchSemanticsNode()
                card.positionInRoot.y + card.size.height
            }
        val fullHeight = bottom - label.positionInRoot.y
        val visibleBefore = targets.boundsOf(HomeTourStep.SHORTCUTS)?.height ?: 0f
        assertTrue(
            "Precondición: con letra grande la sección no se ve entera ($visibleBefore de $fullHeight px)",
            visibleBefore < fullHeight - PIXEL_ROUNDING
        )

        composeRule.runOnUiThread { step = HomeTourStep.SHORTCUTS }
        val (title, body, counter) = tourTexts(context, HomeTourStep.SHORTCUTS)
        composeRule.awaitLayout(
            message = { "La sección se ve ${targets.boundsOf(HomeTourStep.SHORTCUTS)} de $fullHeight px" }
        ) {
            val visible = targets.boundsOf(HomeTourStep.SHORTCUTS)?.height ?: 0f
            abs(visible - fullHeight) <= PIXEL_ROUNDING + 1
        }
        // Y el globo de ese paso se muestra, ya con la sección entera a la vista.
        bubble(title, body, counter).assertIsDisplayed()
    }

    @Test
    fun elPasoDeLaNubeLaIluminaConSuGloboEncima() {
        show(HomeTourStep.CLOUD)
        val (title, body, counter) = tourTexts(context, HomeTourStep.CLOUD)

        val cloud = composeRule.onNodeWithContentDescription(context.getString(R.string.access_back_to_weather))
            .fetchSemanticsNode().boundsInWindow
        val bubbleBounds = bubble(title, body, counter).fetchSemanticsNode().boundsInWindow
        assertTrue(
            "El globo (${bubbleBounds.bottom}) va encima de la nube (${cloud.top})",
            bubbleBounds.bottom <= cloud.top + PIXEL_ROUNDING
        )
        assertEquals(1, composeRule.onAllNodesWithText(done).fetchSemanticsNodes().size)
    }

    @Test
    fun deDerechaAIzquierdaElGloboVaCentradoYNoPoneBotonesBajoLaNube() {
        // Con un idioma de derecha a izquierda la nube va a la izquierda; el globo se
        // ubica con coordenadas reales y su fila de botones se acorta por ese lado.
        show(HomeTourStep.first, layoutDirection = LayoutDirection.Rtl)
        val width = composeRule.onRoot().fetchSemanticsNode().boundsInWindow.width
        val cloudNode = composeRule.onNodeWithContentDescription(context.getString(R.string.access_back_to_weather))

        HomeTourStep.entries.forEach { expected ->
            composeRule.runOnUiThread { step = expected }
            composeRule.waitForIdle()
            val (title, body, counter) = tourTexts(context, expected)
            // El globo va centrado. Se mide su texto, sin recortar: el contenedor no tiene
            // nodo propio y los límites recortados por la ventana esconderían un globo
            // que se sale de la pantalla (el desvío de R-1 era de 16 dp a la derecha).
            val node = bubble(title, body, counter).fetchSemanticsNode()
            val center = node.positionInWindow.x + node.size.width / 2f
            assertTrue(
                "Paso ${expected.number}: el globo debe ir centrado (centro en $center, pantalla de $width)",
                abs(center - width / 2f) <= PIXEL_ROUNDING + 1
            )
            val cloud = cloudNode.fetchSemanticsNode().boundsInWindow
            listOf(next, skip, done)
                .flatMap { composeRule.onAllNodesWithText(it).fetchSemanticsNodes() }
                .forEach { button ->
                    assertTrue(
                        "Paso ${expected.number}: la nube ($cloud) tapa un botón (${button.boundsInWindow})",
                        !button.boundsInWindow.overlaps(cloud)
                    )
                }
        }
        assertTrue("La nube está a la izquierda", cloudNode.fetchSemanticsNode().boundsInWindow.center.x < width / 2)
    }

    /** El nodo del globo que TalkBack lee entero: título, frase y contador. */
    private fun bubble(title: String, body: String, counter: String) = composeRule.onNode(
        hasText(title) and hasText(body) and hasText(counter)
    )

    private companion object {
        const val LARGE_FONT = 2f
    }
}

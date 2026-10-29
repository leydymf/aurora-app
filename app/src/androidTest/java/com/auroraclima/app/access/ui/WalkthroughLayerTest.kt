package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.GuideTypeVoice
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SpotlightTargets
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La guía de Aurora sobre el Inicio real (v0.3.4): el recorrido completo en el orden
 * pedido con sus controles y el gesto atrás, la app de abajo inerte bajo la luz, y el
 * movimiento quieto con «Quitar animaciones». Se arma sobre el grafo seguro real.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughLayerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val context get() = composeRule.activity
    private fun text(id: Int) = context.getString(id)

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    private fun showWithViewModel(mark: WalkthroughMark?): WalkthroughViewModel {
        val viewModel = walkthroughViewModel(mark)
        session.setContent(composeRule) {
            val state by viewModel.uiState.collectAsState()
            WalkthroughHarness(state = state, actions = viewModel.asActions(), targets = SpotlightTargets())
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private fun assertStep(step: WalkthroughStep) {
        val (tag, title) = stepTexts(context, step)
        composeRule.onNode(hasText(title)).assertIsDisplayed()
        if (tag.isNotEmpty()) composeRule.onNodeWithText(tag).assertIsDisplayed()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    @Test
    fun recorreTodoEnOrdenConSusControles() {
        val viewModel = showWithViewModel(WalkthroughMark.FROM_START)
        composeRule.onNodeWithText(text(R.string.access_walk_intro_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.access_walk_intro_sub)).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.access_walk_start)).performClick()
        composeRule.waitForIdle()
        assertStep(WalkthroughStep.TYPE_IN)
        // En la primera tarjeta no hay «Atrás».
        composeRule.onAllNodesWithText(text(R.string.access_walk_back)).assertCountEquals(0)

        val spaceSteps = WalkthroughStep.entries.filter { it.part == WalkthroughStep.SPACE_PART }
        (spaceSteps + WalkthroughStep.CLOUD).forEach { expected ->
            composeRule.onNodeWithText(text(R.string.access_walk_next)).performClick()
            composeRule.waitForIdle()
            assertStep(expected)
            assertEquals(expected, viewModel.uiState.value.step)
        }
        // En la parte 3: «Atrás», «Después» y «Saltar», sin «Siguiente».
        composeRule.onAllNodesWithText(text(R.string.access_walk_next)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.access_walk_later)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.access_walk_skip)).assertIsDisplayed()
        // La segunda frase de «Salir» (D-1 de RF-10).
        composeRule.onNodeWithText("Si la dejas quieta, también vuelve sola.").assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.access_walk_back)).performClick()
        composeRule.waitForIdle()
        assertStep(WalkthroughStep.SETTINGS)
        composeRule.onNodeWithText(text(R.string.access_walk_next)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(text(R.string.access_walk_later)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.access_walk_done_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.access_walk_done_early)).assertIsDisplayed()
        // Los cuatro recordatorios, el último recuperado de D-1 de RF-10.
        listOf(
            "Tu código de acceso en el buscador.",
            "La nube.",
            "Ajustes › Cómo usar Aurora.",
            "Si alguien escribe un código de acceso equivocado, solo verá el clima."
        ).forEach { composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        composeRule.onNodeWithText("Discreta").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.access_walk_finish)).performClick()
        composeRule.waitForIdle()
        assertNull(viewModel.uiState.value.step)
        composeRule.onAllNodesWithText(text(R.string.access_walk_done_title)).assertCountEquals(0)
    }

    @Test
    fun ahoraNoLlevaAInicioSinGuia() {
        val viewModel = showWithViewModel(WalkthroughMark.FROM_START)
        composeRule.onNodeWithText(text(R.string.access_walk_not_now)).performClick()
        composeRule.waitForIdle()

        assertNull(viewModel.uiState.value.step)
        // La app vuelve a estar a la vista y a responder.
        composeRule.onNodeWithText(text(R.string.access_home_start)).assertIsDisplayed()
    }

    @Test
    fun saltarLlevaAListoConParaRecordar() {
        val viewModel = showWithViewModel(WalkthroughMark.FROM_START)
        composeRule.onNodeWithText(text(R.string.access_walk_start)).performClick()
        composeRule.onNodeWithText(text(R.string.access_walk_next)).performClick()
        composeRule.waitForIdle()
        assertStep(WalkthroughStep.GUIDE)

        assertEquals("Saltar", text(R.string.access_walk_skip))
        composeRule.onNodeWithText(text(R.string.access_walk_skip)).performClick()
        composeRule.waitForIdle()

        assertEquals(WalkthroughStep.DONE, viewModel.uiState.value.step)
        composeRule.onNodeWithText(text(R.string.access_walk_done_early)).assertIsDisplayed()
    }

    @Test
    fun elGestoAtrasEquivaleAAtrasYEnConoceAuroraAAhoraNo() {
        val viewModel = showWithViewModel(WalkthroughMark.FROM_START)
        composeRule.onNodeWithText(text(R.string.access_walk_start)).performClick()
        composeRule.onNodeWithText(text(R.string.access_walk_next)).performClick()
        composeRule.onNodeWithText(text(R.string.access_walk_next)).performClick()
        composeRule.waitForIdle()
        assertStep(WalkthroughStep.JOURNAL)

        pressBack()
        assertStep(WalkthroughStep.GUIDE)
        pressBack()
        assertStep(WalkthroughStep.TYPE_IN)
        pressBack()
        composeRule.onNodeWithText(text(R.string.access_walk_intro_title)).assertIsDisplayed()
        pressBack()
        assertNull(viewModel.uiState.value.step)
    }

    @Test
    fun laMarcaDeCierreMuestraListoConElTextoDeLaSalida() {
        showWithViewModel(WalkthroughMark.TO_FINISH)

        composeRule.onNodeWithText(text(R.string.access_walk_done_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.access_walk_done_back)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.access_walk_done_early)).assertCountEquals(0)
    }

    @Test
    fun losBotonesDeLaGuiaMidenAlMenos48dp() {
        showWithViewModel(WalkthroughMark.FROM_START)
        composeRule.onNodeWithText(text(R.string.access_walk_start)).performClick()
        composeRule.onNodeWithText(text(R.string.access_walk_next)).performClick()
        composeRule.waitForIdle()

        listOf(R.string.access_walk_next, R.string.access_walk_back, R.string.access_walk_skip).forEach {
            composeRule.onNodeWithText(text(it), useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNode(hasText(text(it)).and(hasClickAction()))
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun loIluminadoDeLaAppNoRespondeYNoLoVeTalkBack() {
        val targets = SpotlightTargets()
        val taps = mutableListOf<String>()
        var step by mutableStateOf<WalkthroughStep?>(WalkthroughStep.GUIDE)
        session.setContent(composeRule) {
            WalkthroughHarness(
                state = WalkthroughUiState(step = step),
                targets = targets,
                app = { taps += it }
            )
        }
        composeRule.waitForIdle()
        WalkthroughStep.entries.filter { it.part == WalkthroughStep.SPACE_PART }.forEach { lit ->
            composeRule.runOnUiThread { step = lit }
            composeRule.waitForIdle()
            val bounds = requireNotNull(targets.boundsOf(lit)) { "$lit sin elemento" }
            composeRule.onRoot().performTouchInput { click(bounds.center) }
            composeRule.waitForIdle()
            assertTrue("en $lit un toque bajo la luz llegó a la app: $taps", taps.isEmpty())
        }
        // Lo de la app no está para TalkBack mientras la guía está abierta.
        composeRule.onAllNodesWithText(text(R.string.access_panel_history_title)).assertCountEquals(0)

        // Cerrada la guía, la app responde otra vez.
        composeRule.runOnUiThread { step = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.access_home_start)).performClick()
        assertEquals(listOf("empezar"), taps)
    }

    @Test
    fun conLetraGrandeConoceAuroraSeDesplazaYSusBotonesSiguenALaVista() {
        // Con letra 2.0 en una pantalla pequeña el contenido no cabe: se desplaza (un
        // arrastre de verdad, no una acción de accesibilidad: es lo que se cancelaba
        // antes) y «Empezar» y «Ahora no», fijos abajo, están a la vista desde el inicio.
        var started = 0
        showLarge(WalkthroughUiState(step = WalkthroughStep.OVERVIEW), idleActions().copyWith(onStart = { started++ }))
        val start = composeRule.onNode(hasText(text(R.string.access_walk_start)) and hasClickAction())
        start.assertIsDisplayed()
        composeRule.onNode(hasText(text(R.string.access_walk_not_now)) and hasClickAction()).assertIsDisplayed()

        repeat(4) { composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.access_walk_intro_exit)).assertIsDisplayed()

        start.assertIsDisplayed().performClick()
        assertEquals(1, started)
    }

    @Test
    fun conLetraGrandeListoTrasLaSalidaSeDesplazaYIrAMiEspacioSigueALaVista() =
        assertDoneScrollsWithFinishInView(reachedEarly = false)

    @Test
    fun conLetraGrandeListoParaRecordarSeDesplazaYIrAMiEspacioSigueALaVista() =
        assertDoneScrollsWithFinishInView(reachedEarly = true)

    private fun assertDoneScrollsWithFinishInView(reachedEarly: Boolean) {
        showLarge(WalkthroughUiState(step = WalkthroughStep.DONE, reachedEarly = reachedEarly))
        val subtitle = if (reachedEarly) R.string.access_walk_done_early else R.string.access_walk_done_back
        composeRule.onNodeWithText(text(subtitle)).assertIsDisplayed()
        val finish = composeRule.onNode(hasText(text(R.string.access_walk_finish)) and hasClickAction())
        finish.assertIsDisplayed()

        repeat(4) { composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() } }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(text(R.string.access_walk_recall_discreet)).assertIsDisplayed()
        finish.assertIsDisplayed()
    }

    /** Letra 2.0 en una pantalla de 320×568: lo más justo que se prueba. */
    private fun showLarge(state: WalkthroughUiState, actions: WalkthroughActions = idleActions()) {
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                Box(modifier = Modifier.requiredSize(320.dp, 568.dp)) {
                    WalkthroughHarness(state = state, actions = actions, targets = SpotlightTargets())
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun lasPantallasCompletasVanConLaLetraDelInteriorYLasTarjetasConLaDeLaGuia() {
        var state by mutableStateOf(WalkthroughUiState(step = WalkthroughStep.OVERVIEW))
        session.setContent(composeRule) {
            WalkthroughHarness(state = state, targets = SpotlightTargets())
        }
        composeRule.waitForIdle()
        val handwritten = GuideTypeVoice.cardTitle.fontFamily
        // «Conoce Aurora» y «¡Listo!» no se superponen a la app: su título es el del
        // interior (Marcellus), nunca la letra a mano.
        listOf(
            WalkthroughStep.OVERVIEW to R.string.access_walk_intro_title,
            WalkthroughStep.DONE to R.string.access_walk_done_title
        ).forEach { (step, title) ->
            composeRule.runOnUiThread { state = WalkthroughUiState(step = step) }
            composeRule.waitForIdle()
            val family = fontFamilyOf(text(title))
            assertEquals("título de $step", InteriorTypeVoice.titleFamily, family)
            assertTrue("título de $step con la letra a mano", family != handwritten)
        }
        // Las tarjetas superpuestas conservan la letra a mano.
        composeRule.runOnUiThread { state = WalkthroughUiState(step = WalkthroughStep.GUIDE) }
        composeRule.waitForIdle()
        assertEquals(handwritten, fontFamilyOf(text(WalkthroughStep.GUIDE.titleRes)))
    }

    private fun fontFamilyOf(label: String): FontFamily? {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNode(hasText(label)).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.first().layoutInput.style.fontFamily
    }

    @Test
    fun bajoUnaPantallaDeLaGuiaLaAppNoRecibeToques() {
        val taps = mutableListOf<String>()
        session.setContent(composeRule) {
            WalkthroughHarness(
                state = WalkthroughUiState(step = WalkthroughStep.OVERVIEW),
                targets = SpotlightTargets(),
                app = { taps += it }
            )
        }
        composeRule.waitForIdle()
        // Toques por toda la pantalla, donde debajo están las tarjetas y las pestañas.
        listOf(0.2f, 0.4f, 0.6f, 0.8f, 0.95f).forEach { y ->
            composeRule.onRoot().performTouchInput {
                click(
                    androidx.compose.ui.geometry.Offset(width * 0.3f, height * y)
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue("llegó a la app: $taps", taps.isEmpty())
    }

    @Test
    fun conAnimacionesReducidasNadaSeMueve() {
        assertTrue(motionCaptures(reduceMotion = true).let { (early, late) -> early.contentEquals(late) })
    }

    @Test
    fun conAnimacionesElCambioDePasoSeMueve() {
        assertFalse(motionCaptures(reduceMotion = false).let { (early, late) -> early.contentEquals(late) })
    }

    /**
     * Dos capturas del mismo cambio de paso (de «Guía» a «Mi registro»): poco después
     * del cambio y cuando ya debería haber terminado todo movimiento finito.
     */
    private fun motionCaptures(reduceMotion: Boolean): Pair<IntArray, IntArray> {
        var step by mutableStateOf(WalkthroughStep.GUIDE)
        session.setContent(composeRule) {
            WalkthroughHarness(
                state = WalkthroughUiState(step = step),
                motion = WalkthroughMotion.forMotion(reduceMotion),
                targets = SpotlightTargets()
            )
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { step = WalkthroughStep.JOURNAL }
        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)
        val early = pixels()
        composeRule.mainClock.advanceTimeBy(LATE_MILLIS)
        val late = pixels()
        composeRule.mainClock.autoAdvance = true
        return early to late
    }

    private fun pixels(): IntArray {
        val image = composeRule.onRoot().captureToImage()
        return IntArray(image.width * image.height).also { image.readPixels(it) }
    }

    private companion object {
        const val EARLY_MILLIS = 64L
        const val LATE_MILLIS = 900L
    }
}

package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.access.domain.repository.WalkthroughMark
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.SpotlightTargets
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La parte 1 de «Cómo usar Aurora» (RF-10, CP-06): la práctica sobre la réplica de la
 * pantalla de clima. El código se valida tras la pausa y al pulsar «Buscar»; si no
 * coincide, el aviso se ve justo debajo de la barra y TalkBack lo anuncia; «Siguiente»
 * avanza sin practicar; con letra grande nada tapa la tarjeta ni la barra, y la nube
 * sale con un toque también con el aviso a la vista.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughPracticeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private val context get() = composeRule.activity
    private fun text(id: Int) = context.getString(id)

    private val bar get() = composeRule.onNode(hasSetTextAction())
    private val retryNotice = hasText(CODE_RETRY)

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    /** La guía en la parte 1 con el ViewModel real y, si se da, un código de acceso guardado. */
    private fun showPractice(code: String? = null): WalkthroughViewModel {
        val viewModel = walkthroughViewModel(WalkthroughMark.FROM_START, code)
        session.setContent(composeRule) {
            val state by viewModel.uiState.collectAsState()
            WalkthroughHarness(state = state, actions = viewModel.asActions(), targets = SpotlightTargets())
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasText(text(R.string.access_walk_start))).performClick()
        composeRule.waitForIdle()
        return viewModel
    }

    private fun waitFor(timeoutMillis: Long = WAIT_MILLIS, condition: () -> Boolean) =
        composeRule.waitUntil(timeoutMillis, condition)

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInWindow

    @Test
    fun laReplicaSeVeComoLaPantallaDeClima() {
        showPractice()
        // El mensaje tiene el texto pedido y la réplica, los datos fijos de ejemplo.
        assertEquals(CODE_RETRY, text(R.string.access_walk_practice_retry))
        listOf(
            text(R.string.weather_search_hint),
            text(R.string.access_walk_sample_city),
            text(R.string.access_walk_sample_temperature),
            text(R.string.weather_condition_partly_cloudy)
        ).forEach { composeRule.onNode(hasText(it)).performScrollTo().assertIsDisplayed() }
        // Sin escribir no hay aviso.
        composeRule.onNode(retryNotice).assertDoesNotExist()
    }

    @Test
    fun elCodigoCorrectoCoincideTrasLaPausa() {
        val viewModel = showPractice(code = CODE)
        bar.performTextInput(CODE)

        waitFor { viewModel.uiState.value.practiceStatus == PracticeStatus.MATCHED }
        composeRule.waitForIdle()
        // La tarjeta pasa a la confirmación y la guía sigue sola.
        composeRule.onNode(hasText(text(R.string.access_walk_practice_done))).assertIsDisplayed()
        composeRule.onNode(retryNotice).assertDoesNotExist()
        waitFor { viewModel.uiState.value.step == WalkthroughStep.GUIDE }
    }

    @Test
    fun elCodigoCorrectoCoincideAlPulsarBuscar() {
        val viewModel = showPractice(code = CODE)
        bar.performTextInput(CODE)
        bar.performImeAction()

        waitFor { viewModel.uiState.value.practiceStatus == PracticeStatus.MATCHED }
        composeRule.waitForIdle()
        composeRule.onNode(hasText(text(R.string.access_walk_practice_done))).assertIsDisplayed()
        waitFor { viewModel.uiState.value.step == WalkthroughStep.GUIDE }
    }

    /**
     * Que «Buscar» valida en el acto, sin depender del reloj del emulador: con menos de
     * seis caracteres la pausa nunca evalúa, así que el aviso solo puede venir de
     * «Buscar». (Que no espera la pausa lo fija con tiempo virtual la prueba JVM.)
     */
    @Test
    fun buscarValidaEnElActoAunqueLaPausaNoEvalue() {
        val viewModel = showPractice(code = CODE)
        bar.performTextInput("ama")
        composeRule.waitForIdle()
        Thread.sleep(WalkthroughViewModel.PRACTICE_DEBOUNCE_MILLIS * 2)
        assertEquals("la pausa no evalúa tan poco", PracticeStatus.TYPING, viewModel.uiState.value.practiceStatus)

        bar.performImeAction()

        waitFor { viewModel.uiState.value.practiceStatus == PracticeStatus.MISMATCH }
        composeRule.waitForIdle()
        composeRule.onNode(retryNotice).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unCodigoIncorrectoAvisaAlPulsarBuscarYTalkBackLoAnuncia() {
        val viewModel = showPractice(code = CODE)
        bar.performTextInput("otracosa1")
        bar.performImeAction()

        waitFor { viewModel.uiState.value.practiceStatus == PracticeStatus.MISMATCH }
        composeRule.waitForIdle()
        val notice = composeRule.onNode(
            retryNotice and SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        notice.performScrollTo().assertIsDisplayed()
        // Justo debajo de la barra.
        val barBounds = bar.bounds()
        val noticeBounds = notice.bounds()
        assertTrue("el aviso ($noticeBounds) va debajo de la barra ($barBounds)", noticeBounds.top >= barBounds.bottom)
        assertTrue(
            "el aviso ($noticeBounds) va justo debajo de la barra ($barBounds)",
            noticeBounds.top - barBounds.bottom < barBounds.height
        )
        assertEquals(WalkthroughStep.TYPE_IN, viewModel.uiState.value.step)

        // Al volver a escribir, desaparece.
        bar.performTextInput("x")
        composeRule.waitForIdle()
        composeRule.onNode(retryNotice).assertDoesNotExist()
    }

    @Test
    fun unCodigoIncorrectoAvisaTrasLaPausa() {
        val viewModel = showPractice(code = CODE)
        bar.performTextInput("otracosa1")

        waitFor { viewModel.uiState.value.practiceStatus == PracticeStatus.MISMATCH }
        composeRule.waitForIdle()
        composeRule.onNode(retryNotice).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun siguienteEstaHabilitadoSinPracticar() {
        val viewModel = showPractice(code = CODE)
        composeRule.onNode(hasText(text(R.string.access_walk_next)) and hasClickAction())
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(WalkthroughStep.GUIDE, viewModel.uiState.value.step)
    }

    @Test
    fun conLetraGrandeLaReplicaNoTapaLaTarjetaNiLaBarra() = assertNothingCoversLargeFont(small = false)

    @Test
    fun conLetraGrandeEnPantallaPequenaLaReplicaNoTapaLaTarjetaNiLaBarra() = assertNothingCoversLargeFont(small = true)

    /**
     * Con letra 2.0 (y, si [small], en una pantalla de 320 × 568 dp) y el aviso a la
     * vista: ni la tarjeta ni las piezas de la réplica se montan sobre la barra, la
     * réplica no se monta sobre la tarjeta y la barra se alcanza desplazando.
     */
    private fun assertNothingCoversLargeFont(small: Boolean) {
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, 2f)) {
                Box(modifier = if (small) Modifier.requiredSize(320.dp, 568.dp) else Modifier.fillMaxSize()) {
                    WalkthroughHarness(state = mismatchState, targets = SpotlightTargets())
                }
            }
        }
        composeRule.waitForIdle()
        val case = if (small) "letra 2.0, pantalla pequeña" else "letra 2.0"
        val card = listOf(
            composeRule.onNode(hasText(text(R.string.access_walk_enter_title))),
            composeRule.onNode(hasText(text(R.string.access_walk_next)) and hasClickAction()),
            composeRule.onNode(hasText(text(R.string.access_walk_skip)) and hasClickAction())
        ).map { it.bounds() }
        val sample = listOf(
            composeRule.onNode(retryNotice),
            composeRule.onNode(hasText(text(R.string.access_walk_sample_city))),
            composeRule.onNode(hasText(text(R.string.access_walk_sample_temperature))),
            composeRule.onNode(hasText(text(R.string.weather_condition_partly_cloudy)))
        ).map { it.bounds() }
        val barBounds = bar.bounds()
        (card + sample).forEach {
            assertFalse("$case: $it tapa la barra ($barBounds)", it.overlaps(barBounds))
        }
        sample.forEach { piece ->
            card.forEach { assertFalse("$case: la réplica ($piece) tapa la tarjeta ($it)", piece.overlaps(it)) }
        }
        // La barra se alcanza desplazando y queda a la vista.
        bar.performScrollTo().assertIsDisplayed()
    }

    @Test
    fun laNubeSaleConUnToqueConElAvisoALaVista() = assertCloudExitsWithOneTap(fontScale = 1f)

    @Test
    fun conLetraGrandeLaNubeSaleConUnToqueConElAvisoALaVista() = assertCloudExitsWithOneTap(fontScale = 2f)

    private fun assertCloudExitsWithOneTap(fontScale: Float) {
        session.setContent(composeRule) {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                WalkthroughHarness(state = mismatchState, targets = SpotlightTargets())
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
        composeRule.onNode(retryNotice).assertExists()

        composeRule.onNodeWithContentDescription(text(R.string.access_back_to_weather))
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        assertFalse(
            "letra $fontScale: un toque a la nube debe cerrar la sesión",
            session.sessionManager.isSessionActive.value
        )
    }

    private companion object {
        const val CODE = "amanecer26"
        const val CODE_RETRY = "Ese no es tu código de acceso. Inténtalo de nuevo."
        const val WAIT_MILLIS = 5_000L

        val mismatchState = WalkthroughUiState(
            step = WalkthroughStep.TYPE_IN,
            practiceInput = "otracosa1",
            practiceStatus = PracticeStatus.MISMATCH
        )
    }
}

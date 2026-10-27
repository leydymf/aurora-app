package com.auroraclima.app.access.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.CloudAnchor
import com.auroraclima.app.core.security.LocalCloudAnchor
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.security.secureGraph
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.PIXEL_ROUNDING
import com.auroraclima.app.core.ui.v2.PulsePlan
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La práctica del tutorial señala dónde actuar (v0.3.3, RF-10): halo, flecha y
 * etiqueta en la barra; al coincidir el código, la etiqueta confirma el ingreso y la
 * señal pasa a la nube real, que sigue saliendo con un toque (RF-16). Con «Quitar
 * animaciones» el halo y la flecha se ven fijos: dos capturas separadas en el tiempo
 * son idénticas, y sin la preferencia difieren.
 *
 * Las capturas se toman sin el grafo seguro (FLAG_SECURE impediría copiar la
 * ventana); el ancla de la nube se da a mano, en la esquina donde la pone el grafo.
 * La posición real y el toque se prueban con el grafo seguro de verdad.
 */
@RunWith(AndroidJUnit4::class)
class TutorialPracticeCueTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    private var state by mutableStateOf(TutorialUiState(step = TutorialStep.PRACTICE))

    private val hint get() = composeRule.activity.getString(R.string.access_guide_practice_hint)
    private val done get() = composeRule.activity.getString(R.string.access_guide_practice_done)
    private val cloudHint get() = composeRule.activity.getString(R.string.access_guide_practice_cloud_hint)
    private val cloudLabel get() = composeRule.activity.getString(R.string.access_back_to_weather)

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun laSenalApuntaALaBarraYAlCoincidirPasaALaNube() {
        showInSecureGraph()

        // Paso 1: etiqueta y flecha encima de la barra.
        composeRule.onNodeWithText(hint).assertIsDisplayed()
        assertTrue(
            "La etiqueta va encima de la barra",
            node(hint).boundsInWindow.bottom <= bar().boundsInWindow.top + PIXEL_ROUNDING
        )
        composeRule.onAllNodesWithText(cloudHint).assertCountEquals(0)

        // Paso 2: la etiqueta confirma y la señal se muda a la nube.
        match()
        composeRule.onAllNodesWithText(hint).assertCountEquals(0)
        composeRule.onNodeWithText(done).assertIsDisplayed()
        composeRule.onNodeWithText(cloudHint).assertIsDisplayed()
        val cloud = composeRule.onNodeWithContentDescription(cloudLabel).fetchSemanticsNode().boundsInWindow
        val cue = node(cloudHint).boundsInWindow
        assertTrue("La etiqueta va encima de la nube", cue.bottom <= cloud.top + PIXEL_ROUNDING)
        assertTrue(
            "La etiqueta termina alineada con la nube (${cue.right} y ${cloud.right})",
            abs(cue.right - cloud.right) <= PIXEL_ROUNDING + 1
        )

        // La señal no tapa nada: el primer toque a la nube sale.
        composeRule.onNodeWithContentDescription(cloudLabel).performClick()
        composeRule.waitForIdle()
        assertFalse("Un toque a la nube cierra la sesión", session.sessionManager.isSessionActive.value)
    }

    @Test
    fun conAnimacionesReducidasElHaloYLaFlechaDeLaBarraQuedanFijos() {
        showForCapture(PulsePlan.forMotion(reduceMotion = true))
        val region = barRegion()

        val before = pixels(region)
        composeRule.mainClock.advanceTimeBy(CAPTURE_GAP_MS)
        assertArrayEquals("El halo y la flecha no se mueven", before, pixels(region))

        // Fijos pero visibles: justo encima de la barra, fuera de ella, asoma el halo.
        val bar = bar().boundsInRoot
        val probeY = bar.top - with(composeRule.density) { 3.dp.toPx() }
        assertNotEquals(
            "El halo se ve aunque esté quieto",
            V2Palette.screenBackground.toArgb(),
            pixelAt(bar.center.x, probeY)
        )
    }

    @Test
    fun sinReduccionElHaloDeLaBarraLate() {
        showForCapture(PulsePlan.forMotion(reduceMotion = false))
        val region = barRegion()

        val before = pixels(region)
        composeRule.mainClock.advanceTimeBy(CAPTURE_GAP_MS)
        assertFalse("El halo se mueve", before.contentEquals(pixels(region)))
    }

    @Test
    fun conAnimacionesReducidasLaSenalDeLaNubeQuedaFija() {
        showForCapture(PulsePlan.forMotion(reduceMotion = true))
        match()
        val region = cloudRegion()

        val before = pixels(region)
        composeRule.mainClock.advanceTimeBy(CAPTURE_GAP_MS)
        assertArrayEquals("El halo y la flecha de la nube no se mueven", before, pixels(region))
    }

    @Test
    fun deDerechaAIzquierdaLaSenalRodeaLaNubeDondeEsta() {
        // La nube va a la izquierda: el halo debe asomar junto a ella (no reflejado al
        // otro lado) y la etiqueta quedar encima, alineada con su borde izquierdo.
        showForCapture(PulsePlan.forMotion(reduceMotion = true), LayoutDirection.Rtl)
        match()

        val cloud = cloudSample()
        assertTrue(
            "La nube de muestra está a la izquierda",
            cloud.center.x < composeRule.onRoot().fetchSemanticsNode().size.width / 2
        )
        val probeX = cloud.left - with(composeRule.density) { 3.dp.toPx() }
        assertNotEquals(
            "El halo asoma junto a la nube",
            V2Palette.screenBackground.toArgb(),
            pixelAt(probeX, cloud.center.y)
        )
        val cue = node(cloudHint).boundsInRoot
        assertTrue("La etiqueta va encima de la nube", cue.bottom <= cloud.top + PIXEL_ROUNDING)
        assertTrue(
            "La etiqueta empieza alineada con la nube (${cue.left} y ${cloud.left})",
            abs(cue.left - cloud.left) <= PIXEL_ROUNDING + 1
        )
    }

    /** El tutorial dentro del grafo seguro real: la nube y su ancla las pone `SecureScreen`. */
    private fun showInSecureGraph() {
        session.setContent(composeRule) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = GRAPH) {
                secureGraph(route = GRAPH, startDestination = SCREEN) {
                    screen(SCREEN) { Tutorial(PulsePlan.forMotion(reduceMotion = true)) }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    /** Sin bandera de seguridad, para poder capturar; una nube de muestra en su esquina. */
    private fun showForCapture(plan: PulsePlan, layoutDirection: LayoutDirection = LayoutDirection.Ltr) {
        composeRule.mainClock.autoAdvance = false
        val anchor = CloudAnchor()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalSessionManager provides session.sessionManager,
                LocalCloudAnchor provides anchor,
                LocalLayoutDirection provides layoutDirection
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Tutorial(plan)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                            .size(52.dp)
                            .background(V2Palette.accentCloud.tint)
                            .testTag(CLOUD_SAMPLE)
                            .onGloballyPositioned { anchor.bounds = it.boundsInWindow() }
                    )
                }
            }
        }
        settle()
    }

    @Composable
    private fun Tutorial(plan: PulsePlan) {
        AuroraV2Theme(V2Voice.Interior) {
            TutorialScreen(
                state = state,
                isReview = false,
                onStartPractice = {},
                onSkip = {},
                onBack = {},
                onPracticeInputChange = {},
                cuePlan = plan
            )
        }
    }

    private fun match() {
        composeRule.runOnUiThread { state = state.copy(practiceStatus = PracticeStatus.MATCHED) }
        settle()
    }

    /**
     * Unos cuadros: la señal de la nube se coloca en dos pasadas (primero mide su
     * capa, luego se ubica y reserva su aire) y con el reloj manual nada avanza solo.
     */
    private fun settle() {
        repeat(SETTLE_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun node(text: String): SemanticsNode = composeRule.onNodeWithText(text).fetchSemanticsNode()

    private fun bar(): SemanticsNode = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode()

    /** La nube de muestra (va donde la pondría el grafo: abajo en la esquina final). */
    private fun cloudSample() = composeRule.onNodeWithTag(CLOUD_SAMPLE).fetchSemanticsNode().boundsInRoot

    /** De la etiqueta al pie de la barra, con el margen que ocupa el halo abierto. */
    private fun barRegion(): Rect {
        val margin = with(composeRule.density) { 16.dp.toPx() }
        val label = node(hint).boundsInRoot
        val bar = bar().boundsInRoot
        return Rect(bar.left - margin, label.top, bar.right + margin, bar.bottom + margin)
    }

    /** De la etiqueta de la nube al pie de la nube, con el margen del halo. */
    private fun cloudRegion(): Rect {
        val margin = with(composeRule.density) { 16.dp.toPx() }
        val cue = node(cloudHint).boundsInRoot
        val bottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        return Rect(cue.left - margin, cue.top, cue.right, bottom)
    }

    private fun pixels(region: Rect): IntArray {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val left = region.left.roundToInt().coerceIn(0, bitmap.width - 1)
        val top = region.top.roundToInt().coerceIn(0, bitmap.height - 1)
        val width = (region.right.roundToInt() - left).coerceIn(1, bitmap.width - left)
        val height = (region.bottom.roundToInt() - top).coerceIn(1, bitmap.height - top)
        return IntArray(width * height).also { bitmap.getPixels(it, 0, width, left, top, width, height) }
    }

    private fun pixelAt(x: Float, y: Float): Int =
        composeRule.onRoot().captureToImage().asAndroidBitmap().getPixel(x.roundToInt(), y.roundToInt())

    private companion object {
        const val GRAPH = "g"
        const val SCREEN = "s"

        // Un cuarto del ciclo: el halo animado va a medio abrir y ya se nota.
        const val CAPTURE_GAP_MS = 400L
        const val SETTLE_FRAMES = 5
        const val CLOUD_SAMPLE = "cloud_sample"
    }
}

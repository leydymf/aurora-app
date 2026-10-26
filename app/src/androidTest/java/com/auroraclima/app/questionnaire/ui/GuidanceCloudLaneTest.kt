package com.auroraclima.app.questionnaire.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.R
import com.auroraclima.app.core.security.QuickExitCloud
import com.auroraclima.app.core.security.TestSession
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.configureLikeMainActivity
import com.auroraclima.app.questionnaire.domain.model.Block
import com.auroraclima.app.questionnaire.domain.model.Dimension
import com.auroraclima.app.questionnaire.domain.model.GuidanceLevel
import com.auroraclima.app.questionnaire.domain.model.GuidanceResult
import com.auroraclima.app.questionnaire.domain.model.Municipality
import com.auroraclima.app.questionnaire.domain.model.Question
import com.auroraclima.app.questionnaire.domain.model.Questionnaire
import com.auroraclima.app.questionnaire.domain.model.Recognition
import com.auroraclima.app.questionnaire.domain.model.Scale
import com.auroraclima.app.questionnaire.domain.model.ScaleOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La nube de salida rápida (RF-16) flota abajo a la derecha en TODA ruta interna; en
 * las preguntas y el resultado la fila inferior le deja su carril (v0.3.2): los botones
 * y la nube quedan en una misma línea, sin taparse, también en pantallas pequeñas y con
 * la fuente grande. La barra de resultados es fija: «Guardar» y «Terminar» se ven sin
 * desplazarse aunque el contenido sea largo.
 *
 * La réplica de la ruta segura (`SecureScreen`) es la pantalla más la nube anclada
 * abajo a la derecha con [QuickExitCloud], igual que en la app.
 */
@RunWith(AndroidJUnit4::class)
class GuidanceCloudLaneTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val session = TestSession()

    @Before
    fun setUp() {
        composeRule.configureLikeMainActivity()
    }

    @Test
    fun preguntasLaFlechaSiguienteYLaNubeQuedanEnUnaMismaLinea() {
        show(question())

        val back = rect(string(R.string.common_back), byDescription = true)
        val next = rect(string(R.string.guidance_next))
        val cloud = cloud()
        assertSameLine(back, cloud)
        assertSameLine(next, cloud)
        assertDisjoint(back, next, cloud)
    }

    @Test
    fun preguntasEnPantallaPequenaYFuenteGrandeNadaSeTapa() {
        show(question(), size = SMALL, fontScale = 2f)

        val back = rect(string(R.string.common_back), byDescription = true)
        val next = rect(string(R.string.guidance_next))
        val cloud = cloud()
        assertSameLine(next, cloud)
        assertDisjoint(back, next, cloud)
    }

    @Test
    fun resultadoGuardarTerminarYLaNubeQuedanEnUnaMismaLinea() {
        show(result())

        val save = rect(string(R.string.guidance_result_save))
        val finish = rect(string(R.string.guidance_result_finish))
        val cloud = cloud()
        assertSameLine(save, cloud)
        assertSameLine(finish, cloud)
        assertDisjoint(save, finish, cloud)
        assertTrue("«Guardar» es la acción principal y va primero", save.left < finish.left)
    }

    @Test
    fun resultadoLaBarraEsFijaAunConContenidoLargo() {
        show(result(), size = SMALL)

        composeRule.onNodeWithText(string(R.string.guidance_result_save)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.guidance_result_finish)).assertIsDisplayed()
    }

    @Test
    fun resultadoConFuenteGrandeLosBotonesSeApilanSinTaparLaNube() {
        show(result(), size = SMALL, fontScale = 2f)

        val save = rect(string(R.string.guidance_result_save))
        val finish = rect(string(R.string.guidance_result_finish))
        val cloud = cloud()
        assertDisjoint(save, finish, cloud)
        assertTrue("apilados: «Guardar» arriba de «Terminar»", save.bottom <= finish.top)
        assertSameLine(finish, cloud)
        composeRule.onNodeWithText(string(R.string.guidance_result_save)).assertIsDisplayed()
    }

    @Test
    fun resultadoGuardadoLaConstanciaYTerminarSeVenJuntoALaNube() {
        show(result(isSaved = true))

        composeRule.onNodeWithText(string(R.string.guidance_result_saved)).assertIsDisplayed()
        val finish = rect(string(R.string.guidance_result_finish))
        assertDisjoint(finish, cloud())
    }

    private fun show(state: GuidanceUiState, size: DpSize? = null, fontScale: Float = 1f) {
        session.setContent(composeRule) {
            AuroraV2Theme(V2Voice.Interior) {
                Scaled(fontScale) {
                    Box(modifier = if (size != null) Modifier.requiredSize(size) else Modifier.fillMaxSize()) {
                        GuidanceScreen(
                            state = state,
                            onStart = {}, onFinish = {}, onLeaveRequested = {}, onLeaveConfirmed = {},
                            onLeaveDismissed = {}, onOptionSelected = {}, onNext = {}, onPrevious = {},
                            onMunicipalitySelected = {}, onOpenRoutes = {}, onRetry = {}, onSaveRequested = {},
                            onSaveConfirmed = {}, onSaveDismissed = {}
                        )
                        QuickExitCloud(modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }
            }
        }
        composeRule.runOnUiThread { session.sessionManager.startSession() }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Scaled(fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), content = content)
    }

    private fun cloud() = rect(string(R.string.access_back_to_weather), byDescription = true)

    private fun rect(text: String, byDescription: Boolean = false): Rect {
        val node: SemanticsNodeInteraction = if (byDescription) {
            composeRule.onNodeWithContentDescription(text)
        } else {
            composeRule.onNodeWithText(text)
        }
        val semantics = node.fetchSemanticsNode()
        val origin = semantics.positionOnScreen
        return Rect(origin.x, origin.y, origin.x + semantics.size.width, origin.y + semantics.size.height)
    }

    private fun assertSameLine(a: Rect, b: Rect) {
        assertEquals("deben compartir línea: $a y $b", a.center.y, b.center.y, LINE_TOLERANCE_PX)
    }

    private fun assertDisjoint(vararg rects: Rect) {
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                assertTrue("se tapan: ${rects[i]} y ${rects[j]}", !rects[i].overlaps(rects[j]))
            }
        }
    }

    private fun string(id: Int) = composeRule.activity.getString(id)

    private fun question() = GuidanceUiState(
        isLoading = false,
        questionnaire = sampleQuestionnaire(),
        phase = GuidancePhase.QUESTION
    )

    private fun result(isSaved: Boolean = false) = GuidanceUiState(
        isLoading = false,
        questionnaire = sampleQuestionnaire(),
        phase = GuidancePhase.RESULT,
        result = GuidanceResult(sampleLevel(), emptyList(), score = 3, answeredCount = 3, questionCount = 3),
        isSaved = isSaved
    )

    private fun sampleLevel() = GuidanceLevel(
        id = "n01",
        order = 1,
        minScore = 0,
        title = "Nivel 1",
        summary = LONG_TEXT,
        advice = listOf(LONG_TEXT, LONG_TEXT, LONG_TEXT),
        lineIds = emptyList(),
        agencyIds = emptyList(),
        municipalAgencyIds = emptyMap()
    )

    private fun sampleQuestionnaire() = Questionnaire(
        id = "f01",
        version = 1,
        title = "Título",
        intro = "Intro",
        notice = "Aviso",
        scales = listOf(Scale("freq", listOf(ScaleOption("s0", 1, "Nunca", 0), ScaleOption("s1", 2, "A veces", 1)))),
        blocks = listOf(Block("a", 1, "Bloque")),
        questions = listOf(
            Question("q01", 1, "a", "freq", "primera", weight = 1),
            Question("q02", 2, "a", "freq", "segunda", weight = 1)
        ),
        dimensions = listOf(Dimension("d1", 1, "tipo", listOf("q01"), 1)),
        recognition = Recognition("Por tus respuestas", "Sin señales"),
        thresholdFlags = emptyList(),
        countFlags = emptyList(),
        municipalities = listOf(Municipality("m1", 1, "Bucaramanga")),
        levels = listOf(sampleLevel())
    )

    private companion object {
        val SMALL = DpSize(320.dp, 568.dp)
        const val LINE_TOLERANCE_PX = 2f
        const val LONG_TEXT = "Texto largo de orientación que ocupa varias líneas para que el contenido " +
            "del resultado necesite desplazarse y la barra inferior tenga que quedarse fija a la vista."
    }
}

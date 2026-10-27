package com.auroraclima.app.access.ui

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.breaksOnlyAtSpaces
import com.auroraclima.app.core.ui.v2.scaledBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Las etiquetas de Acciones rápidas no parten palabras (v0.3.3): con letra grande,
 * «Informarme» se veía «Infor / ma…». En cualquier ancho y escala, cada etiqueta se
 * ve entera, en dos líneas como mucho, y si ocupa dos el corte va entre palabras. Con
 * letra normal en un ancho de teléfono no se reduce nada, y cuando se reduce es solo lo
 * necesario (con un 10 % más de letra ya no cabría).
 *
 * Se leen los cortes de línea de la maquetación que expone la semántica
 * (`GetTextLayoutResult`), que coinciden con los dibujados (comprobado contra
 * `onTextLayout` y una captura). Su marca de desborde no se usa: informa el ancho del
 * párrafo y no el del texto, y daba desborde con etiquetas que se veían enteras.
 */
@RunWith(Parameterized::class)
class QuickAccessLabelTest(private val widthDp: Int, private val fontScale: Float) {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val labels = listOf(R.string.access_home_call, R.string.access_home_note, R.string.access_home_learn)
        .map { context.getString(it) }
    private val case get() = "ancho $widthDp dp, letra $fontScale"
    private lateinit var measurer: TextMeasurer

    @Test
    fun ningunaEtiquetaParteUnaPalabraNiSeCorta() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                measurer = rememberTextMeasurer()
                AuroraV2Theme(V2Voice.Interior) {
                    QuickAccessRow(
                        onCall = {},
                        onSaveNote = {},
                        onLearn = {},
                        modifier = Modifier.requiredWidth(widthDp.dp)
                    )
                }
            }
        }
        composeRule.waitForIdle()

        labels.forEach { label ->
            val layout = layoutOf(label)
            assertTrue("$case, «$label»: más de dos líneas (${layout.lineCount})", layout.lineCount <= 2)
            assertFalse(
                "$case, «$label»: cortada con puntos suspensivos",
                layout.isLineEllipsized(layout.lineCount - 1)
            )
            assertEquals("$case, «$label»: no se ve entera", label.length, layout.getLineEnd(layout.lineCount - 1))
            (0 until layout.lineCount - 1).forEach { line ->
                val end = layout.getLineEnd(line)
                assertTrue(
                    "$case, «$label»: la línea ${line + 1} termina en «${label.substring(0, end)}», a mitad de palabra",
                    label[end - 1].isWhitespace()
                )
            }
            if (fontScale == 1f && widthDp >= PHONE_WIDTH_DP) {
                assertEquals(
                    "$case, «$label»: con letra normal no se reduce",
                    InteriorTypeVoice.label.fontSize,
                    layout.layoutInput.style.fontSize
                )
            }
            assertNotReducedTooMuch(label, layout)
        }
    }

    /**
     * «Solo lo necesario»: si la etiqueta se redujo, con un 10 % más de letra (sin pasar
     * de la elegida) ya no cabría sin partir una palabra o sin pasar de dos líneas.
     */
    private fun assertNotReducedTooMuch(label: String, layout: TextLayoutResult) {
        val base = InteriorTypeVoice.label
        val applied = layout.layoutInput.style.fontSize
        if (applied.value >= base.fontSize.value) return
        val bigger = base.scaledBy((applied.value / base.fontSize.value * TOLERANCE).coerceAtMost(1f))
        val width = layout.layoutInput.constraints.maxWidth
        val words = label.split(" ").filter { it.isNotEmpty() }
        val wordTooWide = words.any { measurer.measure(it, bigger, maxLines = 1, softWrap = false).size.width > width }
        val wrapped = measurer.measure(label, bigger, constraints = Constraints(maxWidth = width))
        val tooManyLines = wrapped.lineCount > 2
        val breaksWord = !breaksOnlyAtSpaces(label, (0 until wrapped.lineCount).map(wrapped::getLineEnd))
        assertTrue(
            "$case, «$label»: reducida a $applied, pero con un 10 % más de letra también cabría",
            wordTooWide || tooManyLines || breaksWord
        )
    }

    /** La maquetación del texto de la etiqueta, tal como se dibujó. */
    private fun layoutOf(label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull() ?: throw AssertionError("$case, «$label»: sin maquetación")
    }

    companion object {
        // El ancho útil de un teléfono de 360 dp: la pantalla menos sus márgenes de 24 dp.
        private const val PHONE_WIDTH_DP = 312

        @JvmStatic
        // Anchos útiles de una pantalla pequeña (320 dp), de un teléfono (360 dp) y de una
        // pantalla ancha (600 dp): la pantalla menos sus márgenes de 24 dp.
        @Parameterized.Parameters(name = "ancho {0}, letra {1}")
        fun cases(): List<Array<Any>> = listOf(272, PHONE_WIDTH_DP, 552).flatMap { width ->
            listOf(arrayOf<Any>(width, 1f), arrayOf<Any>(width, 2f))
        }

        // Margen de «solo lo necesario»: el paso de reducción es de un 4 %.
        private const val TOLERANCE = 1.1f
    }
}

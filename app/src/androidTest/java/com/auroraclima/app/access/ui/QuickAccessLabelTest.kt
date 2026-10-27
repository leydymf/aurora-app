package com.auroraclima.app.access.ui

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Las etiquetas de Acciones rápidas se ven enteras y con la letra que eligió la persona
 * (v0.3.3). Con letra grande «Informarme» se veía «Infor / ma…»; después la letra se
 * reducía hasta caber en la fila, y la autora pidió conservarla (WCAG 1.4.4): si no
 * caben en fila, las tres tarjetas se apilan.
 *
 * En cada ancho y escala, cada etiqueta se ve entera, sin puntos suspensivos, cortada
 * solo entre palabras y con la letra elegida. Las tarjetas van en fila con letra normal
 * desde un teléfono de 360 dp, apiladas con letra 2.0 en un teléfono y apiladas con
 * cualquier letra en una pantalla de 320 dp (decisión de la autora); y solo se apilan
 * cuando alguna etiqueta no cabría en la fila sin reducirla (medido aquí por separado,
 * no con la función de la pantalla).
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
    private lateinit var density: Density

    @Test
    fun cadaEtiquetaSeVeEnteraYConLaLetraElegida() {
        show()
        val stacked = isStacked(labels.map(::cardBounds))

        labels.forEach { label ->
            val layout = layoutOf(label)
            if (!stacked) {
                assertTrue("$case, «$label»: más de dos líneas en la fila (${layout.lineCount})", layout.lineCount <= 2)
            }
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
            assertEquals(
                "$case, «$label»: la letra se redujo",
                InteriorTypeVoice.label.fontSize,
                layout.layoutInput.style.fontSize
            )
        }
    }

    @Test
    fun seApilanSoloCuandoNoCabenEnFilaConLaLetraElegida() {
        show()
        val cards = labels.map(::cardBounds)
        val stacked = isStacked(cards)

        if (fontScale == 1f && widthDp >= PHONE_WIDTH_DP) assertFalse("$case: con letra normal van en fila", stacked)
        if (fontScale == 2f && widthDp <= PHONE_WIDTH_DP) assertTrue("$case: con letra 2.0 se apilan", stacked)
        // Decisión de la autora: también con letra normal, porque «Informarme» no cabe en
        // su tarjeta sin reducirla. Es un dato medido (depende de cómo se dibuja la letra).
        if (widthDp < PHONE_WIDTH_DP) assertTrue("$case: en una pantalla de 320 dp se apilan", stacked)

        if (stacked) {
            val widthPx = with(density) { widthDp.dp.toPx() }
            cards.forEachIndexed { index, card ->
                assertEquals("$case, «${labels[index]}»: apilada, va a lo ancho", widthPx, card.width, 1f)
            }
            cards.zipWithNext().forEach { (upper, lower) ->
                assertTrue("$case: apiladas, en el orden de la fila", upper.bottom <= lower.top)
            }
            assertTrue(
                "$case: se apilaron, pero las tres cabían en la fila con la letra elegida",
                labels.any { !fitsInRowAtChosenSize(it) }
            )
        } else {
            cards.zipWithNext().forEach { (start, end) ->
                assertEquals("$case: en fila, a la misma altura", start.top, end.top, 1f)
                assertTrue("$case: en fila, en orden", start.right <= end.left)
            }
        }
    }

    private fun show() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                measurer = rememberTextMeasurer()
                density = LocalDensity.current
                AuroraV2Theme(V2Voice.Interior) {
                    QuickAccessCards(
                        onCall = {},
                        onSaveNote = {},
                        onLearn = {},
                        modifier = Modifier.requiredWidth(widthDp.dp)
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Apiladas: una debajo de otra. En fila comparten la altura de su borde superior. */
    private fun isStacked(cards: List<Rect>): Boolean = cards.zipWithNext().all { (a, b) -> a.bottom <= b.top }

    /** La tarjeta que contiene la etiqueta: el nodo tocable que fusiona su texto. */
    private fun cardBounds(label: String): Rect =
        composeRule.onNode(hasText(label) and hasClickAction()).fetchSemanticsNode().boundsInRoot

    /**
     * Si [label] cabría en una tarjeta de la fila con la letra elegida: cada palabra
     * entera, dos líneas como mucho y los cortes entre palabras.
     */
    private fun fitsInRowAtChosenSize(label: String): Boolean {
        val width = with(density) {
            quickAccessLabelWidthPx(
                rowWidthPx = widthDp.dp.roundToPx(),
                gapPx = QUICK_ACCESS_GAP.roundToPx(),
                sidePaddingPx = QUICK_ACCESS_SIDE_PADDING.roundToPx(),
                count = labels.size
            )
        }
        val style = InteriorTypeVoice.label
        val words = label.split(" ").filter { it.isNotEmpty() }
        if (words.any { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width > width }) return false
        val wrapped = measurer.measure(label, style, constraints = Constraints(maxWidth = width))
        return wrapped.lineCount <= 2 && breaksOnlyAtSpaces(label, (0 until wrapped.lineCount).map(wrapped::getLineEnd))
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
    }
}

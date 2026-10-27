package com.auroraclima.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Las etiquetas de la barra de pestañas (`TabLabel`, v0.3.3) se ven enteras en una
 * línea: con letra grande en una pantalla angosta se reducen para caber, y esta prueba
 * comprueba que la reducción alcanza, porque el ancho del texto no escala en proporción
 * a la letra y la etiqueta no se parte ni pone puntos suspensivos (recorta). Se compara
 * el ancho de la línea dibujada con el del texto en pantalla.
 */
@RunWith(Parameterized::class)
class InnerTabBarLabelTest(private val widthDp: Int, private val fontScale: Float) {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val case get() = "ancho $widthDp dp, letra $fontScale"

    @Test
    fun lasEtiquetasDePestanaSeVenEnterasEnUnaLinea() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                AuroraV2Theme(V2Voice.Interior) {
                    Box(modifier = Modifier.requiredWidth(widthDp.dp)) {
                        InnerTabsLayout(selectedTab = HOME_TAB, onSelectTab = {}) {}
                    }
                }
            }
        }
        composeRule.waitForIdle()

        listOf(R.string.access_tab_home, R.string.access_tab_guide, R.string.access_tab_journal)
            .map { context.getString(it) }
            .forEach { label ->
                val node = composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
                val layout = layoutOf(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action, label)
                assertEquals("$case, «$label»: más de una línea", 1, layout.lineCount)
                assertEquals("$case, «$label»: no se ve entera", label.length, layout.getLineEnd(0))
                val lineWidth = layout.getLineRight(0) - layout.getLineLeft(0)
                assertTrue(
                    "$case, «$label»: la línea mide $lineWidth px y el texto en pantalla " +
                        "${node.size.width} px (recortada)",
                    lineWidth <= node.size.width + 1
                )
            }
    }

    private fun layoutOf(action: ((MutableList<TextLayoutResult>) -> Boolean)?, label: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        action?.invoke(results)
        return results.firstOrNull() ?: throw AssertionError("$case, «$label»: sin maquetación")
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "ancho {0}, letra {1}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf<Any>(320, 1f),
            arrayOf<Any>(320, 2f),
            arrayOf<Any>(411, 2f)
        )
    }
}

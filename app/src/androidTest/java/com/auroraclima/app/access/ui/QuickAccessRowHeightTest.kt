package com.auroraclima.app.access.ui

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * En fila, las tres tarjetas de «Acciones rápidas» (P09) deben medir SIEMPRE lo
 * mismo, en cualquier ancho y con cualquier escala de fuente del sistema (ajuste
 * pedido en la corrida manual §8-A3): «Guardar una nota» ocupa dos líneas
 * donde las otras dos ocupan una, y antes eso dejaba la fila despareja según
 * el dispositivo. La altura la fija la tarjeta más alta de la fila. Cuando no
 * caben en fila con la letra elegida se apilan (v0.3.3, `QuickAccessLabelTest`):
 * por eso el caso de letra grande va en una pantalla ancha, donde siguen en fila.
 */
@RunWith(AndroidJUnit4::class)
class QuickAccessRowHeightTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun lasTresTarjetasMidenLoMismoEnPantallaAngosta() {
        assertEqualHeights(widthDp = 312, fontScale = 1f)
    }

    @Test
    fun lasTresTarjetasMidenLoMismoEnPantallaAncha() {
        assertEqualHeights(widthDp = 552, fontScale = 1f)
    }

    @Test
    fun lasTresTarjetasMidenLoMismoConFuenteGrandeEnPantallaAncha() {
        assertEqualHeights(widthDp = 552, fontScale = 1.5f)
    }

    /**
     * Renderiza la fila con el ancho útil indicado (el de la pantalla menos sus
     * márgenes de 24 dp a cada lado) y compara las alturas de las tres
     * tarjetas, que son los tres únicos nodos pulsables de la fila.
     *
     * `requiredWidth` y no `width`: este último se deja recortar por el ancho
     * real del dispositivo de prueba, con lo que el caso ancho se mediría igual
     * que el angosto y no probaría nada (revisión 2026-10-06 M-1).
     */
    private fun assertEqualHeights(widthDp: Int, fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale)
            ) {
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

        val cards = composeRule.onAllNodes(hasClickAction())
        // Cardinalidad primero: si la fila cambiara, el fallo debe ser legible
        // y no un índice fuera de rango (auditoría 2026-10-06 H-4).
        assertEquals("La fila tiene exactamente tres tarjetas", 3, cards.fetchSemanticsNodes().size)
        val tops = (0 until 3).map { index -> cards[index].fetchSemanticsNode().boundsInRoot.top }
        // Precondición: van en fila. Apiladas, la prueba no diría nada de la fila.
        assertEquals("Las tres tarjetas van en fila", 1, tops.map { Math.round(it) }.toSet().size)
        val heights = (0 until 3).map { index ->
            cards[index].fetchSemanticsNode().size.height
        }
        assertTrue("La fila debe tener altura real", heights.all { it > 0 })
        assertEquals("Las tres tarjetas deben medir lo mismo", setOf(heights.first()), heights.toSet())
    }
}

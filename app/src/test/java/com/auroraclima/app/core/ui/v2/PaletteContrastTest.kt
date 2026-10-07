package com.auroraclima.app.core.ui.v2

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verificador WCAG 2.1 de los pares acento del V2: todo [AccentPair] debe
 * cumplir AA de texto normal (≥ 4.5:1) entre trazo y tinte, porque el trazo se
 * usa como color de texto sobre el tinte (regla madre del anexo v2-29). Nace
 * con la escala de severidad del resultado (decisión directora 2026-08-14,
 * reversión de P-41) y de paso fija los siete acentos preexistentes, cuyos
 * ratios estaban solo documentados en `docs/mockups-v2/README.md` §3.6.
 *
 * Desde 2026-10-05 fija además los pares de las superficies: el contorno de los
 * controles contra tarjeta y fondo (≥ 3:1, WCAG §1.4.11) y las dos tintas de
 * texto contra tarjeta y fondo (≥ 4.5:1), tras el hallazgo de los campos
 * invisibles sobre el fondo lila (relleno y borde en `divider`, 1.03:1).
 */
class PaletteContrastTest {

    private val surfaces = mapOf(
        "cardSurface" to V2Palette.cardSurface,
        "screenBackground" to V2Palette.screenBackground
    )

    private val accentPairs = mapOf(
        "accentRecognize" to V2Palette.accentRecognize,
        "accentCloud" to V2Palette.accentCloud,
        "accentProtection" to V2Palette.accentProtection,
        "accentCare" to V2Palette.accentCare,
        "accentNotice" to V2Palette.accentNotice,
        "accentJustice" to V2Palette.accentJustice,
        "accentGroup" to V2Palette.accentGroup,
        "accentSeverity1" to V2Palette.accentSeverity1,
        "accentSeverity2" to V2Palette.accentSeverity2,
        "accentSeverity3" to V2Palette.accentSeverity3,
        "accentSeverity4" to V2Palette.accentSeverity4
    )

    @Test
    fun `todo par acento cumple AA entre trazo y tinte`() {
        accentPairs.forEach { (name, pair) ->
            val ratio = contrastRatio(pair.stroke, pair.tint)
            assertTrue(
                "$name: trazo sobre tinte da ${"%.2f".format(ratio)}:1, se exige >= 4.5:1",
                ratio >= 4.5
            )
        }
    }

    @Test
    fun `la escala de severidad tambien cumple AA sobre tarjeta y fondo`() {
        // El trazo de severidad rotula la tarjeta del nivel y sus reflejos en
        // listados sobre superficies blancas o el fondo lila de pantalla.
        listOf(
            V2Palette.accentSeverity1,
            V2Palette.accentSeverity2,
            V2Palette.accentSeverity3,
            V2Palette.accentSeverity4
        ).forEachIndexed { index, pair ->
            listOf(V2Palette.cardSurface, V2Palette.screenBackground).forEach { surface ->
                val ratio = contrastRatio(pair.stroke, surface)
                assertTrue(
                    "accentSeverity${index + 1} sobre superficie da ${"%.2f".format(ratio)}:1",
                    ratio >= 4.5
                )
            }
        }
    }

    @Test
    fun `el contorno de los controles se distingue sobre tarjeta y sobre fondo`() {
        // §1.4.11: el borde de un campo sin foco o de un chip sin elegir es la única
        // pista de que ahí hay un control; debe dar ≥ 3:1 contra lo que lo rodea.
        surfaces.forEach { (name, surface) ->
            val ratio = contrastRatio(V2Palette.controlOutline, surface)
            assertTrue(
                "controlOutline sobre $name da ${"%.2f".format(ratio)}:1, se exige >= 3:1",
                ratio >= 3.0
            )
        }
    }

    @Test
    fun `el borde con foco de los campos se distingue sobre tarjeta y sobre fondo`() {
        surfaces.forEach { (name, surface) ->
            val ratio = contrastRatio(V2Palette.inkPrimary, surface)
            assertTrue(
                "inkPrimary (borde con foco) sobre $name da ${"%.2f".format(ratio)}:1, se exige >= 3:1",
                ratio >= 3.0
            )
        }
    }

    @Test
    fun `las tintas de texto cumplen AA sobre tarjeta y sobre fondo`() {
        // inkSecondary también sirve de borde a casillas, interruptor y botón
        // secundario; AA de texto normal (4.5:1) cubre de sobra el 3:1 no textual.
        mapOf("inkPrimary" to V2Palette.inkPrimary, "inkSecondary" to V2Palette.inkSecondary)
            .forEach { (ink, color) ->
                surfaces.forEach { (name, surface) ->
                    val ratio = contrastRatio(color, surface)
                    assertTrue(
                        "$ink sobre $name da ${"%.2f".format(ratio)}:1, se exige >= 4.5:1",
                        ratio >= 4.5
                    )
                }
            }
    }

    @Test
    fun `el separador nunca alcanza para delimitar un control`() {
        // Guarda del hallazgo: si algún día `divider` se oscurece hasta pasar 3:1,
        // este test avisa para revisar si el KDoc («NUNCA como borde») sigue vigente.
        surfaces.forEach { (_, surface) ->
            assertTrue(contrastRatio(V2Palette.divider, surface) < 3.0)
        }
    }

    @Test
    fun `la escala de severidad no entra a la biblioteca seleccionable del contenido`() {
        // Misma fijación estructural que la exclusión del violeta de la nube
        // (ContentVisualTest): la escala existe solo para el resultado y ningún
        // documento puede pedirla, con ningún nombre.
        listOf(
            V2Palette.accentSeverity1,
            V2Palette.accentSeverity2,
            V2Palette.accentSeverity3,
            V2Palette.accentSeverity4
        ).forEach { pair ->
            assertFalse(selectableAccents.containsValue(pair))
        }
    }

    /** Ratio de contraste WCAG 2.1 (§ contrast ratio): (Lclaro + 0.05) / (Loscuro + 0.05). */
    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Luminancia relativa WCAG 2.1 sobre sRGB linealizado. */
    private fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}

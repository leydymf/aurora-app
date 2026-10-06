package com.auroraclima.app.questionnaire.ui

import androidx.compose.ui.text.AnnotatedString
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Énfasis de los nombres de tipos (resultado, tarjeta y detalle). Lo que se
 * fija aquí es que el TEXTO no cambia y que la negrilla cubre exactamente los
 * nombres, nunca los separadores ni el prefijo del documento.
 */
class RecognitionTextTest {
    private val prefix = "Por tus respuestas, aparecen señales de"
    private val empty = "No aparecen señales claras en tus respuestas."

    /** Tramos en negrilla de una cadena anotada, en orden. */
    private fun bold(text: AnnotatedString): List<String> = text.spanStyles
        .filter { it.item.fontWeight == InteriorTypeVoice.emphasisWeight }
        .map { text.text.substring(it.start, it.end) }

    @Test
    fun `la frase del resultado conserva el texto plano de siempre`() {
        val line = RecognitionLine(
            prefix = prefix,
            labels = listOf("violencia psicológica", "violencia física"),
            empty = empty
        )
        assertEquals(
            "Por tus respuestas, aparecen señales de violencia psicológica y violencia física.",
            emphasizedRecognition(line).text
        )
        assertEquals(line.plain, emphasizedRecognition(line).text)
    }

    @Test
    fun `solo los nombres de los tipos van en negrilla`() {
        val labels = listOf("violencia psicológica", "violencia física", "violencia económica")
        val line = RecognitionLine(prefix, labels, empty)
        val annotated = emphasizedRecognition(line)
        assertEquals(labels, bold(annotated))
        // El prefijo, las comas, la conjunción y el punto quedan en peso normal.
        assertTrue(bold(annotated).none { it.contains(",") || it.contains(" y ") || it.contains(".") })
        assertTrue(bold(annotated).none { it.contains("Por tus respuestas") })
    }

    @Test
    fun `un solo tipo va en negrilla sin separadores`() {
        val labels = listOf("acoso sexual")
        val line = RecognitionLine(prefix, labels, empty)
        assertEquals("Por tus respuestas, aparecen señales de acoso sexual.", emphasizedRecognition(line).text)
        assertEquals(labels, bold(emphasizedRecognition(line)))
    }

    @Test
    fun `sin dimensiones encendidas se muestra el texto de ausencia sin negrilla`() {
        val line = RecognitionLine(prefix, emptyList(), empty)
        val annotated = emphasizedRecognition(line)
        assertEquals(empty, annotated.text)
        assertTrue(bold(annotated).isEmpty())
    }

    @Test
    fun `la tarjeta del listado resalta las etiquetas y deja el separador normal`() {
        val tags = listOf("violencia psicológica", "violencia digital")
        val annotated = emphasizedLabels(tags, CARD_LABEL_SEPARATOR)
        assertEquals("violencia psicológica · violencia digital", annotated.text)
        assertEquals(tags, bold(annotated))
    }

    @Test
    fun `el detalle guardado mantiene el rotulo y el punto fuera del enfasis`() {
        val tags = listOf("violencia física", "acoso sexual")
        val annotated = emphasizedLabelList("Señales reconocidas:", tags)
        assertEquals("Señales reconocidas: violencia física, acoso sexual.", annotated.text)
        assertEquals(tags, bold(annotated))
    }

    @Test
    fun `sin etiquetas el detalle no deja el rotulo suelto`() {
        assertEquals("", emphasizedLabelList("Señales reconocidas:", emptyList()).text)
        assertEquals("", emphasizedLabels(emptyList(), CARD_LABEL_SEPARATOR).text)
    }

    @Test
    fun `la linea no imprime los nombres de los tipos al stringificarse`() {
        val line = RecognitionLine(prefix, listOf("violencia física"), empty)
        assertEquals("RecognitionLine(…)", line.toString())
        assertFalse(line.toString().contains("violencia"))
        assertFalse(line.toString().contains("labels"))
    }

    @Test
    fun `el texto plano une con coma y conjuncion final`() {
        assertEquals("", joinWithConjunction(emptyList()))
        assertEquals("a", joinWithConjunction(listOf("a")))
        assertEquals("a y b", joinWithConjunction(listOf("a", "b")))
        assertEquals("a, b y c", joinWithConjunction(listOf("a", "b", "c")))
    }
}

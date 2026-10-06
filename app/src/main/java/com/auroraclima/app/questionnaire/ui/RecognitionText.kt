package com.auroraclima.app.questionnaire.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice

/**
 * Línea de tipos reconocidos (§6.1 del documento del cuestionario) y su
 * presentación con los nombres resaltados.
 *
 * El TEXTO no cambia: el prefijo y los nombres de los tipos son literales del
 * documento publicado (viven en Firestore, nunca en el APK — RNF-05) y lo que
 * RF-31 persiste sigue siendo fecha, nivel y etiquetas. Lo único que agrega
 * este archivo es el énfasis tipográfico, en funciones puras para que el mismo
 * criterio sirva a los tres lugares donde aparece la lista — resultado,
 * tarjeta del listado y detalle guardado — y se pueda probar en JVM.
 */

/** Separador entre etiquetas; el último par va con conjunción («a, b y c»). */
private const val LABEL_SEPARATOR = ", "
private const val LABEL_LAST_SEPARATOR = " y "

/** Separador sobrio de la tarjeta del listado, que no es una frase. */
internal const val CARD_LABEL_SEPARATOR = " · "

/** Cierre de la frase de reconocimiento, fuera del énfasis. */
private const val SENTENCE_END = "."

/**
 * Línea de reconocimiento lista para pintar. [labels] vacío significa que no se
 * encendió ninguna dimensión: entonces la línea es el texto de ausencia del
 * documento ([empty]) y no hay nada que resaltar.
 */
data class RecognitionLine(
    val prefix: String,
    val labels: List<String>,
    val empty: String
) {
    /**
     * Texto plano de la misma línea. Se DERIVA de la versión resaltada, no se
     * guarda aparte: así es imposible que el texto visible y el plano discrepen.
     */
    val plain: String
        get() = emphasizedRecognition(this).text

    /**
     * Nunca imprime sus campos: [labels] trae los nombres reales de los tipos y
     * la plantilla del `toString` generado sobrevive a R8 (P-78, mismo criterio
     * que `StoredCredentials`).
     */
    override fun toString(): String = "RecognitionLine(…)"
}

/** Une etiquetas en español: «a», «a y b», «a, b y c». */
internal fun joinWithConjunction(labels: List<String>): String = emphasizedConjunction(labels).text

/**
 * Etiquetas en negrilla, con los separadores en peso normal: el contraste es lo
 * que hace resaltar los nombres (si todo fuera negrilla no resaltaría nada).
 */
internal fun emphasizedLabels(
    labels: List<String>,
    separator: String,
    lastSeparator: String = separator
): AnnotatedString = buildAnnotatedString {
    labels.forEachIndexed { index, label ->
        if (index > 0) {
            append(if (index == labels.lastIndex) lastSeparator else separator)
        }
        withStyle(SpanStyle(fontWeight = InteriorTypeVoice.emphasisWeight)) { append(label) }
    }
}

/** Etiquetas en negrilla unidas como frase («a, b y c»), para el reconocimiento. */
internal fun emphasizedConjunction(labels: List<String>): AnnotatedString =
    emphasizedLabels(labels, LABEL_SEPARATOR, LABEL_LAST_SEPARATOR)

/**
 * Frase completa del resultado: prefijo del documento en peso normal, nombres
 * de los tipos en negrilla y el punto final fuera del énfasis. Sin dimensiones
 * encendidas devuelve el texto de ausencia tal cual.
 */
internal fun emphasizedRecognition(line: RecognitionLine): AnnotatedString {
    if (line.labels.isEmpty()) return AnnotatedString(line.empty)
    return buildAnnotatedString {
        append(line.prefix)
        append(" ")
        append(emphasizedConjunction(line.labels))
        append(SENTENCE_END)
    }
}

/**
 * Lista de etiquetas precedida por un rótulo fijo del APK («Señales
 * reconocidas:»), con los nombres en negrilla y el punto final neutro. El
 * rótulo y la lista viajan separados para no tener que hurgar en un marcador
 * de formato.
 */
internal fun emphasizedLabelList(
    lead: String,
    labels: List<String>,
    separator: String = LABEL_SEPARATOR
): AnnotatedString {
    // Sin etiquetas no hay lista que rotular: el rótulo solo con un punto sería
    // ruido. Los dos llamadores ya lo evitan; el contrato queda defendido aquí.
    if (labels.isEmpty()) return AnnotatedString("")
    return buildAnnotatedString {
        append(lead)
        append(" ")
        append(emphasizedLabels(labels, separator))
        append(SENTENCE_END)
    }
}

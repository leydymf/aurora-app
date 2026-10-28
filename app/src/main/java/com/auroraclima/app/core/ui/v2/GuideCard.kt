package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Piezas de la guía de Aurora (v0.3.4) con su estilo propio ([GuidePalette],
 * [GuideTypeVoice], [GuideCorner]): la tarjeta de cada paso, el botón lleno de la guía
 * —el único lleno en pantalla mientras está abierta—, la pantalla completa y el
 * apagado de la app que queda debajo.
 */

/** Una acción de la tarjeta: su texto y qué hace. */
class GuideAction(val label: String, val onClick: () -> Unit)

/**
 * Tarjeta de un paso: etiqueta de parte arriba con el enlace [skip] («Saltar guía») en
 * la esquina final; título, frase y [extra]; y la fila de botones: [back] y
 * [secondary] como texto, [primary] lleno. Etiqueta, título y frase son un solo nodo
 * con región viva: TalkBack los lee juntos en cada paso. Con letra muy grande los
 * botones se apilan y, si la tarjeta tiene un alto máximo, el título y la frase se
 * desplazan dentro de ella mientras los botones quedan fijos al pie ([scrollable]; en
 * falso cuando la tarjeta va dentro de algo que ya se desplaza). [actionsEndInset] acorta la fila de botones por el lado final para que
 * ninguno quede bajo la nube.
 */
@Composable
fun GuideCard(
    tag: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primary: GuideAction? = null,
    back: GuideAction? = null,
    secondary: GuideAction? = null,
    skip: GuideAction? = null,
    actionsEndInset: Dp = 0.dp,
    scrollable: Boolean = true,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = GuideCorner.card)
            .clip(GuideCorner.card)
            .background(GuidePalette.surface)
            .padding(CARD_PADDING)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tag,
                style = GuideTypeVoice.partLabel,
                color = GuidePalette.label,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            if (skip != null) GuideLink(action = skip)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Con un alto máximo, lo que se desplaza es el texto: los botones quedan
        // siempre a la vista al pie de la tarjeta.
        Column(
            modifier = if (scrollable) {
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            ) {
                Text(
                    text = title,
                    style = GuideTypeVoice.cardTitle,
                    color = GuidePalette.ink,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = body, style = GuideTypeVoice.body, color = GuidePalette.inkSoft)
            }
            extra()
        }
        if (primary != null || back != null || secondary != null) {
            Spacer(modifier = Modifier.height(16.dp))
            GuideActions(
                primary = primary,
                back = back,
                secondary = secondary,
                modifier = Modifier.padding(end = actionsEndInset)
            )
        }
    }
}

/** La fila de botones; con letra muy grande, apilados (el lleno primero). */
@Composable
private fun GuideActions(
    primary: GuideAction?,
    back: GuideAction?,
    secondary: GuideAction?,
    modifier: Modifier = Modifier
) {
    if (LocalDensity.current.fontScale >= STACKED_FONT_SCALE) {
        Column(modifier = modifier.fillMaxWidth()) {
            primary?.let {
                GuidePrimaryButton(
                    text = it.label,
                    onClick = it.onClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            secondary?.let { GuideTextButton(action = it, modifier = Modifier.fillMaxWidth()) }
            back?.let { GuideTextButton(action = it, modifier = Modifier.fillMaxWidth()) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            back?.let { GuideTextButton(action = it) }
            Spacer(modifier = Modifier.weight(1f))
            secondary?.let { GuideTextButton(action = it) }
            primary?.let {
                GuidePrimaryButton(
                    text = it.label,
                    onClick = it.onClick,
                    modifier = Modifier.widthIn(min = PRIMARY_MIN_WIDTH)
                )
            }
        }
    }
}

/**
 * El botón lleno de la guía, en su lavanda: el único lleno en pantalla mientras está
 * abierta. Su ancho lo decide quien lo pone (a lo ancho en una pantalla, a su medida
 * en la fila de una tarjeta).
 */
@Composable
fun GuidePrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = BUTTON_HEIGHT),
        shape = GuideCorner.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = GuidePalette.action,
            contentColor = GuidePalette.onAction
        )
    ) {
        Text(text = text, style = GuideTypeVoice.action)
    }
}

/** Botón de texto de la guía («Atrás», «Después», «Ahora no»). */
@Composable
fun GuideTextButton(action: GuideAction, modifier: Modifier = Modifier) {
    TextButton(onClick = action.onClick, modifier = modifier.heightIn(min = MIN_TOUCH)) {
        Text(text = action.label, style = GuideTypeVoice.action, color = GuidePalette.ink)
    }
}

/** El enlace pequeño de la esquina («Saltar guía»): subrayado, con su área de 48 dp. */
@Composable
private fun GuideLink(action: GuideAction) {
    TextButton(onClick = action.onClick, modifier = Modifier.heightIn(min = MIN_TOUCH)) {
        Text(
            text = action.label,
            style = InteriorTypeVoice.caption,
            color = GuidePalette.label,
            textDecoration = TextDecoration.Underline
        )
    }
}

/**
 * Pantalla completa de la guía: el fondo lavanda tapa la app y se traga sus toques,
 * así que solo responden los controles de la guía y la nube, que va por encima.
 */
@Composable
fun GuideSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GuidePalette.surface)
            .swallowTouches(),
        content = content
    )
}

/** Se traga cada gesto entero: ni toques ni desplazamientos llegan a lo de abajo. */
fun Modifier.swallowTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Apaga lo que dibuja: sin color (saturación cero) y un poco lavado, para que ningún
 * botón de la app parezca una acción mientras la guía está abierta. Solo cambia cómo
 * se ve; que no responda lo garantiza quien lo cubre ([swallowTouches] o el velo).
 * Funciona en todas las versiones (capa con filtro de color, sin `RenderEffect`).
 */
fun Modifier.muted(enabled: Boolean): Modifier = if (!enabled) {
    this
} else {
    drawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), MutedPaint)
            drawContent()
            canvas.restore()
        }
    }
}

/** Saturación cero y un lavado hacia el blanco del 25 %. */
private val MutedPaint = Paint().apply {
    colorFilter = ColorFilter.colorMatrix(
        ColorMatrix().apply { setToSaturation(0f) }.also { matrix ->
            val wash = 0.25f
            val keep = 1f - wash
            val lighten = ColorMatrix(
                floatArrayOf(
                    keep, 0f, 0f, 0f, 255f * wash,
                    0f, keep, 0f, 0f, 255f * wash,
                    0f, 0f, keep, 0f, 255f * wash,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix.timesAssign(lighten)
        }
    )
}

private val CARD_PADDING = 20.dp
private val BUTTON_HEIGHT = 52.dp
private val MIN_TOUCH = 48.dp
private val PRIMARY_MIN_WIDTH = 120.dp
private const val STACKED_FONT_SCALE = 1.5f

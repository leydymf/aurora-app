package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
 * —el único lleno en pantalla mientras está abierta—, el fondo del paso 1 y el
 * apagado de la app que queda debajo.
 */

/** Una acción de la tarjeta: su texto y qué hace. */
class GuideAction(val label: String, val onClick: () -> Unit)

/**
 * Tarjeta de un paso: la etiqueta de parte con el enlace [skip] («Saltar») en la
 * esquina final, el título, la frase (y, si hay, una [note] más pequeña debajo) y la fila de botones: [back] y [secondary] como
 * texto, [primary] lleno. Título y frase son un nodo con región viva: TalkBack los lee
 * juntos en cada paso. Con letra grande los botones pasan a otra línea solo si no caben.
 * Si la tarjeta tiene un alto máximo, todo menos los botones se desplaza dentro de ella
 * y los botones quedan fijos al pie ([scrollable]; en falso cuando la tarjeta va dentro
 * de algo que ya se desplaza). [actionsEndInset] acorta la fila de botones por el lado
 * final para que ninguno quede bajo la nube.
 */
@Composable
fun GuideCard(
    tag: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    primary: GuideAction? = null,
    back: GuideAction? = null,
    secondary: GuideAction? = null,
    skip: GuideAction? = null,
    actionsEndInset: Dp = 0.dp,
    scrollable: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 6.dp, shape = GuideCorner.card)
            .clip(GuideCorner.card)
            .background(GuidePalette.surface)
            .padding(CARD_PADDING)
    ) {
        // Con un alto máximo se desplaza todo menos los botones: quedan siempre a la
        // vista al pie, también con letra grande junto a un elemento alto.
        Column(
            modifier = if (scrollable) {
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier
            }
        ) {
            GuideCardHeader(tag = tag, skip = skip)
            Spacer(modifier = Modifier.height(4.dp))
            GuideCardText(title = title, body = body, note = note)
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

/** La etiqueta de parte y, en la esquina final, el enlace «Saltar». */
@Composable
private fun GuideCardHeader(tag: String, skip: GuideAction?) {
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
}

/** Título, frase y nota opcional: un solo nodo con región viva, que TalkBack lee junto. */
@Composable
private fun GuideCardText(title: String, body: String, note: String?) {
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
        if (note != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = note, style = GuideTypeVoice.note, color = GuidePalette.inkSoft)
        }
    }
}

/**
 * La fila de botones: «Atrás» a un lado y el lleno (o «Después») al otro. Con letra
 * grande pasan a la línea siguiente solo si no caben juntos: apilarlos siempre gastaba
 * un alto que la tarjeta no tiene junto a un elemento alto (corrida con letra 2.0).
 * `FlowRow` sigue experimental en foundation 1.7.8 (BOM fijado); es la vía de la
 * plataforma para «en fila si cabe» y admite medidas intrínsecas, que usa la capa para
 * saber el alto entero de la tarjeta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideActions(
    primary: GuideAction?,
    back: GuideAction?,
    secondary: GuideAction?,
    modifier: Modifier = Modifier
) {
    val count = listOfNotNull(primary, back, secondary).size
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (count == 1) Arrangement.End else Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        back?.let { GuideTextButton(action = it) }
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

/** El enlace pequeño de la esquina («Saltar»): subrayado, con su área de 48 dp. */
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
 * Fondo lavanda del paso 1 (la práctica sobre la réplica del clima): tapa la app y se
 * traga sus toques, así que solo responden los controles de la guía y la nube, que va
 * por encima. «Conoce Aurora» y «¡Listo!» no lo usan: van con el fondo del interior.
 */
@Composable
fun GuideSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GuidePalette.surface)
            .blockTouchesBelow(),
        content = content
    )
}

/**
 * Ningún toque ni desplazamiento llega a lo que hay debajo: Compose entrega el gesto
 * solo al hermano de más arriba que tenga entrada de puntero en ese punto, así que
 * basta con estar aquí. No consume nada a propósito: consumir en la pasada principal
 * cancelaba el arrastre de lo que hay DENTRO (la pantalla de la guía no se desplazaba
 * con letra grande y «Empezar» quedaba fuera de alcance, corrida de v0.3.4).
 */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false) }
}

/**
 * Apaga lo que dibuja: sin color (saturación cero) y un poco lavado, para que ningún
 * botón de la app parezca una acción mientras la guía está abierta. Solo cambia cómo
 * se ve; que no responda lo garantiza quien lo cubre ([blockTouchesBelow] o el velo).
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

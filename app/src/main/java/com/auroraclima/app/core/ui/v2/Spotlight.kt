package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lo que una capa de recorrido puede iluminar (v0.3.3). Cada elemento marcado con
 * [spotlightTarget] publica aquí sus límites —en coordenadas de la ventana y ya
 * recortados por lo que lo contiene, así que lo que está fuera de la vista mide
 * cero— y cómo traerlo a la vista. Lo provee la capa con [LocalSpotlightTargets];
 * sin ella, marcar un elemento no hace nada.
 *
 * `BringIntoViewRequester` sigue marcado experimental en foundation 1.7.8 (la del BOM
 * fijado); es la vía de la plataforma para «desplaza hasta que esto se vea» y no se
 * reescribe a mano. Si cambiara al actualizar el BOM, el cambio queda en este archivo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class SpotlightTargets {
    private val bounds = mutableStateMapOf<Any, Rect>()
    private val requesters = mutableMapOf<Any, BringIntoViewRequester>()

    /** Límites del elemento [key] en la ventana; null si no está en composición. */
    fun boundsOf(key: Any): Rect? = bounds[key]

    /**
     * Desplaza lo que lo contenga hasta que [key] se vea entero (con letra grande
     * puede estar fuera de la pantalla). Vuelve al terminar; sin nada que desplazar,
     * vuelve enseguida.
     */
    suspend fun bringIntoView(key: Any) {
        requesters[key]?.bringIntoView()
    }

    internal fun attach(key: Any, requester: BringIntoViewRequester) {
        requesters[key] = requester
    }

    internal fun place(key: Any, rect: Rect) {
        bounds[key] = rect
    }

    internal fun detach(key: Any, requester: BringIntoViewRequester) {
        if (requesters[key] === requester) {
            requesters.remove(key)
            bounds.remove(key)
        }
    }
}

/** Los objetivos de la capa de recorrido en curso; null si no hay ninguna. */
val LocalSpotlightTargets = staticCompositionLocalOf<SpotlightTargets?> { null }

/**
 * Marca este elemento como el objetivo [key] de la capa de recorrido. Fuera de una
 * capa (vistas previas, pruebas de la pantalla sola) no agrega nada.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.spotlightTarget(key: Any): Modifier = composed {
    val targets = LocalSpotlightTargets.current ?: return@composed Modifier
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(targets, key, requester) {
        targets.attach(key, requester)
        onDispose { targets.detach(key, requester) }
    }
    Modifier
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { targets.place(key, it.boundsInWindow()) }
}

/**
 * Dónde va todo lo que dibuja la capa (v0.3.3). Puro, para probarlo en la JVM: el
 * globo va debajo del elemento si cabe; si no, encima; y si no cabe en ninguno de los
 * dos lados (letra muy grande en una pantalla baja), pegado al borde del lado con más
 * espacio, aunque cubra parte del elemento. Todo en las coordenadas de la capa.
 */
object SpotlightPlacement {
    /**
     * [target] y el resultado en coordenadas de la capa; [top]..[bottom] es la franja
     * usable (sin barras del sistema) y [gap] el aire entre elemento y globo.
     */
    fun place(target: Rect, bubbleHeight: Float, top: Float, bottom: Float, gap: Float): BubbleSpot {
        val roomBelow = bottom - (target.bottom + gap)
        val roomAbove = (target.top - gap) - top
        return when {
            bubbleHeight <= roomBelow -> BubbleSpot(top = target.bottom + gap, below = true)
            bubbleHeight <= roomAbove -> BubbleSpot(top = target.top - gap - bubbleHeight, below = false)
            roomBelow >= roomAbove -> BubbleSpot(top = (bottom - bubbleHeight).coerceAtLeast(top), below = true)
            else -> BubbleSpot(top = top, below = false)
        }
    }

    /**
     * Cuánto acortar por el lado final la fila de botones del globo [bubble] para que
     * [keepClear] (la nube) no quede encima de ninguno: cero si no se cruzan. La nube
     * va por encima de la capa y un toque en ella sale al clima; un botón debajo de
     * ella sería una salida por accidente. [contentPadding] es el margen interior del
     * globo, que ya separa la fila de su borde. Con [rightToLeft] el lado final es el
     * izquierdo, y allí está también la nube.
     */
    fun actionsEndInset(
        bubble: Rect,
        keepClear: Rect?,
        gap: Float,
        contentPadding: Float,
        rightToLeft: Boolean = false
    ): Float {
        if (keepClear == null) return 0f
        val crossesVertically = bubble.bottom > keepClear.top - gap && bubble.top < keepClear.bottom + gap
        if (!crossesVertically) return 0f
        val overlap = if (rightToLeft) {
            keepClear.right + gap - (bubble.left + contentPadding)
        } else {
            bubble.right - contentPadding - (keepClear.left - gap)
        }
        return overlap.coerceAtLeast(0f)
    }

    /**
     * El cuadro completo de un paso: el hueco alrededor de [target], el globo de alto
     * [bubbleHeight] centrado en [areaWidth], su pico y el margen de su fila de botones.
     * El globo no baja del borde inferior de [keepClear]: así la nube solo puede cruzar
     * su franja de botones, y esa franja se acorta (ver [actionsEndInset]).
     */
    fun frame(
        target: Rect,
        keepClear: Rect?,
        areaWidth: Float,
        top: Float,
        bottom: Float,
        bubbleHeight: Float,
        metrics: SpotlightMetrics,
        rightToLeft: Boolean = false
    ): SpotlightFrame {
        val hole = target.inflate(metrics.holePadding)
        val lowest = keepClear?.let { min(bottom, it.bottom) } ?: bottom
        val width = min(areaWidth - 2 * metrics.edgeMargin, metrics.maxBubbleWidth)
        val left = (areaWidth - width) / 2
        val spot = place(hole, bubbleHeight, top, lowest, metrics.gap)
        val bubble = Rect(left, spot.top, left + width, spot.top + bubbleHeight)
        val caretMin = left + metrics.caretInset
        val caretMax = left + width - metrics.caretInset
        return SpotlightFrame(
            hole = hole,
            bubble = bubble,
            maxBubbleHeight = (lowest - top).coerceAtLeast(0f),
            caretX = if (caretMin <= caretMax) hole.center.x.coerceIn(caretMin, caretMax) else bubble.center.x,
            caretPointsUp = spot.below,
            actionsEndInset = actionsEndInset(bubble, keepClear, metrics.gap, metrics.bubblePadding, rightToLeft)
        )
    }
}

/** El globo empieza en [top]; [below] dice si va debajo del elemento (su pico apunta hacia arriba). */
data class BubbleSpot(val top: Float, val below: Boolean)

/** Medidas de la capa en píxeles; las fija la capa con su densidad. */
data class SpotlightMetrics(
    val edgeMargin: Float,
    val maxBubbleWidth: Float,
    val gap: Float,
    val holePadding: Float,
    val caretInset: Float,
    val bubblePadding: Float
)

/** Lo que dibuja la capa en un paso, en sus coordenadas. */
data class SpotlightFrame(
    val hole: Rect,
    val bubble: Rect,
    val maxBubbleHeight: Float,
    val caretX: Float,
    val caretPointsUp: Boolean,
    val actionsEndInset: Float
)

/**
 * Capa de recorrido: un velo que oscurece toda su área menos [targetBounds] (en
 * coordenadas de la ventana, con un poco de aire) y, al lado que tenga sitio, el
 * globo [bubble] con su pico hacia el elemento. Sin límites todavía solo pinta el
 * velo. El velo se traga los toques: nada de lo que hay debajo se activa. Lo que
 * esté POR ENCIMA de esta capa (la nube del grafo seguro) sigue recibiendo el suyo.
 *
 * [keepClear] es un área que el globo respeta (la nube, en ventana): el globo no baja
 * de su borde inferior y, si la cruza, su fila de botones se acorta por el lado final
 * (lo recibe [bubble] como margen) para que ningún botón quede debajo.
 */
@Composable
fun SpotlightLayer(
    targetBounds: Rect?,
    modifier: Modifier = Modifier,
    keepClear: Rect? = null,
    bubble: @Composable (actionsEndInset: Dp) -> Unit
) {
    val window = rememberWindowFrame()
    var bubbleHeight by remember { mutableIntStateOf(0) }
    val frame = spotlightFrameOf(window, targetBounds, keepClear, bubbleHeight)
    Box(modifier = modifier.fillMaxSize().trackWindowFrame(window)) {
        // Sin el alto del globo, ni pico ni globo: no se muestran en un lugar falso.
        Scrim(hole = frame?.hole, caret = frame?.takeIf { bubbleHeight > 0 }?.caret())
        if (frame != null) {
            BubbleSlot(frame = frame, measured = bubbleHeight > 0, onHeight = { bubbleHeight = it }, bubble = bubble)
        }
    }
}

/** El cuadro del paso con las medidas, las barras del sistema y la dirección de esta pantalla. */
@Composable
private fun spotlightFrameOf(
    window: WindowFrame,
    targetBounds: Rect?,
    keepClear: Rect?,
    bubbleHeight: Int
): SpotlightFrame? {
    val density = LocalDensity.current
    val systemBars = WindowInsets.systemBars
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val target = targetBounds?.let(window::toLocal) ?: return null
    return with(density) {
        SpotlightPlacement.frame(
            target = target,
            keepClear = keepClear?.let(window::toLocal),
            areaWidth = window.size.width.toFloat(),
            top = systemBars.getTop(this) + EDGE_MARGIN.toPx(),
            bottom = window.size.height - systemBars.getBottom(this) - EDGE_MARGIN.toPx(),
            bubbleHeight = bubbleHeight.toFloat(),
            metrics = SpotlightMetrics(
                edgeMargin = EDGE_MARGIN.toPx(),
                maxBubbleWidth = BUBBLE_MAX_WIDTH.toPx(),
                gap = GAP.toPx(),
                holePadding = HOLE_PADDING.toPx(),
                caretInset = CARET_INSET.toPx(),
                bubblePadding = BUBBLE_PADDING.toPx()
            ),
            rightToLeft = rightToLeft
        )
    }
}

/**
 * El globo en su lugar. Posición absoluta (no se refleja en derecha a izquierda): el
 * cuadro ya viene en coordenadas reales de la capa.
 */
@Composable
private fun BubbleSlot(
    frame: SpotlightFrame,
    measured: Boolean,
    onHeight: (Int) -> Unit,
    bubble: @Composable (actionsEndInset: Dp) -> Unit
) {
    with(LocalDensity.current) {
        Box(
            modifier = Modifier
                .absoluteOffset { IntOffset(frame.bubble.left.roundToInt(), frame.bubble.top.roundToInt()) }
                .width(frame.bubble.width.toDp())
                .heightIn(max = frame.maxBubbleHeight.toDp())
                .onSizeChanged { onHeight(it.height) }
                .graphicsLayer { alpha = if (measured) 1f else 0f }
        ) {
            bubble(frame.actionsEndInset.toDp())
        }
    }
}

/**
 * Globo del recorrido: título en negrita, frase y contador, que TalkBack lee juntos
 * en cada paso (un nodo, región viva), y los botones de al menos 48 dp. «Saltar»
 * ([skipLabel]) es la acción callada; el botón lleno avanza. Con letra muy grande
 * los botones se apilan y el texto se desplaza dentro del globo. [actionsEndInset]
 * acorta la fila de botones por el lado final (la nube, ver [SpotlightLayer]).
 */
@Composable
fun SpotlightBubble(
    title: String,
    body: String,
    counter: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    skipLabel: String?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    actionsEndInset: Dp = 0.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = V2Corner.card)
            .clip(V2Corner.card)
            .background(V2Palette.cardSurface)
            .verticalScroll(rememberScrollState())
            .padding(BUBBLE_PADDING)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        ) {
            Text(
                text = title,
                style = InteriorTypeVoice.cardTitle,
                color = V2Palette.inkPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = body, style = InteriorTypeVoice.body, color = V2Palette.inkSecondary)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = counter, style = InteriorTypeVoice.label, color = V2Palette.inkSecondary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        BubbleActions(
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            skipLabel = skipLabel,
            onSkip = onSkip,
            modifier = Modifier.padding(end = actionsEndInset)
        )
    }
}

/** «Saltar» y el botón lleno; con letra muy grande, apilados (el lleno primero). */
@Composable
private fun BubbleActions(
    primaryLabel: String,
    onPrimary: () -> Unit,
    skipLabel: String?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (LocalDensity.current.fontScale >= STACKED_BUTTONS_FONT_SCALE) {
        Column(modifier = modifier) {
            V2PrimaryButton(text = primaryLabel, onClick = onPrimary)
            if (skipLabel != null) {
                SkipButton(label = skipLabel, onClick = onSkip, modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (skipLabel != null) SkipButton(label = skipLabel, onClick = onSkip)
            V2PrimaryButton(text = primaryLabel, onClick = onPrimary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SkipButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = MIN_TOUCH)) {
        Text(text = label, style = InteriorTypeVoice.bodyStrong, color = V2Palette.inkPrimary)
    }
}

/** Pico del globo: base sobre su borde en [baseY], apuntando arriba o abajo. */
private data class Caret(val x: Float, val baseY: Float, val pointsUp: Boolean)

private fun SpotlightFrame.caret(): Caret = Caret(
    x = caretX,
    baseY = if (caretPointsUp) bubble.top else bubble.bottom,
    pointsUp = caretPointsUp
)

@Composable
private fun Scrim(hole: Rect?, caret: Caret?) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Se traga cada gesto entero: ni toques ni desplazamientos llegan abajo.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        // Esquinas que acompañan la forma: un círculo si el hueco es pequeño y cuadrado.
        val rounded = hole?.let {
            RoundRect(it, CornerRadius(min(HOLE_RADIUS.toPx(), min(it.width, it.height) / 2)))
        }
        val veil = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            rounded?.let { addRoundRect(it) }
        }
        drawPath(veil, V2Palette.spotlightScrim)
        rounded?.let {
            drawPath(Path().apply { addRoundRect(it) }, color = Color.White, style = Stroke(width = RING_WIDTH.toPx()))
        }
        caret?.let {
            val half = CARET_HALF_WIDTH.toPx()
            val tipY = if (it.pointsUp) it.baseY - CARET_HEIGHT.toPx() else it.baseY + CARET_HEIGHT.toPx()
            val pointer = Path().apply {
                moveTo(it.x - half, it.baseY)
                lineTo(it.x, tipY)
                lineTo(it.x + half, it.baseY)
                close()
            }
            drawPath(pointer, V2Palette.cardSurface)
        }
    }
}

private val EDGE_MARGIN = 16.dp
private val BUBBLE_PADDING = 20.dp
private val BUBBLE_MAX_WIDTH = 480.dp
private val GAP = 14.dp
private val HOLE_PADDING = 6.dp
private val HOLE_RADIUS = 28.dp
private val RING_WIDTH = 2.dp
private val CARET_INSET = 28.dp
private val CARET_HALF_WIDTH = 9.dp
private val CARET_HEIGHT = 9.dp
private val MIN_TOUCH: Dp = 48.dp
private const val STACKED_BUTTONS_FONT_SCALE = 1.5f

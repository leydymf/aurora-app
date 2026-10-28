package com.auroraclima.app.core.ui.v2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
    private val sizes = mutableMapOf<Any, IntSize>()

    /** Límites del elemento [key] en la ventana; null si no está en composición. */
    fun boundsOf(key: Any): Rect? = bounds[key]

    /**
     * Desplaza lo que lo contenga hasta que [key] se vea entero (con letra grande
     * puede estar fuera de la pantalla) y, si se puede, con [roomBelow] píxeles libres
     * debajo: ahí cabe la tarjeta de la guía sin apretarse contra el elemento (v0.3.4).
     * Vuelve al terminar; sin nada que desplazar, vuelve enseguida.
     */
    suspend fun bringIntoView(key: Any, roomBelow: Float = 0f) {
        val requester = requesters[key] ?: return
        val size = sizes[key]
        if (size == null || roomBelow <= 0f) {
            requester.bringIntoView()
        } else {
            requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), size.height + roomBelow))
        }
    }

    internal fun attach(key: Any, requester: BringIntoViewRequester) {
        requesters[key] = requester
    }

    internal fun place(key: Any, rect: Rect, size: IntSize) {
        bounds[key] = rect
        sizes[key] = size
    }

    internal fun detach(key: Any, requester: BringIntoViewRequester) {
        if (requesters[key] === requester) {
            requesters.remove(key)
            bounds.remove(key)
            sizes.remove(key)
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
        .onGloballyPositioned { targets.place(key, it.boundsInWindow(), it.size) }
}

/**
 * Dónde va todo lo que dibuja la capa (v0.3.3; v0.3.4 para la guía). Puro, para
 * probarlo en la JVM: la tarjeta va debajo del elemento si cabe; si no, encima; y si
 * no cabe entera en ninguno de los dos lados (letra muy grande en una pantalla baja),
 * en el lado con más espacio y con el alto de ese lado (se desplaza por dentro):
 * **la tarjeta nunca tapa el elemento iluminado**. Todo en las coordenadas de la capa.
 */
object SpotlightPlacement {
    /**
     * [target] y el resultado en coordenadas de la capa; [top]..[bottom] es la franja
     * usable (sin barras del sistema) y [gap] el aire entre elemento y tarjeta.
     */
    fun place(target: Rect, bubbleHeight: Float, top: Float, bottom: Float, gap: Float): BubbleSpot {
        val roomBelow = (bottom - (target.bottom + gap)).coerceAtLeast(0f)
        val roomAbove = ((target.top - gap) - top).coerceAtLeast(0f)
        // Sin medir todavía (alto cero) va al lado más amplio: así se mide con sitio de
        // verdad y no queda «entrando» en un lado sin espacio, con alto cero para siempre.
        val measured = bubbleHeight > 0f
        return when {
            measured && bubbleHeight <= roomBelow -> BubbleSpot(
                top = target.bottom + gap,
                below = true,
                maxHeight = roomBelow,
                fits = true
            )
            measured && bubbleHeight <= roomAbove ->
                BubbleSpot(top = target.top - gap - bubbleHeight, below = false, maxHeight = roomAbove, fits = true)
            roomBelow >= roomAbove -> BubbleSpot(
                top = target.bottom + gap,
                below = true,
                maxHeight = roomBelow,
                fits = false
            )
            else -> BubbleSpot(top = target.top - gap - roomAbove, below = false, maxHeight = roomAbove, fits = false)
        }
    }

    /**
     * Cuánto acortar por el lado final la fila de botones de la tarjeta [bubble] para
     * que [keepClear] (la nube) no quede encima de ninguno: cero si no se cruzan. La
     * nube va por encima de la capa y un toque en ella sale al clima; un botón debajo
     * de ella sería una salida por accidente. [contentPadding] es el margen interior de
     * la tarjeta, que ya separa la fila de su borde. Con [rightToLeft] el lado final es
     * el izquierdo, y allí está también la nube.
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
     * El cuadro completo de un paso: el hueco alrededor de [target], lo que hay que
     * dejar libre alrededor del hueco ([ringOf]: el círculo a mano) y la tarjeta de alto
     * [bubbleHeight] centrada en [areaWidth]. Si cabe, la tarjeta se aparta
     * [SpotlightMetrics.arrowGap] para que quepa la flecha; si no, solo
     * [SpotlightMetrics.gap]. La tarjeta no baja del borde inferior de [keepClear]: así
     * la nube solo puede cruzar su franja de botones, y esa franja se acorta (ver
     * [actionsEndInset]).
     */
    fun frame(
        target: Rect,
        keepClear: Rect?,
        areaWidth: Float,
        top: Float,
        bottom: Float,
        bubbleHeight: Float,
        metrics: SpotlightMetrics,
        rightToLeft: Boolean = false,
        ringOf: (Rect) -> Rect = { it }
    ): SpotlightFrame {
        val hole = target.inflate(metrics.holePadding)
        val ring = ringOf(hole)
        val lowest = keepClear?.let { min(bottom, it.bottom) } ?: bottom
        val width = min(areaWidth - 2 * metrics.edgeMargin, metrics.maxBubbleWidth)
        val left = (areaWidth - width) / 2
        val roomy = place(ring, bubbleHeight, top, lowest, metrics.arrowGap)
        val spot = if (roomy.fits) roomy else place(ring, bubbleHeight, top, lowest, metrics.gap)
        if (!spot.fits && bubbleHeight > 0f) {
            overflowFrame(hole, keepClear, left, width, top, lowest, bubbleHeight, metrics, rightToLeft, ringOf)
                ?.let { return it }
        }
        val height = min(bubbleHeight, spot.maxHeight)
        val bubble = Rect(left, spot.top, left + width, spot.top + height)
        return SpotlightFrame(
            hole = hole,
            ring = ring,
            bubble = bubble,
            maxBubbleHeight = spot.maxHeight,
            actionsEndInset = actionsEndInset(bubble, keepClear, metrics.gap, metrics.bubblePadding, rightToLeft)
        )
    }

    /**
     * Cuando la tarjeta no entra entera en ningún lado (un elemento muy alto con letra
     * grande, como Acciones rápidas apiladas): la tarjeta va abajo, con su alto y como
     * mucho [OVERFLOW_SHARE] de la franja, y el hueco se queda con la parte del elemento
     * que queda a la vista encima de ella. Se ilumina menos del elemento, pero la tarjeta
     * sigue sin tapar nada de lo iluminado y se lee entera. Null si lo que quedaría
     * iluminado es menos que [SpotlightMetrics.minLit]: entonces gana la regla general.
     */
    private fun overflowFrame(
        hole: Rect,
        keepClear: Rect?,
        left: Float,
        width: Float,
        top: Float,
        lowest: Float,
        bubbleHeight: Float,
        metrics: SpotlightMetrics,
        rightToLeft: Boolean,
        ringOf: (Rect) -> Rect
    ): SpotlightFrame? {
        val maxHeight = (lowest - top) * OVERFLOW_SHARE
        val height = min(bubbleHeight, maxHeight)
        val cardTop = lowest - height
        val ringBelow = ringOf(hole).bottom - hole.bottom
        val litBottom = min(hole.bottom, cardTop - metrics.gap - ringBelow)
        val litTop = maxOf(hole.top, top)
        if (litBottom - litTop < metrics.minLit) return null
        val lit = Rect(hole.left, litTop, hole.right, litBottom)
        val bubble = Rect(left, cardTop, left + width, lowest)
        return SpotlightFrame(
            hole = lit,
            ring = ringOf(lit),
            bubble = bubble,
            maxBubbleHeight = maxHeight,
            actionsEndInset = actionsEndInset(bubble, keepClear, metrics.gap, metrics.bubblePadding, rightToLeft)
        )
    }

    /** Lo más que ocupa la tarjeta de la franja cuando el elemento no le deja sitio. */
    const val OVERFLOW_SHARE = 0.6f
}

/**
 * La tarjeta empieza en [top]; [below] dice si va debajo del elemento; [maxHeight] es
 * el alto del lado elegido y [fits] si entra entera.
 */
data class BubbleSpot(val top: Float, val below: Boolean, val maxHeight: Float, val fits: Boolean)

/** Medidas de la capa en píxeles; las fija la capa con su densidad. */
data class SpotlightMetrics(
    val edgeMargin: Float,
    val maxBubbleWidth: Float,
    val gap: Float,
    val arrowGap: Float,
    val holePadding: Float,
    val minLit: Float = 0f,
    val bubblePadding: Float
)

/** Lo que dibuja la capa en un paso, en sus coordenadas. */
data class SpotlightFrame(
    val hole: Rect,
    val ring: Rect,
    val bubble: Rect,
    val maxBubbleHeight: Float,
    val actionsEndInset: Float
)

/**
 * Cómo se mueve y se señala la capa (v0.3.4): el hueco se desliza a cada elemento en
 * [holeMillis], la tarjeta aparece con un fundido corto en [cardMillis], y el círculo
 * a mano y la flecha (en [cueColor]; sin color, ninguno) se trazan con [draw] y el
 * círculo respira con [breath]. Con ceros y planes quietos, todo aparece en su sitio.
 */
@Immutable
data class SpotlightStyle(
    val holeMillis: Int = 0,
    val cardMillis: Int = 0,
    val draw: DrawPlan = DrawPlan.still,
    val breath: PulsePlan = PulsePlan.still,
    val cueColor: Color? = null
)

/**
 * Capa de recorrido: un velo que oscurece toda su área menos el hueco alrededor de
 * [targetBounds] (en coordenadas de la ventana) y, al lado que tenga sitio, la
 * tarjeta [bubble], que nunca tapa el elemento. El hueco se desliza de un elemento al
 * siguiente; el círculo a mano lo rodea y la flecha sale de la tarjeta hacia él. Sin
 * límites todavía solo pinta el velo. El velo se traga los toques: nada de lo que hay
 * debajo se activa, tampoco lo iluminado. Lo que esté POR ENCIMA de esta capa (la
 * nube del grafo seguro) sigue recibiendo el suyo.
 *
 * [keepClear] es un área que la tarjeta y la flecha respetan (la nube, en ventana):
 * la tarjeta no baja de su borde inferior y, si la cruza, su fila de botones se acorta
 * por el lado final (lo recibe [bubble] como margen). [stepKey] identifica el paso:
 * al cambiar, la tarjeta vuelve a aparecer y los trazos se dibujan otra vez. Con
 * [showBubble] en falso solo se ven el velo y el hueco (mientras el elemento se trae
 * a la vista).
 *
 * El contenido de [bubble] debe admitir medidas intrínsecas: la capa pregunta el alto
 * que la tarjeta querría (no el recortado) para decidir dónde va. Un `Lazy*`, un
 * `SubcomposeLayout` o un `BoxWithConstraints` ahí fallarían en ejecución.
 */
@Composable
fun SpotlightLayer(
    targetBounds: Rect?,
    modifier: Modifier = Modifier,
    keepClear: Rect? = null,
    style: SpotlightStyle = SpotlightStyle(),
    stepKey: Any? = null,
    showBubble: Boolean = true,
    bubble: @Composable (actionsEndInset: Dp) -> Unit
) {
    val window = rememberWindowFrame()
    var bubbleHeight by remember(stepKey) { mutableIntStateOf(0) }
    val keepLocal = keepClear?.let(window::toLocal)
    val frame = spotlightFrameOf(window, targetBounds, keepClear, bubbleHeight)
    val hole = rememberSlidingHole(frame?.hole, style.holeMillis)
    // Alineación absoluta: la tarjeta se coloca con coordenadas reales de la capa, y
    // una alineación relativa la reflejaría en derecha a izquierda antes de desplazarla.
    Box(modifier = modifier.fillMaxSize().trackWindowFrame(window), contentAlignment = AbsoluteAlignment.TopLeft) {
        Scrim(hole = hole)
        if (frame != null && showBubble) {
            val measured = bubbleHeight > 0
            if (style.cueColor != null && measured) {
                HandDrawnCue(
                    target = frame.hole,
                    card = frame.bubble,
                    avoid = listOfNotNull(keepLocal),
                    color = style.cueColor,
                    draw = style.draw,
                    breath = style.breath,
                    key = stepKey,
                    area = window.area()
                )
            }
            key(stepKey) {
                BubbleSlot(
                    frame = frame,
                    measured = measured,
                    appearMillis = style.cardMillis,
                    onHeight = { bubbleHeight = it },
                    bubble = bubble
                )
            }
        }
    }
}

/** Toda el área de la capa, en sus coordenadas: hasta ahí llegan los trazos. */
private fun WindowFrame.area(): Rect = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())

/**
 * El hueco en su sitio, deslizándose y cambiando de tamaño hacia [target] en
 * [millis] con curva suave; el primero aparece sin moverse, y con cero no se mueve
 * nunca. Si el elemento se desplaza (letra grande), el hueco lo sigue.
 */
@Composable
private fun rememberSlidingHole(target: Rect?, millis: Int): Rect? {
    val hole = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(target, millis) {
        if (target == null) return@LaunchedEffect
        if (!placed || millis == 0) {
            hole.snapTo(target)
            placed = true
        } else {
            hole.animateTo(target, tween(durationMillis = millis, easing = FastOutSlowInEasing))
        }
    }
    return if (placed) hole.value else null
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
                arrowGap = ARROW_GAP.toPx(),
                holePadding = HOLE_PADDING.toPx(),
                minLit = MIN_LIT.toPx(),
                bubblePadding = BUBBLE_PADDING.toPx()
            ),
            rightToLeft = rightToLeft,
            ringOf = { handCueRing(it, density, window.area()) }
        )
    }
}

/**
 * La tarjeta en su lugar. Posición absoluta (no se refleja en derecha a izquierda): el
 * cuadro ya viene en coordenadas reales de la capa. Aparece con un fundido corto y un
 * leve desplazamiento en [appearMillis] (con cero, de golpe); hasta medirse no se ve,
 * para no mostrarla en un lugar falso.
 */
@Composable
private fun BubbleSlot(
    frame: SpotlightFrame,
    measured: Boolean,
    appearMillis: Int,
    onHeight: (Int) -> Unit,
    bubble: @Composable (actionsEndInset: Dp) -> Unit
) {
    val appear = remember { Animatable(if (appearMillis == 0) 1f else 0f) }
    LaunchedEffect(measured, appearMillis) {
        if (measured && appearMillis > 0) appear.animateTo(1f, tween(durationMillis = appearMillis))
    }
    with(LocalDensity.current) {
        Box(
            modifier = Modifier
                .absoluteOffset { IntOffset(frame.bubble.left.roundToInt(), frame.bubble.top.roundToInt()) }
                .width(frame.bubble.width.toDp())
                .heightIn(max = frame.maxBubbleHeight.toDp())
                // El alto que la tarjeta querría, no el recortado: con él se decide dónde
                // va (con el recortado nunca se sabría que no entra entera).
                .layout { measurable, constraints ->
                    onHeight(measurable.maxIntrinsicHeight(constraints.maxWidth))
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    alpha = if (measured) appear.value else 0f
                    translationY = (1f - appear.value) * CARD_TRAVEL.toPx()
                }
        ) {
            bubble(frame.actionsEndInset.toDp())
        }
    }
}

@Composable
private fun Scrim(hole: Rect?) {
    Canvas(modifier = Modifier.fillMaxSize().blockTouchesBelow()) {
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
    }
}

private val EDGE_MARGIN = 16.dp
private val BUBBLE_PADDING = 20.dp
private val BUBBLE_MAX_WIDTH = 480.dp
private val GAP = 14.dp
private val ARROW_GAP = 64.dp
private val MIN_LIT = 96.dp
private val HOLE_PADDING = 6.dp
private val HOLE_RADIUS = 28.dp
private val CARD_TRAVEL = 8.dp

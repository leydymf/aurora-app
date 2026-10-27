package com.auroraclima.app.access.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.reserveCloudLane
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.WordSafeText
import com.auroraclima.app.core.ui.v2.fitsWithoutShrinking
import com.auroraclima.app.core.ui.v2.spotlightTarget

/**
 * Inicio del espacio interno (mockup v2-10, P09): saludo, tarjeta del
 * cuestionario con «Empezar» y los tres accesos rápidos. Sin conteo de
 * preguntas ni duración (decisión D8). Ajustes se abre desde el
 * engranaje arriba a la derecha (decisión D3). Los destinos los deciden la
 * navegación raíz y el host de pestañas (guardia RF-06, §2: sin imports entre
 * features — los accesos rápidos solo emiten callbacks). Es un menú estático
 * (guía §3, decisión 2026-07-10). Lo que el recorrido por Inicio ilumina va
 * marcado con `spotlightTarget`; fuera de la capa del recorrido no agrega nada.
 */
@Composable
fun PanelScreen(
    onStartGuidance: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onCall: () -> Unit,
    onSaveNote: () -> Unit,
    onLearn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .statusBarsPadding()
            // La zona que se desplaza termina antes de la nube: ninguna tarjeta ni botón
            // pasa por debajo de ella, con cualquier letra (v0.3.3). La nube no se mueve.
            .reserveCloudLane()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandHeader(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.spotlightTarget(HomeTourStep.SETTINGS)
            ) {
                Icon(
                    painter = painterResource(V2Icons.settings),
                    contentDescription = stringResource(R.string.access_panel_settings),
                    tint = V2Palette.inkSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Saludo en tarjeta (v2-10) con el léxico ya aprobado (D1).
        SoftCard {
            Text(
                text = stringResource(R.string.access_panel_title),
                style = InteriorTypeVoice.greeting,
                color = V2Palette.inkPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.access_panel_subtitle),
                style = InteriorTypeVoice.body,
                color = V2Palette.inkSecondary
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.access_home_section_start),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        // Tarjeta del cuestionario: texto genérico sin números (D8); el lila es
        // el acento fijo de «reconocer» (regla 1). Toda la tarjeta lo abre, como
        // «Resultados guardados» (v0.3.3: un toque en el texto no hacía nada).
        SoftCard(
            modifier = Modifier
                .spotlightTarget(HomeTourStep.START)
                .clip(V2Corner.card)
                .clickable(onClick = onStartGuidance, role = Role.Button)
        ) {
            IconListRow(
                iconRes = V2Icons.recognize,
                accent = V2Palette.accentRecognize,
                title = stringResource(R.string.access_panel_guide_title),
                supportingText = stringResource(R.string.access_panel_guide_body)
            )
            Spacer(modifier = Modifier.height(14.dp))
            // Único botón lleno de la pantalla (regla 2), al pie de su tarjeta.
            V2PrimaryButton(
                text = stringResource(R.string.access_home_start),
                onClick = onStartGuidance
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        // Resultados guardados (RF-31): el listado vive junto al cuestionario
        // que los produce; el destino lo decide la navegación raíz (RF-06, §2).
        SoftCard(modifier = Modifier.spotlightTarget(HomeTourStep.SAVED)) {
            IconListRow(
                iconRes = V2Icons.date,
                accent = V2Palette.accentRecognize,
                title = stringResource(R.string.access_panel_history_title),
                supportingText = stringResource(R.string.access_panel_history_body),
                onClick = onOpenHistory,
                trailing = { RowChevron() }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        // La sección entera (rótulo y tarjetas) es un solo paso del recorrido.
        Column(modifier = Modifier.spotlightTarget(HomeTourStep.SHORTCUTS)) {
            Text(
                text = stringResource(R.string.access_home_section_quick),
                style = InteriorTypeVoice.label,
                color = V2Palette.inkSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            QuickAccessCards(onCall = onCall, onSaveNote = onSaveNote, onLearn = onLearn)
        }
        // Aire final; la franja de la nube ya queda fuera de lo que se desplaza.
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Los tres accesos rápidos de P09: en una fila si caben, apilados si no.
 *
 * En **fila** ([QuickAccessCardRow]) cuando las tres etiquetas caben en el ancho de su
 * tarjeta con la letra que eligió la persona, sin reducirla: cada palabra entera y dos
 * líneas como mucho. Con letra normal pasa desde un teléfono de 360 dp.
 *
 * **Apiladas** ([QuickAccessCardColumn]) cuando alguna no cabe, también con letra normal
 * en una pantalla de 320 dp: la etiqueta conserva su letra (v0.3.3, decisión de la
 * autora por WCAG 1.4.4: antes la letra se reducía hasta caber en la fila, a la mitad o
 * algo más con letra 2.0).
 *
 * El ancho de texto en la fila se calcula aquí, una vez, y no dentro de la tarjeta: la
 * medida intrínseca de la fila no admite `BoxWithConstraints` por debajo.
 */
@Composable
internal fun QuickAccessCards(
    onCall: () -> Unit,
    onSaveNote: () -> Unit,
    onLearn: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Verde para llamar (regla 1); neutro para lo que agrupa varias cosas (nota con
    // adjuntos, guía) — regla 5. La redacción evita el término del mockup vedado por
    // el guardián de camuflaje (RNF-05).
    val items = listOf(
        QuickAccessItem(V2Icons.call, V2Palette.accentCare, R.string.access_home_call, onCall),
        QuickAccessItem(V2Icons.media, V2Palette.accentGroup, R.string.access_home_note, onSaveNote),
        QuickAccessItem(V2Icons.guide, V2Palette.accentGroup, R.string.access_home_learn, onLearn)
    )
    val labels = items.map { stringResource(it.labelRes) }
    // El medidor cambia con la densidad, la escala de letra y la dirección.
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val labelWidthPx = with(LocalDensity.current) {
            quickAccessLabelWidthPx(
                rowWidthPx = constraints.maxWidth,
                gapPx = QUICK_ACCESS_GAP.roundToPx(),
                sidePaddingPx = QUICK_ACCESS_SIDE_PADDING.roundToPx(),
                count = items.size
            )
        }
        val fitsInRow = remember(labels, labelWidthPx, measurer) {
            labels.all { label ->
                fitsWithoutShrinking(measurer, label, InteriorTypeVoice.label, labelWidthPx, QUICK_ACCESS_LABEL_LINES)
            }
        }
        if (fitsInRow) {
            QuickAccessCardRow(items = items, labels = labels, labelWidthPx = labelWidthPx)
        } else {
            QuickAccessCardColumn(items = items, labels = labels)
        }
    }
}

/**
 * Las tarjetas en fila. La fila se mide con [IntrinsicSize.Min] y cada tarjeta ocupa
 * toda la altura resultante: así miden lo mismo —la altura de la más alta— aunque una
 * etiqueta ocupe dos líneas y las otras una (mismo recurso que la tarjeta del listado
 * de resultados). El ancho ya era idéntico por los `weight(1f)`.
 */
@Composable
private fun QuickAccessCardRow(items: List<QuickAccessItem>, labels: List<String>, labelWidthPx: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(QUICK_ACCESS_GAP)
    ) {
        items.forEachIndexed { index, item ->
            QuickAccessCard(
                item = item,
                label = labels[index],
                labelWidthPx = labelWidthPx,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Las tarjetas apiladas a lo ancho, en el mismo orden que en la fila ([QuickAccessStackedCard]). */
@Composable
private fun QuickAccessCardColumn(items: List<QuickAccessItem>, labels: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(QUICK_ACCESS_GAP)
    ) {
        items.forEachIndexed { index, item ->
            QuickAccessStackedCard(item = item, label = labels[index])
        }
    }
}

/** Un acceso rápido: insignia, acento, etiqueta y acción. */
private class QuickAccessItem(
    @DrawableRes val iconRes: Int,
    val accent: AccentPair,
    @StringRes val labelRes: Int,
    val onClick: () -> Unit
)

/**
 * Ancho de texto de cada tarjeta de una fila de [count] tarjetas iguales separadas por
 * [gapPx], con [sidePaddingPx] de margen a cada lado del texto. Puro, para probarlo en
 * la JVM.
 */
internal fun quickAccessLabelWidthPx(rowWidthPx: Int, gapPx: Int, sidePaddingPx: Int, count: Int): Int =
    if (count <= 0) 0 else ((rowWidthPx - (count - 1) * gapPx) / count - 2 * sidePaddingPx).coerceAtLeast(0)

/**
 * Acceso rápido de P09 en la fila: tarjeta vertical con insignia de acento y
 * etiqueta. Llena la altura que fija la fila y centra su contenido. La etiqueta va
 * en dos líneas como mucho y nunca parte una palabra; la fila solo se arma si cabe
 * con la letra elegida, así que `WordSafeText` no la reduce (queda como red).
 */
@Composable
private fun QuickAccessCard(item: QuickAccessItem, label: String, labelWidthPx: Int, modifier: Modifier = Modifier) {
    SoftCard(
        modifier = modifier
            .fillMaxHeight()
            .clip(V2Corner.card)
            .clickable(onClick = item.onClick, role = Role.Button),
        contentPadding = PaddingValues(horizontal = QUICK_ACCESS_SIDE_PADDING, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AccentBadge(iconRes = item.iconRes, accent = item.accent)
            Spacer(modifier = Modifier.height(10.dp))
            WordSafeText(
                text = label,
                style = InteriorTypeVoice.label,
                availableWidthPx = labelWidthPx,
                maxLines = QUICK_ACCESS_LABEL_LINES,
                color = V2Palette.inkPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Acceso rápido apilado: a lo ancho, con la insignia al lado del texto, como las filas
 * de tarjeta del Inicio. La etiqueta conserva la letra elegida y baja de línea entre
 * palabras, sin tope de líneas; `WordSafeText` solo la reduciría si una palabra sola no
 * cupiera a lo ancho.
 */
@Composable
private fun QuickAccessStackedCard(item: QuickAccessItem, label: String) {
    SoftCard(
        modifier = Modifier
            .clip(V2Corner.card)
            .clickable(onClick = item.onClick, role = Role.Button),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentBadge(iconRes = item.iconRes, accent = item.accent)
            Spacer(modifier = Modifier.width(14.dp))
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                WordSafeText(
                    text = label,
                    style = InteriorTypeVoice.label,
                    availableWidthPx = constraints.maxWidth,
                    maxLines = Int.MAX_VALUE,
                    color = V2Palette.inkPrimary
                )
            }
        }
    }
}

/** Tope de líneas de la etiqueta en la fila: el ícono y el texto deben caber centrados. */
private const val QUICK_ACCESS_LABEL_LINES = 2

/** Separación entre tarjetas, en fila y apiladas: de ella sale el ancho de texto ([quickAccessLabelWidthPx]). */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal val QUICK_ACCESS_GAP = 12.dp

/**
 * Margen lateral del texto dentro de cada tarjeta. Es el único margen horizontal que
 * `SoftCard` agrega hoy; si ganara otro (un borde, por ejemplo), hay que restarlo en
 * [quickAccessLabelWidthPx] o el ancho calculado quedaría más grande que el real.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal val QUICK_ACCESS_SIDE_PADDING = 10.dp

@Preview(name = "Inicio · 360 dp", widthDp = 360, heightDp = 760, showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun PanelScreenNarrowPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        PanelScreen(
            onStartGuidance = {},
            onOpenHistory = {},
            onOpenSettings = {},
            onCall = {},
            onSaveNote = {},
            onLearn = {}
        )
    }
}

@Preview(name = "Inicio · ancho", widthDp = 600, heightDp = 760, showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun PanelScreenWidePreview() {
    AuroraV2Theme(V2Voice.Interior) {
        PanelScreen(
            onStartGuidance = {},
            onOpenHistory = {},
            onOpenSettings = {},
            onCall = {},
            onSaveNote = {},
            onLearn = {}
        )
    }
}

@Preview(
    name = "Inicio · fuente 2.0",
    widthDp = 360,
    heightDp = 760,
    fontScale = 2.0f,
    showBackground = true,
    backgroundColor = 0xFFF3F1F7
)
@Composable
private fun PanelScreenLargeFontPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        PanelScreen(
            onStartGuidance = {},
            onOpenHistory = {},
            onOpenSettings = {},
            onCall = {},
            onSaveNote = {},
            onLearn = {}
        )
    }
}

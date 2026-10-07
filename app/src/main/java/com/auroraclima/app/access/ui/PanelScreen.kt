package com.auroraclima.app.access.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
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

/**
 * Inicio del espacio interno (mockup v2-10, P09): saludo, tarjeta del
 * cuestionario con «Empezar» y los tres accesos rápidos. Sin conteo de
 * preguntas ni duración (decisión D8), así que sigue sin ViewModel — menú
 * estático (guía §3, decisión 2026-07-10). Configuración se abre desde el
 * engranaje arriba a la derecha (decisión D3). Los destinos los deciden la
 * navegación raíz y el host de pestañas (guardia RF-06, §2: sin imports entre
 * features — los accesos rápidos solo emiten callbacks).
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandHeader(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
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
        // el acento fijo de «reconocer» (regla 1).
        SoftCard {
            IconListRow(
                iconRes = V2Icons.recognize,
                accent = V2Palette.accentRecognize,
                title = stringResource(R.string.access_panel_guide_title),
                supportingText = stringResource(R.string.access_panel_guide_body)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        // Único botón lleno de la pantalla (regla 2).
        V2PrimaryButton(
            text = stringResource(R.string.access_home_start),
            onClick = onStartGuidance
        )
        Spacer(modifier = Modifier.height(14.dp))
        // Resultados guardados (RF-31): el listado vive junto al cuestionario
        // que los produce; el destino lo decide la navegación raíz (RF-06, §2).
        SoftCard {
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
        Text(
            text = stringResource(R.string.access_home_section_quick),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        QuickAccessRow(onCall = onCall, onSaveNote = onSaveNote, onLearn = onLearn)
        // Aire final: la nube queda por encima de la barra de pestañas.
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Los tres accesos rápidos de P09. La fila se mide con [IntrinsicSize.Min] y
 * cada tarjeta ocupa toda la altura resultante: así las tres miden lo mismo —
 * la altura de la más alta — en cualquier ancho de pantalla y con cualquier
 * escala de fuente, aunque una etiqueta ocupe dos líneas y las otras una
 * (mismo recurso que la tarjeta del listado de resultados). El ancho ya era
 * idéntico por los tres `weight(1f)`.
 */
@Composable
internal fun QuickAccessRow(
    onCall: () -> Unit,
    onSaveNote: () -> Unit,
    onLearn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Verde para llamar (regla 1); neutro para lo que agrupa varias
        // cosas (nota con adjuntos, guía) — regla 5. La redacción evita el
        // término del mockup vedado por el guardián de camuflaje (RNF-05).
        QuickAccessCard(
            iconRes = V2Icons.call,
            accent = V2Palette.accentCare,
            label = stringResource(R.string.access_home_call),
            onClick = onCall,
            modifier = Modifier.weight(1f)
        )
        QuickAccessCard(
            iconRes = V2Icons.media,
            accent = V2Palette.accentGroup,
            label = stringResource(R.string.access_home_note),
            onClick = onSaveNote,
            modifier = Modifier.weight(1f)
        )
        QuickAccessCard(
            iconRes = V2Icons.guide,
            accent = V2Palette.accentGroup,
            label = stringResource(R.string.access_home_learn),
            onClick = onLearn,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Acceso rápido de P09: tarjeta vertical con insignia de acento y etiqueta.
 * Llena la altura que fija la fila y centra su contenido; la etiqueta se acota
 * a dos líneas para que con fuente grande la fila no crezca sin control.
 */
@Composable
private fun QuickAccessCard(
    @DrawableRes iconRes: Int,
    accent: AccentPair,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SoftCard(
        modifier = modifier
            .fillMaxHeight()
            .clip(V2Corner.card)
            .clickable(onClick = onClick, role = Role.Button),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AccentBadge(iconRes = iconRes, accent = accent)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = InteriorTypeVoice.label,
                color = V2Palette.inkPrimary,
                textAlign = TextAlign.Center,
                maxLines = QUICK_ACCESS_LABEL_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Tope de líneas de la etiqueta: el ícono y el texto deben caber centrados. */
private const val QUICK_ACCESS_LABEL_LINES = 2

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

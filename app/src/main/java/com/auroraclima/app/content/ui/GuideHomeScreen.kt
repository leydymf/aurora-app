package com.auroraclima.app.content.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2Voice

/**
 * Portada de la Guía (v2-11, pestaña del hogar interno): agrupa los tipos
 * (RF-21), las rutas (RF-22) y las líneas (RF-23) en tres tarjetas, con la nota
 * de lectura sin conexión (RF-26 — la persistencia offline de Firestore ya la
 * garantiza). Estática y sin ViewModel, como el Inicio (guía §3): los destinos
 * los decide el host de pestañas por callbacks. Léxico neutro aprobado (D1) y
 * sin conteos (criterio D8: los números de los mockups son datos vivos).
 */
@Composable
fun GuideHomeScreen(
    onOpenTopics: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenLines: () -> Unit,
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
        BrandHeader()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.content_guide_title),
            style = InteriorTypeVoice.screenTitle,
            color = V2Palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.content_guide_subtitle),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(20.dp))
        // Tres secciones (v2-11). Tarjetas agrupadoras en neutro (regla 5: el
        // color aparece un nivel más adentro); la de líneas lleva el verde de
        // llamar porque esa ES su única acción dominante.
        GuideSectionCard(
            iconRes = V2Icons.guide,
            accent = V2Palette.accentGroup,
            title = stringResource(R.string.content_topics_title),
            support = stringResource(R.string.content_guide_topics_support),
            onClick = onOpenTopics
        )
        Spacer(modifier = Modifier.height(14.dp))
        GuideSectionCard(
            iconRes = V2Icons.place,
            accent = V2Palette.accentGroup,
            title = stringResource(R.string.content_topics_routes_button),
            support = stringResource(R.string.content_guide_routes_support),
            onClick = onOpenRoutes
        )
        Spacer(modifier = Modifier.height(14.dp))
        GuideSectionCard(
            iconRes = V2Icons.call,
            accent = V2Palette.accentCare,
            title = stringResource(R.string.content_topics_lines_button),
            support = stringResource(R.string.content_guide_lines_support),
            onClick = onOpenLines
        )
        Spacer(modifier = Modifier.height(20.dp))
        // Nota offline (RF-26): visto verde de «listo» + texto — el color nunca
        // informa solo (regla 3).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(V2Icons.synced),
                contentDescription = null,
                tint = V2Palette.accentCare.stroke,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.content_guide_offline),
                style = InteriorTypeVoice.label,
                color = V2Palette.inkSecondary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Tarjeta de sección de la portada: insignia + título + apoyo + chevron. */
@Composable
private fun GuideSectionCard(
    @DrawableRes iconRes: Int,
    accent: AccentPair,
    title: String,
    support: String,
    onClick: () -> Unit
) {
    SoftCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        IconListRow(
            iconRes = iconRes,
            accent = accent,
            title = title,
            supportingText = support,
            onClick = onClick,
            trailing = { RowChevron() }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun GuideHomeScreenPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        GuideHomeScreen(onOpenTopics = {}, onOpenRoutes = {}, onOpenLines = {})
    }
}

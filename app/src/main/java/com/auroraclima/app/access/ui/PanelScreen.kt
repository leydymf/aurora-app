package com.auroraclima.app.access.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.v2.AccentBadge
import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton

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
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.access_home_section_quick),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkSecondary
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        // Aire final: la nube queda por encima de la barra de pestañas.
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Acceso rápido de P09: tarjeta vertical con insignia de acento y etiqueta. */
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
            .clip(V2Corner.card)
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AccentBadge(iconRes = iconRes, accent = accent)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = InteriorTypeVoice.label,
                color = V2Palette.inkPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

package com.auroraclima.app.access.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Plum
import com.auroraclima.app.core.ui.theme.Sky
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Hogar del espacio interno (mockup P09 / alta-fidelidad-16): tres módulos.
 * Menú estático sin datos propios: no lleva ViewModel — el patrón UiState (guía §3)
 * aplica a pantallas con estado (decisión registrada en la guía, 2026-07-10).
 * Los destinos los decide la navegación raíz (guardia RF-06); las tarjetas de
 * orientación (Día 5) y de notas (Día 6) aún no navegan y se muestran atenuadas.
 */
@Composable
fun PanelScreen(onOpenInfo: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandHeader(modifier = Modifier.weight(1f))
            // TODO(Día 5): pantalla de configuración (mockup P21); mientras tanto
            // el engranaje del mockup se muestra deshabilitado.
            IconButton(onClick = {}, enabled = false) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.access_panel_settings),
                    tint = SteelDeep
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        SectionTitle(stringResource(R.string.access_panel_title))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.access_panel_subtitle),
            fontFamily = PlexSans,
            fontSize = 15.sp,
            color = SteelDeep
        )
        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PanelCard(
                iconRes = R.drawable.ic_book,
                iconTint = SteelDeep,
                chipColor = Sky,
                title = stringResource(R.string.access_panel_info_title),
                body = stringResource(R.string.access_panel_info_body),
                onClick = onOpenInfo
            )
            PanelCard(
                iconRes = R.drawable.ic_compass,
                iconTint = SteelDeep,
                chipColor = Sky,
                title = stringResource(R.string.access_panel_guide_title),
                body = stringResource(R.string.access_panel_guide_body),
                // TODO(Día 5): módulo de orientación (RF-27..31).
                onClick = null
            )
            PanelCard(
                iconRes = R.drawable.ic_folder,
                iconTint = Plum,
                chipColor = Plum.copy(alpha = 0.14f),
                title = stringResource(R.string.access_panel_journal_title),
                body = stringResource(R.string.access_panel_journal_body),
                // TODO(Día 6): módulo de notas personales (RF-32..39).
                onClick = null
            )
        }
    }
}

@Composable
private fun PanelCard(
    @DrawableRes iconRes: Int,
    iconTint: Color,
    chipColor: Color,
    title: String,
    body: String,
    onClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (onClick != null) 1f else 0.55f),
        shape = RoundedCornerShape(16.dp),
        color = CloudWhite
    ) {
        Row(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = chipColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Night
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    fontFamily = PlexSans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = SteelDeep
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Steel
            )
        }
    }
}

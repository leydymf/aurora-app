package com.auroraclima.app.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.BackHeader
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.components.SectionTitle
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Configuración del espacio interno (mockup P21 / alta-fidelidad-33): grupos de
 * filas con navegación. Menú estático sin ViewModel — mismo criterio que
 * PanelScreen (guía, 2026-07-10): el patrón UiState aplica a pantallas con
 * estado. Los destinos los decide la navegación raíz (guardia RF-06).
 *
 * Redacción de camuflaje sobre el mockup (mismo criterio que P-17/P-25):
 * «Cambiar código secreto» → «Cambiar tu clave personal» («secreto» está vedado
 * en recursos por CamouflageResourcesTest). La fila «Eliminar cuenta y datos»
 * llegará con RF-20.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onReopenGuide: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Mist)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        BackHeader(tag = stringResource(R.string.access_settings_tag), onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        SectionTitle(stringResource(R.string.access_settings_title))
        Spacer(modifier = Modifier.height(20.dp))

        SectionTag(stringResource(R.string.access_settings_group_security))
        Spacer(modifier = Modifier.height(10.dp))
        SettingsGroup {
            SettingsRow(
                icon = painterResource(R.drawable.ic_book),
                title = stringResource(R.string.access_settings_guide),
                onClick = onReopenGuide
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.access_settings_footer),
            fontFamily = PlexSans,
            fontSize = 12.sp,
            color = Steel,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        // Aire final para no chocar con el botón nube de la esquina.
        Spacer(modifier = Modifier.height(64.dp))
    }
}

/** Tarjeta blanca que agrupa filas, con separadores suaves (mockup P21). */
@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CloudWhite
    ) {
        Column { content() }
    }
}

@Composable
internal fun SettingsRowDivider() {
    HorizontalDivider(color = Mist, thickness = 1.dp)
}

/** Fila de configuración: ícono lineal, título y chevron de navegación. */
@Composable
internal fun SettingsRow(
    icon: Painter,
    title: String,
    onClick: () -> Unit,
    titleColor: Color = Night,
    showChevron: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = SteelDeep,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Steel
            )
        }
    }
}

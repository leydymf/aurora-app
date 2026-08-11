package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Botón lleno en lavanda: la ÚNICA acción principal de la pantalla (regla 2 del
 * anexo — un solo botón lleno por pantalla). Texto 7.5:1 sobre la lavanda (AA).
 */
@Composable
fun V2PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = V2Corner.control,
        colors = ButtonDefaults.buttonColors(
            containerColor = V2Palette.primaryAction,
            contentColor = V2Palette.onPrimaryAction,
            disabledContainerColor = V2Palette.primaryAction.copy(alpha = 0.45f),
            disabledContentColor = V2Palette.onPrimaryAction.copy(alpha = 0.7f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = V2Palette.onPrimaryAction,
                strokeWidth = 2.dp
            )
        } else {
            Text(text = text, style = InteriorTypeVoice.bodyStrong)
        }
    }
}

/**
 * Acción secundaria: blanca con borde (regla 2). El borde va en el gris del texto
 * secundario y no en lavanda: 6.4:1 contra 2.1:1 sobre blanco (README de
 * mockups-v2 §3.6 — la lavanda no alcanza el 3:1 no textual de AA).
 */
@Composable
fun V2SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = V2Corner.control,
        border = BorderStroke(width = 1.2.dp, color = V2Palette.inkSecondary),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = V2Palette.cardSurface,
            contentColor = V2Palette.inkPrimary
        )
    ) {
        Text(text = text, style = InteriorTypeVoice.bodyStrong)
    }
}

/**
 * Chip de tipo o estado: tinte de fondo, texto (y opcional icono de contorno) en
 * el trazo del mismo acento. Siempre lleva texto: el color acompaña, nunca
 * informa solo (regla 3).
 */
@Composable
fun AccentChip(text: String, accent: AccentPair, modifier: Modifier = Modifier, @DrawableRes iconRes: Int? = null) {
    Row(
        modifier = modifier
            .clip(V2Corner.pill)
            .background(accent.tint)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = accent.stroke,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, style = InteriorTypeVoice.label, color = accent.stroke)
    }
}

/**
 * Progreso por segmentos («paso N de 3» en P05–P07, preguntas del cuestionario en
 * P16). Va SIEMPRE junto a un texto que enuncie el avance (regla 3): el relleno
 * lavanda es refuerzo visual, nunca el único indicador (README §3.6).
 */
@Composable
fun SegmentedProgressBar(totalSegments: Int, completedSegments: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(totalSegments) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(V2Corner.pill)
                    .background(
                        if (index < completedSegments) V2Palette.primaryAction else V2Palette.accentGroup.tint
                    )
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun V2ButtonsPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        Column(modifier = Modifier.padding(16.dp)) {
            V2PrimaryButton(text = "Continuar", onClick = {})
            Spacer(modifier = Modifier.height(12.dp))
            V2PrimaryButton(text = "Continuar", onClick = {}, enabled = false)
            Spacer(modifier = Modifier.height(12.dp))
            V2SecondaryButton(text = "Volver", onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun AccentChipPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        Row(modifier = Modifier.padding(16.dp)) {
            AccentChip(text = "Guardado", accent = V2Palette.accentCare, iconRes = V2Icons.synced)
            Spacer(modifier = Modifier.width(8.dp))
            AccentChip(text = "En espera", accent = V2Palette.accentJustice)
            Spacer(modifier = Modifier.width(8.dp))
            AccentChip(text = "Aviso", accent = V2Palette.accentNotice)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun SegmentedProgressBarPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Paso 1 de 3", style = InteriorTypeVoice.label, color = V2Palette.inkSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            SegmentedProgressBar(totalSegments = 3, completedSegments = 1)
        }
    }
}

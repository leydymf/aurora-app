package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Tarjeta suave del V2: blanca, esquina 28 (anexo v2-29). Solo composición,
 * sin lógica ni sombra: el contraste con el fondo lila la separa por sí sola.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(V2Corner.card)
            .background(V2Palette.cardSurface)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Círculo de acento con icono de contorno: icono de 21 sobre círculo de 46
 * (v2-29 §iconografía). El tinte pinta el fondo y el trazo tiñe el icono.
 * Decorativo por defecto (descripción nula): siempre acompaña a un texto
 * visible (regla 3), así que no se repite para el lector de pantalla.
 */
@Composable
fun AccentBadge(
    @DrawableRes iconRes: Int,
    accent: AccentPair,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(accent.tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = accent.stroke,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun SoftCardPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        SoftCard(modifier = Modifier.padding(16.dp)) {
            Text(text = "Título de tarjeta", style = InteriorTypeVoice.cardTitle, color = V2Palette.inkPrimary)
            Text(
                text = "Texto de lectura dentro de la tarjeta.",
                style = InteriorTypeVoice.body,
                color = V2Palette.inkSecondary
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun AccentBadgePreview() {
    AuroraV2Theme(V2Voice.Interior) {
        AccentBadge(
            iconRes = V2Icons.cloud,
            accent = V2Palette.accentQuickExit,
            modifier = Modifier.padding(8.dp)
        )
    }
}

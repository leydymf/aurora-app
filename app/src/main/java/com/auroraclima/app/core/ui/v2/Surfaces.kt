package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
 * [size] admite las variantes menores de los mockups (vistos de señales,
 * encabezados de sector en v2-13/v2-14); el icono conserva la proporción 21/46.
 */
@Composable
fun AccentBadge(
    @DrawableRes iconRes: Int,
    accent: AccentPair,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 46.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.tint),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = accent.stroke,
            modifier = Modifier.size(size * 21f / 46f)
        )
    }
}

/**
 * Cuerpo visual de una hoja inferior (P20·b): tarjeta blanca de esquinas
 * superiores 28 con el asa de arrastre decorativa del mockup. Solo apariencia:
 * la ventana segura la pone `core/security/SecureSheet`, que recibe esto como
 * contenido — mismo reparto que CloudReturnVisual con la salida rápida.
 */
@Composable
fun V2SheetCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(V2Corner.sheet)
            .background(V2Palette.cardSurface)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 4.dp)
                .clip(V2Corner.pill)
                .background(V2Palette.divider)
        )
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

/**
 * Aviso en el rosa de avisos y cuidado (regla 1): tinte de fondo, icono de
 * información y texto en el trazo del mismo acento (6.6:1, AA). Es el aviso de
 * alcance del cuestionario (RF-30, v2-17/v2-19) y de toda advertencia serena.
 */
@Composable
fun V2NoticeCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(V2Corner.control)
            .background(V2Palette.accentNotice.tint)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(V2Icons.info),
            contentDescription = null,
            tint = V2Palette.accentNotice.stroke,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = InteriorTypeVoice.caption,
            color = V2Palette.accentNotice.stroke
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
            accent = V2Palette.accentCloud,
            modifier = Modifier.padding(8.dp)
        )
    }
}

package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Fila de lista con icono de contorno (portada de Guía, rutas, ajustes): insignia
 * de acento + título + texto de apoyo opcional + espacio final opcional. Área de
 * toque ≥ 48 dp (constantes de v2-04). Sin lógica: solo pinta y emite el toque.
 */
@Composable
fun IconListRow(
    @DrawableRes iconRes: Int,
    accent: AccentPair,
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    supportingMaxLines: Int = Int.MAX_VALUE,
    onClick: (() -> Unit)? = null,
    // La insignia es decorativa por defecto (regla 3); una fila cuya ACCIÓN es
    // el icono (llamar, v2-19) lo anuncia aquí para el lector de pantalla.
    iconDescription: String? = null,
    // Una acción irreversible tiñe su título con el trazo rosa de avisos
    // (v2-23); el resto conserva la tinta principal.
    titleColor: Color = V2Palette.inkPrimary,
    trailing: (@Composable () -> Unit)? = null
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick, role = Role.Button)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(V2Corner.control)
            .then(clickModifier)
            .heightIn(min = 56.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccentBadge(iconRes = iconRes, accent = accent, contentDescription = iconDescription)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = InteriorTypeVoice.bodyStrong, color = titleColor)
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = InteriorTypeVoice.caption,
                    color = V2Palette.inkSecondary,
                    maxLines = supportingMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun IconListRowPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        SoftCard(modifier = Modifier.padding(16.dp)) {
            IconListRow(
                iconRes = V2Icons.protection,
                accent = V2Palette.accentProtection,
                title = "Título de la fila",
                supportingText = "Texto de apoyo de la fila",
                onClick = {}
            )
            IconListRow(
                iconRes = V2Icons.guide,
                accent = V2Palette.accentGroup,
                title = "Fila sin texto de apoyo"
            )
        }
    }
}

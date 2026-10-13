package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R

/**
 * Apariencia V2 de la nube de regreso al clima (RF-16): círculo de 52 dp en el
 * violeta EXCLUSIVO del anexo v2-29 con la nube de contorno encima. Esto es SOLO
 * apariencia: el mecanismo (grafo seguro, `SessionManager.closeNow`, posición y
 * alcance del pulgar) vive en `core/security/SecureNavigation`, que la inyecta.
 * Para un tercero sigue siendo un botón decorativo «del clima» (F2 de A2-6).
 */
@Composable
fun CloudReturnVisual(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(52.dp)
            .shadow(elevation = 6.dp, shape = CircleShape)
            .background(V2Palette.accentCloud.tint)
            .clickable(onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(V2Icons.cloud),
            contentDescription = stringResource(R.string.access_back_to_weather),
            tint = V2Palette.accentCloud.stroke,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun CloudReturnVisualPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        CloudReturnVisual(onClick = {}, modifier = Modifier.padding(12.dp))
    }
}

package com.auroraclima.app.content.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Elementos compartidos del módulo de información (mockups P10–P13): filas
 * navegables y salida al marcador. La cabecera y los estados de carga/error
 * viven en core/ui/components/StatusElements.kt desde P-42a.
 */

/** Fila navegable con título, resumen truncado y flecha (filas de P10 y P12). */
@Composable
internal fun ContentListRow(title: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                text = summary,
                fontFamily = PlexSans,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = SteelDeep,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Steel
        )
    }
}

/** Tarjeta blanca que agrupa filas con separadores (bloques de P10 y P12). */
@Composable
internal fun ContentRowGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CloudWhite
    ) {
        Column { content() }
    }
}

@Composable
internal fun ContentRowDivider() {
    HorizontalDivider(color = Mist, thickness = 1.dp)
}

/** Punto de viñeta + texto (listado de señales de P11). */
@Composable
internal fun BulletRow(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Steel)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontFamily = PlexSans,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Night
        )
    }
}

/** Espacio final para que el contenido no quede bajo el botón nube (RF-16). */
internal val CONTENT_BOTTOM_CLEARANCE = 88.dp

/**
 * Abre el marcador con el número puesto (RF-23, decisión F5): ACTION_DIAL no
 * exige permiso alguno (RNF-04), no marca por sí solo y la app no conserva
 * rastro. Sin marcador instalado (p. ej. tablet WiFi) la acción se descarta en
 * silencio: un crash sería una anomalía visible y quedaría en logcat (D4 S2
 * B-1). Único punto de salida al teléfono de todo el módulo.
 */
internal fun dialNumber(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }
}

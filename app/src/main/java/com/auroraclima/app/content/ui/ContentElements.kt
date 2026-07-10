package com.auroraclima.app.content.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.SectionTag
import com.auroraclima.app.core.ui.theme.CloudWhite
import com.auroraclima.app.core.ui.theme.Mist
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.Steel
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Elementos compartidos del módulo de información (mockups P10–P13): cabecera con
 * flecha de regreso, filas navegables, estados de carga y de error. Los mensajes de
 * error son SIEMPRE genéricos: nada de `error.message` en la UI (P-24, guía §3).
 */
@Composable
internal fun ContentBackHeader(tag: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.content_back),
                tint = Night
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        SectionTag(tag)
    }
}

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

@Composable
internal fun ContentLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SteelDeep)
    }
}

/** Error genérico con reintento (RF-25: sin detalles técnicos visibles; P-24). */
@Composable
internal fun ContentErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.content_error_generic),
            fontFamily = PlexSans,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Night,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.content_retry),
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = SteelDeep
            )
        }
    }
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

package com.auroraclima.app.core.ui.v2

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R

/**
 * Cabecera de retorno del V2 (v2-12..v2-16): círculo blanco con flecha de
 * contorno y etiqueta de contexto al lado. Reemplaza a la BackHeader V1 en las
 * pantallas migradas; la flecha y el chevron de fila son mobiliario de
 * navegación, no significados de la librería cerrada — por eso viven aquí y no
 * en [V2Icons].
 */
@Composable
fun V2BackHeader(tag: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        V2BackButton(onBack = onBack)
        Spacer(modifier = Modifier.size(12.dp))
        Text(text = tag, style = InteriorTypeVoice.bodyStrong, color = V2Palette.inkPrimary)
    }
}

/**
 * El círculo blanco con flecha de [V2BackHeader], suelto: la navegación del
 * cuestionario (v2-18) lo ancla abajo, junto a «Siguiente», sin etiqueta.
 */
@Composable
fun V2BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = V2Palette.cardSurface,
            contentColor = V2Palette.inkPrimary
        ),
        modifier = modifier
            .size(44.dp)
            .background(V2Palette.cardSurface, CircleShape)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_left_soft),
            contentDescription = stringResource(R.string.common_back),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Chevron final de una fila navegable (mobiliario, como la flecha de
 * [V2BackHeader]). Decorativo: la fila entera es el destino táctil y ya anuncia
 * su acción.
 */
@Composable
fun RowChevron(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_soft),
        contentDescription = null,
        tint = V2Palette.inkSecondary,
        modifier = modifier.size(20.dp)
    )
}

/** Carga del V2: indicador centrado en la tinta secundaria. */
@Composable
fun V2LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = V2Palette.inkSecondary)
    }
}

/** Error genérico del V2 con reintento: sin detalles técnicos visibles (P-24, guía §3). */
@Composable
fun V2ErrorState(@StringRes messageRes: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(messageRes),
            style = InteriorTypeVoice.body,
            color = V2Palette.inkPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.common_retry),
                style = InteriorTypeVoice.bodyStrong,
                color = V2Palette.inkSecondary
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF3F1F7)
@Composable
private fun V2BackHeaderPreview() {
    AuroraV2Theme(V2Voice.Interior) {
        V2BackHeader(tag = "Etiqueta", onBack = {}, modifier = Modifier.padding(12.dp))
    }
}

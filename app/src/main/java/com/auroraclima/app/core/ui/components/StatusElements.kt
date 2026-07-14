package com.auroraclima.app.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.theme.PlexSans
import com.auroraclima.app.core.ui.theme.SteelDeep

/**
 * Estados compartidos de las pantallas internas: cabecera con flecha de regreso,
 * carga y error genérico con reintento. Extraídos de content/ y questionnaire/
 * cuando records/ iba a duplicarlos por tercera vez (P-42a; §2 de ARQUITECTURA.md —
 * lo común de dos features vive en core, mismo criterio que SectionTag y el kit de
 * formulario). Los mensajes de error son SIEMPRE genéricos: nada de `error.message`
 * en la UI (P-24, guía §3).
 */
@Composable
fun BackHeader(tag: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = Night
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        SectionTag(tag)
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SteelDeep)
    }
}

/** Error genérico con reintento: sin detalles técnicos visibles (P-24, guía §3). */
@Composable
fun ErrorState(@StringRes messageRes: Int, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(messageRes),
            fontFamily = PlexSans,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Night,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text(
                text = stringResource(R.string.common_retry),
                fontFamily = PlexSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = SteelDeep
            )
        }
    }
}

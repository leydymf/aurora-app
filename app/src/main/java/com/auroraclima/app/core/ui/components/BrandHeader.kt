package com.auroraclima.app.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.theme.Marcellus
import com.auroraclima.app.core.ui.theme.Night
import com.auroraclima.app.core.ui.v2.V2BackButton

/**
 * Encabezado de marca del espacio interno: símbolo squircle + wordmark en
 * Marcellus (manual de marca §02, «Aurora real»). Las pantallas de la fachada
 * NO deben usarlo (decisión F1: fuera del espacio interno solo sans neutra,
 * sin símbolo ni serif).
 */
@Composable
fun BrandHeader(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Flecha opcional junto a la marca (v0.3.2), en el patrón de Ajustes
        // (v2-23): la muestra quien tenga a dónde volver.
        if (onBack != null) {
            V2BackButton(onBack = onBack)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Image(
            painter = painterResource(R.drawable.ic_sparkle_badge),
            // Decorativa: sin descripción que delate nada por accesibilidad (RNF-05).
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontFamily = Marcellus,
            fontSize = 22.sp,
            color = Night
        )
    }
}

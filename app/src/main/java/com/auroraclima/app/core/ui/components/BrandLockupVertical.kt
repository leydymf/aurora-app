package com.auroraclima.app.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.theme.Marcellus
import com.auroraclima.app.core.ui.theme.Night

/**
 * Lockup vertical de la marca del espacio interno (docs/marca/13): símbolo arriba y
 * wordmark en Marcellus debajo, centrados. Mismo arte que [BrandHeader] a mayor
 * tamaño. El wordmark es logo, no texto de lectura: se fija en dp y no crece con la
 * fuente del sistema. Como el encabezado, NO es para la fachada (decisión F1).
 */
@Composable
fun BrandLockupVertical(modifier: Modifier = Modifier) {
    // Proporción del SVG 13: símbolo 44 : wordmark 28 (≈ 1 : 0,64).
    val wordmarkSize = with(LocalDensity.current) { 56.dp.toSp() }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.drawable.ic_sparkle_badge),
            // Decorativa: sin descripción que delate nada por accesibilidad (RNF-05).
            contentDescription = null,
            modifier = Modifier.size(88.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontFamily = Marcellus,
            fontSize = wordmarkSize,
            color = Night
        )
    }
}

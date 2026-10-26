package com.auroraclima.app.accounts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.components.BrandLockupVertical
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.CloudLaneBar
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2Voice

/**
 * Bienvenida (v0.3.2): primer paso del primer uso, antes del consentimiento (RF-05,
 * RF-17). Sin flecha: detrás solo está el clima, y la nube de salida rápida (RF-16)
 * la pone el grafo seguro. El contenido se desplaza y «Siguiente» queda fijo abajo,
 * en el carril de la nube, con cualquier tamaño de letra.
 */
@Composable
fun WelcomeRoute(onNext: () -> Unit) {
    // El ViewModel existe solo por el efecto de su init: descartar la activación sin
    // terminar. La guardia de alcanzabilidad localiza la entrada por la forma
    // genérica de esta llamada; cambiarla exige ajustar esa guardia.
    hiltViewModel<WelcomeViewModel>()
    AuroraV2Theme(V2Voice.Interior) {
        WelcomeScreen(onNext = onNext)
    }
}

@Composable
internal fun WelcomeScreen(onNext: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .systemBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrandLockupVertical()
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.welcome_what),
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.inkPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.welcome_hint),
                    style = InteriorTypeVoice.caption,
                    color = V2Palette.inkSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
        CloudLaneBar {
            V2PrimaryButton(text = stringResource(R.string.welcome_next), onClick = onNext)
        }
    }
}

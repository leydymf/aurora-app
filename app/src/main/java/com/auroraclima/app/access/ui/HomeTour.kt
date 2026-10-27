package com.auroraclima.app.access.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalCloudAnchor
import com.auroraclima.app.core.ui.v2.LocalSpotlightTargets
import com.auroraclima.app.core.ui.v2.SpotlightBubble
import com.auroraclima.app.core.ui.v2.SpotlightLayer
import com.auroraclima.app.core.ui.v2.SpotlightTargets

/**
 * Enlace del recorrido por Inicio con su estado ([HomeTourViewModel], scoped a la
 * entrada del panel: muere con el pop del grafo seguro). [replayRequested] llega de
 * Ajustes por la navegación raíz y se apaga con [onReplayHandled]; [onShowHome] lleva
 * a la pestaña Inicio cuando el recorrido empieza, porque casi todo lo que ilumina
 * vive en ella.
 */
@Composable
fun HomeTourHost(
    replayRequested: Boolean,
    onReplayHandled: () -> Unit,
    onShowHome: () -> Unit,
    content: @Composable () -> Unit
) {
    val viewModel: HomeTourViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(replayRequested) {
        if (replayRequested) {
            viewModel.onReplay()
            onReplayHandled()
        }
    }
    val touring = state.step != null
    LaunchedEffect(touring) {
        if (touring) onShowHome()
    }
    HomeTourLayer(
        step = state.step,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip,
        content = content
    )
}

/**
 * La capa del recorrido, sin estado propio (se prueba sin Hilt). Va en la MISMA
 * ventana, dentro de la ruta del panel, y no en una ventana propia: así la nube del
 * grafo seguro, que `SecureScreen` dibuja después del contenido, queda por encima del
 * velo en todos los pasos y el primer toque le llega a ella (RF-16). El bloqueo de
 * capturas y de recientes y el registro de interacción los da la ruta (RNF-02, RNF-03).
 *
 * En cada paso primero desplaza lo necesario para que el elemento se vea entero (con
 * letra grande puede estar fuera) y después lo ilumina. Mientras el recorrido está a
 * la vista, lo de abajo se oculta a TalkBack: solo quedan el globo y la nube. El
 * gesto atrás equivale a «Saltar».
 */
@Composable
fun HomeTourLayer(
    step: HomeTourStep?,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    targets: SpotlightTargets = remember { SpotlightTargets() },
    content: @Composable () -> Unit
) {
    val cloud = LocalCloudAnchor.current
    // El paso ya en su sitio: hasta que termina el desplazamiento solo hay velo.
    var revealed by remember { mutableStateOf<HomeTourStep?>(null) }
    LaunchedEffect(step) {
        revealed = null
        if (step != null) {
            targets.bringIntoView(step)
            revealed = step
        }
    }
    BackHandler(enabled = step != null, onBack = onSkip)
    CompositionLocalProvider(LocalSpotlightTargets provides targets) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (step != null) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                content()
            }
            if (step != null) {
                val shown = revealed?.takeIf { it == step }
                val bounds = when (shown) {
                    null -> null
                    HomeTourStep.CLOUD -> cloud?.bounds
                    else -> targets.boundsOf(shown)
                }
                // La nube queda libre: el globo no baja de ella ni pone un botón debajo.
                SpotlightLayer(targetBounds = bounds, keepClear = cloud?.bounds) { actionsEndInset ->
                    HomeTourBubble(step = step, onNext = onNext, onSkip = onSkip, actionsEndInset = actionsEndInset)
                }
            }
        }
    }
}

/** El globo de un paso: título, frase, «n de 7», «Saltar» y «Siguiente» o, al final, «Entendido». */
@Composable
private fun HomeTourBubble(step: HomeTourStep, onNext: () -> Unit, onSkip: () -> Unit, actionsEndInset: Dp) {
    SpotlightBubble(
        title = stringResource(step.titleRes),
        body = stringResource(step.bodyRes),
        counter = stringResource(R.string.access_tour_counter, step.number, HomeTourStep.count),
        primaryLabel = stringResource(if (step.isLast) R.string.access_tour_done else R.string.access_tour_next),
        onPrimary = onNext,
        // En el último paso no queda nada que saltar.
        skipLabel = if (step.isLast) null else stringResource(R.string.access_tour_skip),
        onSkip = onSkip,
        actionsEndInset = actionsEndInset
    )
}

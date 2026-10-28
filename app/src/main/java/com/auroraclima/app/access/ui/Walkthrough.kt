package com.auroraclima.app.access.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.ui.rememberReduceMotion
import com.auroraclima.app.core.ui.v2.DrawPlan
import com.auroraclima.app.core.ui.v2.LocalSpotlightTargets
import com.auroraclima.app.core.ui.v2.PulsePlan
import com.auroraclima.app.core.ui.v2.SpotlightTargets
import com.auroraclima.app.core.ui.v2.muted

/**
 * Enlace de la guía de Aurora con su estado ([WalkthroughViewModel], scoped a la
 * entrada del panel: muere con el pop del grafo seguro). [replayRequested] llega de
 * Ajustes por la navegación raíz y se apaga con [onReplayHandled]; [onShowHome] lleva
 * a la pestaña Inicio al llegar a las partes 2 y 3, que iluminan lo que hay en ella.
 */
@Composable
fun WalkthroughHost(
    replayRequested: Boolean,
    onReplayHandled: () -> Unit,
    onShowHome: () -> Unit,
    content: @Composable () -> Unit
) {
    val viewModel: WalkthroughViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    LaunchedEffect(replayRequested) {
        if (replayRequested) {
            viewModel.onReplay()
            onReplayHandled()
        }
    }
    val overHome = state.step?.let { it.part >= WalkthroughStep.SPACE_PART && it.isCard } == true
    LaunchedEffect(overHome) {
        if (overHome) onShowHome()
    }
    val actions = remember(viewModel) {
        WalkthroughActions(
            onStart = viewModel::onStart,
            onNotNow = viewModel::onNotNow,
            onNext = viewModel::onNext,
            onBack = viewModel::onBack,
            onFinishEarly = viewModel::onFinishEarly,
            onFinish = viewModel::onFinish,
            onBackGesture = viewModel::onBackGesture,
            onPracticeInputChange = {
                // El teclado es una ventana propia: cada tecla debe contar como
                // interacción o la inactividad expulsaría a mitad de práctica (P-15).
                sessionManager.registerInteraction()
                viewModel.onPracticeInputChange(it)
            }
        )
    }
    WalkthroughLayer(
        state = state,
        actions = actions,
        motion = WalkthroughMotion.forMotion(reduceMotion = rememberReduceMotion()),
        content = content
    )
}

/** Lo que la guía le pide a su estado; agrupado para que pase de una vez. */
class WalkthroughActions(
    val onStart: () -> Unit,
    val onNotNow: () -> Unit,
    val onNext: () -> Unit,
    val onBack: () -> Unit,
    val onFinishEarly: () -> Unit,
    val onFinish: () -> Unit,
    val onBackGesture: () -> Unit,
    val onPracticeInputChange: (String) -> Unit
)

/**
 * Cómo se mueve la guía: fundido entre pantallas, cambio de tarjeta, desliz del hueco,
 * trazo de flechas y círculos, y la respiración del círculo. Con «Quitar animaciones»
 * todo aparece quieto y completo. La salida con la nube nunca se anima: es la de
 * siempre y no pasa por aquí.
 */
@Immutable
data class WalkthroughMotion(
    val screenFadeMillis: Int,
    val cardMillis: Int,
    val holeMillis: Int,
    val draw: DrawPlan,
    val breath: PulsePlan
) {
    val isStatic: Boolean get() = screenFadeMillis == 0

    companion object {
        fun forMotion(reduceMotion: Boolean): WalkthroughMotion = if (reduceMotion) {
            WalkthroughMotion(0, 0, 0, DrawPlan.still, PulsePlan.still)
        } else {
            WalkthroughMotion(
                screenFadeMillis = 250,
                cardMillis = 200,
                holeMillis = 300,
                draw = DrawPlan.forMotion(reduceMotion = false),
                breath = PulsePlan.breathing(reduceMotion = false)
            )
        }
    }
}

/**
 * La capa de la guía, sin estado propio (se prueba sin Hilt). Va en la MISMA ventana,
 * dentro de la ruta del panel, y no en una ventana propia: así la nube del grafo
 * seguro, que `SecureScreen` dibuja después del contenido, queda por encima de todo
 * en cada paso y el primer toque le llega a ella (RF-16). El bloqueo de capturas y de
 * recientes desde el primer cuadro y el registro de interacción los da la ruta
 * (RNF-02, RNF-03).
 *
 * Mientras la guía está abierta, la app de abajo se ve apagada ([muted]), se oculta a
 * TalkBack y no recibe toques (los traga la pantalla de la guía o el velo): solo
 * responden los controles de la guía y la nube.
 */
@Composable
fun WalkthroughLayer(
    state: WalkthroughUiState,
    actions: WalkthroughActions,
    modifier: Modifier = Modifier,
    motion: WalkthroughMotion = WalkthroughMotion.forMotion(reduceMotion = false),
    targets: SpotlightTargets = remember { SpotlightTargets() },
    content: @Composable () -> Unit
) {
    val open = state.step != null
    val scene = WalkthroughScene.of(state.step)
    val keyboard = LocalSoftwareKeyboardController.current
    // Solo la parte 1 tiene dónde escribir: en las demás escenas un teclado abierto (el
    // de la barra de la fachada, que puede seguir arriba al entrar) tapaba los botones.
    LaunchedEffect(scene) {
        if (scene != WalkthroughScene.NONE && scene != WalkthroughScene.TYPE_IN) keyboard?.hide()
    }
    BackHandler(enabled = open, onBack = actions.onBackGesture)
    CompositionLocalProvider(LocalSpotlightTargets provides targets) {
        Box(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .muted(open)
                    .then(if (open) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                content()
            }
            Crossfade(
                targetState = scene,
                animationSpec = if (motion.isStatic) snap() else tween(motion.screenFadeMillis),
                label = "escena"
            ) { scene ->
                WalkthroughSceneContent(scene = scene, state = state, actions = actions, motion = motion)
            }
        }
    }
}

/** Las pantallas entre las que se funde la guía; los pasos de una misma escena no se funden. */
internal enum class WalkthroughScene {
    NONE,
    OVERVIEW,
    TYPE_IN,
    HOME,
    DONE;

    companion object {
        fun of(step: WalkthroughStep?): WalkthroughScene = when {
            step == null -> NONE
            step == WalkthroughStep.OVERVIEW -> OVERVIEW
            step == WalkthroughStep.TYPE_IN -> TYPE_IN
            step == WalkthroughStep.DONE -> DONE
            else -> HOME
        }
    }
}

@Composable
private fun WalkthroughSceneContent(
    scene: WalkthroughScene,
    state: WalkthroughUiState,
    actions: WalkthroughActions,
    motion: WalkthroughMotion
) {
    when (scene) {
        WalkthroughScene.NONE -> Unit
        WalkthroughScene.OVERVIEW -> OverviewScreen(onStart = actions.onStart, onNotNow = actions.onNotNow)
        WalkthroughScene.TYPE_IN -> TypeInScreen(state = state, actions = actions, motion = motion)
        WalkthroughScene.DONE -> DoneScreen(reachedEarly = state.reachedEarly, onFinish = actions.onFinish)
        // Mientras se funde hacia otra escena, la saliente conserva su último paso.
        WalkthroughScene.HOME -> {
            val step = remember { StepHolder() }.also { holder ->
                state.step?.takeIf { WalkthroughScene.of(it) == scene }?.let { holder.step = it }
            }.step
            if (step != null) HomeScene(step = step, actions = actions, motion = motion)
        }
    }
}

/** El último paso de una escena, para la tarjeta que se desvanece. */
private class StepHolder {
    var step: WalkthroughStep? = null
}

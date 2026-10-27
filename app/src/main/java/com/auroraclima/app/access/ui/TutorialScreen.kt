package com.auroraclima.app.access.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalCloudAnchor
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SecureTextEntry
import com.auroraclima.app.core.ui.components.BrandHeader
import com.auroraclima.app.core.ui.rememberReduceMotion
import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.CueArrow
import com.auroraclima.app.core.ui.v2.CueArrowWidth
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.PulsePlan
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.V2SecondaryButton
import com.auroraclima.app.core.ui.v2.V2StepHeader
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.keyboardAware
import com.auroraclima.app.core.ui.v2.pulseHalo
import com.auroraclima.app.core.ui.v2.rememberPulsePhase
import com.auroraclima.app.core.ui.v2.rememberWindowFrame
import com.auroraclima.app.core.ui.v2.trackWindowFrame
import kotlin.math.roundToInt

/**
 * Tutorial del espacio interno (RF-10; mockup v2-08, P07). Paso 3 de 3.
 * La vista V2 presenta los TRES momentos juntos en tarjetas (antes, pager de
 * tres páginas): la máquina de pasos sigue siendo del ViewModel — «Practicar»
 * invoca su salto directo a la práctica (`onStartPractice`, hallazgo M-1 de la
 * revisión S2; cambio de VM justificado en el plan de sesión aprobado).
 *
 * La práctica de salida es REAL (decisión de la usuaria sobre P07·b y la nota
 * F3·F4 de A2-6): la nube de la esquina — pintada por el grafo seguro — sale de
 * verdad a la fachada, y el reingreso con la clave ejercita RF-12 tal como será
 * siempre. En modo repaso (RF-11) la única diferencia es el rótulo y que
 * la flecha regresa a donde estaba la usuaria.
 */
@Composable
fun TutorialRoute(isReview: Boolean, onClose: () -> Unit, onSkip: () -> Unit) {
    val viewModel: TutorialViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val sessionManager = LocalSessionManager.current
    // Saltar (refinamiento RF-10, 2026-08): el destino lo decide el NavHost,
    // como el resto del flujo del primer uso (guardia RF-06).
    LaunchedEffect(viewModel) {
        viewModel.skipEvents.collect { onSkip() }
    }
    LaunchedEffect(viewModel) {
        viewModel.closeEvents.collect { onClose() }
    }
    // La flecha y el gesto atrás hacen lo mismo (v0.3.2; la regla vive en el
    // ViewModel). En la primera página el gesto hace el pop normal: repaso →
    // Ajustes; primer uso → la fachada, porque el setup ya salió de la pila.
    BackHandler(enabled = !state.isFirstStep, onBack = viewModel::onBackRequested)
    AuroraV2Theme(V2Voice.Interior) {
        TutorialScreen(
            state = state,
            isReview = isReview,
            onStartPractice = viewModel::onStartPractice,
            onSkip = { viewModel.onSkip(isReview) },
            onBack = viewModel::onBackRequested,
            onPracticeInputChange = {
                // El teclado es una ventana propia: cada tecla debe contar como
                // interacción o la inactividad expulsaría a mitad de práctica (P-15).
                sessionManager.registerInteraction()
                viewModel.onPracticeInputChange(it)
            },
            // «Quitar animaciones» deja el halo y la flecha quietos (ADR 2026-10-25 (c)).
            cuePlan = PulsePlan.forMotion(reduceMotion = rememberReduceMotion())
        )
    }
}

/**
 * La práctica señala dónde actuar (v0.3.3): primero la barra, con halo, flecha y
 * etiqueta; al coincidir el código, la etiqueta pasa a confirmar el ingreso y la
 * señal se muda a la nube real de la esquina. La señal de la nube va en una capa
 * sobre el contenido desplazable, en la posición que publica el grafo seguro
 * ([LocalCloudAnchor]); la nube sigue por encima de todo y su toque no cambia.
 */
@Composable
internal fun TutorialScreen(
    state: TutorialUiState,
    isReview: Boolean,
    onStartPractice: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onPracticeInputChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    cuePlan: PulsePlan = PulsePlan.forMotion(reduceMotion = false)
) {
    // Lo que ocupa la señal de la nube desde abajo: el contenido deja ese aire al
    // final para que ningún texto quede debajo de la etiqueta.
    var cloudCueClearance by remember { mutableStateOf(0.dp) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .keyboardAware()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // En la primera página del primer uso no hay a dónde volver: detrás solo
            // está el clima.
            BrandHeader(onBack = onBack.takeIf { state.showsBack(isReview) })
            Spacer(modifier = Modifier.height(16.dp))
            if (state.step == TutorialStep.PRACTICE) {
                PracticeContent(state.practiceInput, state.practiceStatus, cuePlan, onPracticeInputChange)
            } else {
                OverviewContent(isReview, state.showsSkip(isReview), onStartPractice, onSkip)
            }
            // Aire final para no chocar con el botón nube de la esquina ni con su señal.
            Spacer(modifier = Modifier.height(maxOf(64.dp, if (state.showsCloudCue) cloudCueClearance else 0.dp)))
        }
        if (state.showsCloudCue) {
            CloudCue(plan = cuePlan, onClearanceChange = { cloudCueClearance = it })
        }
    }
}

/** Los tres momentos del mockup v2-08 en una sola pantalla, con la librería cerrada. */
private data class TutorialMoment(
    @DrawableRes val iconRes: Int,
    val accent: AccentPair,
    val titleRes: Int,
    val bodyRes: Int
)

private val MOMENTS = listOf(
    // La llave abre (regla 4: llave, nunca candado); la nube violeta es exclusiva
    // de la salida; el escudo azul cuenta lo que un tercero NO llega a ver.
    TutorialMoment(
        iconRes = V2Icons.code,
        accent = V2Palette.accentRecognize,
        titleRes = R.string.access_guide_enter_title,
        bodyRes = R.string.access_guide_enter_body
    ),
    TutorialMoment(
        iconRes = V2Icons.cloud,
        accent = V2Palette.accentCloud,
        titleRes = R.string.access_guide_exit_title,
        bodyRes = R.string.access_guide_exit_body
    ),
    TutorialMoment(
        iconRes = V2Icons.protection,
        accent = V2Palette.accentProtection,
        titleRes = R.string.access_guide_close_title,
        bodyRes = R.string.access_guide_close_body
    )
)

@Composable
private fun OverviewContent(isReview: Boolean, showSkip: Boolean, onStartPractice: () -> Unit, onSkip: () -> Unit) {
    if (isReview) {
        Text(
            text = stringResource(R.string.access_guide_tag_review),
            style = InteriorTypeVoice.label,
            color = V2Palette.inkSecondary
        )
    } else {
        V2StepHeader(step = 3, totalSteps = 3)
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.access_guide_overview_title),
        style = InteriorTypeVoice.screenTitle,
        color = V2Palette.inkPrimary
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.access_guide_overview_subtitle),
        style = InteriorTypeVoice.body,
        color = V2Palette.inkSecondary
    )
    Spacer(modifier = Modifier.height(20.dp))
    MOMENTS.forEach { moment ->
        SoftCard(modifier = Modifier.padding(bottom = 12.dp)) {
            IconListRow(
                iconRes = moment.iconRes,
                accent = moment.accent,
                title = stringResource(moment.titleRes),
                supportingText = stringResource(moment.bodyRes)
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    PracticeTeaserCard()
    Spacer(modifier = Modifier.height(24.dp))
    V2PrimaryButton(
        text = stringResource(R.string.access_guide_practice_cta),
        onClick = onStartPractice
    )
    if (showSkip) {
        // Un solo lleno por pantalla (kit V2): saltar es la acción secundaria y
        // la línea de abajo recuerda la reapertura desde Ajustes (RF-11).
        Spacer(modifier = Modifier.height(10.dp))
        V2SecondaryButton(
            text = stringResource(R.string.access_guide_skip),
            onClick = onSkip
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.access_guide_skip_hint),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Tarjeta rosa «Practiquemos ahora» del mockup (el rosa siempre es aviso/cuidado, regla 1). */
@Composable
private fun PracticeTeaserCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(V2Corner.card)
            .background(V2Palette.accentNotice.tint)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(V2Icons.waiting),
                contentDescription = null,
                tint = V2Palette.accentNotice.stroke,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.access_guide_practice_card_title),
                style = InteriorTypeVoice.bodyStrong,
                color = V2Palette.accentNotice.stroke
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.access_guide_practice_teaser),
            style = InteriorTypeVoice.caption,
            color = V2Palette.accentNotice.stroke
        )
    }
}

/**
 * Paso 1 de la señal: etiqueta y flecha sobre la barra, y la barra con halo. Al
 * coincidir, la etiqueta se vuelve la confirmación (en el verde de «listo») y se
 * cierra el teclado, para que la nube vuelva a su esquina y la señal la encuentre
 * ahí; el paso 2 lo pinta [CloudCue]. La lógica de la práctica sigue en el ViewModel.
 */
@Composable
private fun PracticeContent(
    input: String,
    status: PracticeStatus,
    cuePlan: PulsePlan,
    onInputChange: (String) -> Unit
) {
    val matched = status == PracticeStatus.MATCHED
    val focusManager = LocalFocusManager.current
    LaunchedEffect(matched) {
        if (matched) focusManager.clearFocus()
    }
    PracticeIntro()
    Spacer(modifier = Modifier.height(16.dp))
    // Con el ingreso logrado la señal se muda a la nube: aquí la fase se queda quieta.
    val phase = rememberPulsePhase(if (matched) PulsePlan.still else cuePlan)
    if (matched) {
        PracticeSuccessCard(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        )
        Spacer(modifier = Modifier.height(14.dp))
    } else {
        BarCue(phase)
    }
    // La barra va fuera de las ramas: no se recrea al coincidir el código.
    PracticeSearchBar(value = input, onValueChange = onInputChange, haloPhase = phase.takeUnless { matched })
    if (status == PracticeStatus.MISMATCH) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.access_guide_practice_retry),
            style = InteriorTypeVoice.caption,
            color = V2Palette.inkSecondary
        )
    }
}

/** Rótulo, título y frase de la práctica. */
@Composable
private fun PracticeIntro() {
    Text(
        text = stringResource(R.string.access_guide_practice_tag),
        style = InteriorTypeVoice.label,
        color = V2Palette.inkSecondary
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.access_guide_practice_title),
        style = InteriorTypeVoice.screenTitle,
        color = V2Palette.inkPrimary
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.access_guide_practice_body),
        style = InteriorTypeVoice.body,
        color = V2Palette.inkSecondary
    )
}

/** Etiqueta y flecha sobre la barra, en el lila de «entrar con el código» (regla 1), el de la llave. */
@Composable
private fun BarCue(phase: State<Float>) {
    CueLabel(
        text = stringResource(R.string.access_guide_practice_hint),
        accent = V2Palette.accentRecognize
    )
    CueArrow(
        phase = phase,
        color = V2Palette.accentRecognize.stroke,
        modifier = Modifier.padding(start = BAR_ARROW_INSET, top = 4.dp, bottom = 6.dp)
    )
}

/**
 * Paso 2 de la señal: halo alrededor de la nube real y, encima, la etiqueta con su
 * flecha. El halo se dibuja en el contenido, que queda DEBAJO de la nube, así que
 * solo asoma su aro; nada de esta capa recibe toques. Sin grafo seguro (sin nube
 * que señalar) no pinta nada. Las posiciones son absolutas: la nube llega en
 * coordenadas reales de la ventana, también en derecha a izquierda.
 */
@Composable
private fun CloudCue(plan: PulsePlan, onClearanceChange: (Dp) -> Unit) {
    val cloudBounds = LocalCloudAnchor.current?.bounds ?: return
    val window = rememberWindowFrame()
    val phase = rememberPulsePhase(plan)
    // Alineación absoluta, como las posiciones: una relativa reflejaría el halo en
    // derecha a izquierda antes de desplazarlo.
    Box(modifier = Modifier.fillMaxSize().trackWindowFrame(window), contentAlignment = AbsoluteAlignment.TopLeft) {
        val cloud = window.toLocal(cloudBounds) ?: return@Box
        with(LocalDensity.current) {
            Box(
                modifier = Modifier
                    .absoluteOffset { IntOffset(cloud.left.roundToInt(), cloud.top.roundToInt()) }
                    .size(width = cloud.width.toDp(), height = cloud.height.toDp())
                    .pulseHalo(phase, V2Palette.accentCloud.stroke, cornerRadius = (cloud.width / 2).toDp())
            )
        }
        CloudHint(cloud = cloud, area = window.size, phase = phase, onClearanceChange = onClearanceChange)
    }
}

/**
 * Etiqueta y flecha encima de la nube, alineadas con el borde de la nube que da a la
 * orilla de la pantalla (la derecha, o la izquierda en derecha a izquierda), y la
 * flecha sobre su centro.
 */
@Composable
private fun BoxScope.CloudHint(cloud: Rect, area: IntSize, phase: State<Float>, onClearanceChange: (Dp) -> Unit) {
    val onRight = cloud.center.x >= area.width / 2f
    with(LocalDensity.current) {
        val edge = (if (onRight) area.width - cloud.right else cloud.left).coerceAtLeast(0f).toDp()
        val arrowInset = ((cloud.width.toDp() - CueArrowWidth) / 2).coerceAtLeast(0.dp)
        Column(
            modifier = Modifier
                .align(if (onRight) AbsoluteAlignment.BottomRight else AbsoluteAlignment.BottomLeft)
                .absolutePadding(
                    left = if (onRight) CLOUD_CUE_ROOM else edge,
                    right = if (onRight) edge else CLOUD_CUE_ROOM,
                    bottom = (area.height - cloud.top).coerceAtLeast(0f).toDp() + CLOUD_CUE_GAP
                )
                .onGloballyPositioned { onClearanceChange((area.height - it.positionInParent().y).toDp()) },
            horizontalAlignment = if (onRight) AbsoluteAlignment.Right else AbsoluteAlignment.Left
        ) {
            // El violeta es exclusivo de la salida (regla 1).
            CueLabel(
                text = stringResource(R.string.access_guide_practice_cloud_hint),
                accent = V2Palette.accentCloud,
                modifier = Modifier
                    .widthIn(max = CLOUD_CUE_MAX_WIDTH)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            CueArrow(
                phase = phase,
                color = V2Palette.accentCloud.stroke,
                modifier = Modifier.absolutePadding(
                    top = 4.dp,
                    left = if (onRight) 0.dp else arrowInset,
                    right = if (onRight) arrowInset else 0.dp
                )
            )
        }
    }
}

/** Etiqueta de la señal: texto fuerte sobre el tinte de su acento, legible sobre cualquier fondo. */
@Composable
private fun CueLabel(text: String, accent: AccentPair, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = InteriorTypeVoice.bodyStrong,
        color = accent.stroke,
        modifier = modifier
            .clip(V2Corner.control)
            .background(accent.tint)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

/** La flecha de la barra apunta al tramo donde se escribe, no al borde. */
private val BAR_ARROW_INSET = 24.dp

/** Aire entre la flecha y la nube. */
private val CLOUD_CUE_GAP = 6.dp

/** La etiqueta de la nube no cruza toda la pantalla: se lee junto a la esquina. */
private val CLOUD_CUE_MAX_WIDTH = 260.dp

/** Aire mínimo entre la etiqueta de la nube y la orilla opuesta de la pantalla. */
private val CLOUD_CUE_ROOM = 24.dp

/**
 * Réplica visual de la barra V2 de la fachada (v2-01): la práctica se ve igual
 * que el uso real, por eso usa el icono meteorológico de búsqueda y no la
 * librería interna. Lleva el mismo contorno gris que la barra real muestra sobre
 * fondo plano (aquí siempre lo está): la píldora blanca sola daba 1.12:1 sobre
 * el lila, bajo el 3:1 de WCAG §1.4.11. Con [haloPhase] lleva el halo de la señal
 * (paso 1 de la práctica); el halo se dibuja por fuera y no cambia su tamaño.
 */
@Composable
private fun PracticeSearchBar(value: String, onValueChange: (String) -> Unit, haloPhase: State<Float>?) {
    val focusManager = LocalFocusManager.current
    SecureTextEntry(hideSuggestions = true) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (haloPhase == null) {
                        Modifier
                    } else {
                        Modifier.pulseHalo(haloPhase, V2Palette.accentRecognize.stroke, cornerRadius = BAR_HALO_RADIUS)
                    }
                )
                .border(width = 1.dp, color = V2Palette.controlOutline, shape = V2Corner.pill),
            textStyle = InteriorTypeVoice.body.copy(color = V2Palette.inkPrimary),
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_weather_search),
                    contentDescription = null,
                    tint = V2Palette.inkSecondary
                )
            },
            singleLine = true,
            // Como en la barra real: sin autocorrección. Que el IME no aprenda la
            // clave tecleada aquí NO lo consigue esta bandera —solo retira la
            // autocorrección, medido con `dumpsys input_method`— sino el envoltorio
            // `SecureTextEntry` de alrededor (RF-14, P-11, H-7/P-94).
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search
            ),
            // Igual que la barra real: «Buscar» solo cierra el teclado.
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = V2Corner.pill,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = V2Palette.cardSurface,
                unfocusedContainerColor = V2Palette.cardSurface,
                focusedTextColor = V2Palette.inkPrimary,
                unfocusedTextColor = V2Palette.inkPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = V2Palette.inkPrimary
            )
        )
    }
}

/** Radio del halo de la barra: el de su píldora (56 dp de alto). */
private val BAR_HALO_RADIUS = 28.dp

/**
 * Ingreso de práctica logrado: visto en el verde de «listo» (regla 1). Ocupa el
 * lugar de la etiqueta de la barra; su texto dice cómo se entra.
 */
@Composable
private fun PracticeSuccessCard(modifier: Modifier = Modifier) {
    SoftCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(V2Icons.synced),
                contentDescription = null,
                tint = V2Palette.accentCare.stroke,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.access_guide_practice_done),
                style = InteriorTypeVoice.body,
                color = V2Palette.inkPrimary
            )
        }
    }
}

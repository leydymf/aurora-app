package com.auroraclima.app.access.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalCloudAnchor
import com.auroraclima.app.core.security.SecureTextEntry
import com.auroraclima.app.core.security.reserveCloudLane
import com.auroraclima.app.core.ui.v2.FacadeConditionsSummary
import com.auroraclima.app.core.ui.v2.FacadePinIcon
import com.auroraclima.app.core.ui.v2.FacadeSearchIcon
import com.auroraclima.app.core.ui.v2.FacadeSearchPlaceholder
import com.auroraclima.app.core.ui.v2.FacadeSky
import com.auroraclima.app.core.ui.v2.FacadeTypeVoice
import com.auroraclima.app.core.ui.v2.GuideCorner
import com.auroraclima.app.core.ui.v2.GuidePalette
import com.auroraclima.app.core.ui.v2.GuideSurface
import com.auroraclima.app.core.ui.v2.GuideTypeVoice
import com.auroraclima.app.core.ui.v2.HandDrawnCue
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.WindowFrame
import com.auroraclima.app.core.ui.v2.facadeSearchFieldColors
import com.auroraclima.app.core.ui.v2.facadeSearchKeyboardOptions
import com.auroraclima.app.core.ui.v2.keyboardAware
import com.auroraclima.app.core.ui.v2.rememberWindowFrame
import com.auroraclima.app.core.ui.v2.trackWindowFrame

/**
 * Parte 1 de la guía, «Entrar» (RF-10): la tarjeta y, debajo, una réplica de la
 * pantalla de clima dentro de un marco redondeado, con un círculo a mano alrededor de
 * su barra y una flecha con rizo que sale de la tarjeta. La barra valida el código de
 * acceso sin abrir nada (la lógica es del ViewModel) tras la pausa o al pulsar
 * «Buscar»; si no coincide, lo dice justo debajo de ella. Al coincidir, la barra
 * muestra el éxito, la tarjeta pasa a la confirmación, se cierra el teclado y la guía
 * sigue sola a Inicio.
 *
 * Pantalla completa sobre el fondo de la guía: tapa la app y se traga sus toques. La
 * zona que se desplaza termina antes de la nube también con el teclado abierto (la
 * nube del panel va más alta por la barra de pestañas, así que la franja sale de ella:
 * `reserveCloudLane`).
 */
@Composable
internal fun TypeInScreen(state: WalkthroughUiState, actions: WalkthroughActions, motion: WalkthroughMotion) {
    val window = rememberWindowFrame()
    var card by remember { mutableStateOf<Rect?>(null) }
    var bar by remember { mutableStateOf<Rect?>(null) }
    val matched = state.practiceStatus == PracticeStatus.MATCHED
    val focusManager = LocalFocusManager.current
    val scroll = rememberScrollState()
    // Al coincidir se cierra el teclado y la pantalla vuelve arriba: la confirmación
    // está en la tarjeta, que el teclado había sacado de la vista. De un salto: animado,
    // el círculo a mano iba un cuadro detrás de la barra y se veía sobre la tarjeta.
    LaunchedEffect(matched) {
        if (matched) {
            focusManager.clearFocus()
            scroll.scrollTo(0)
        }
    }
    GuideSurface(modifier = Modifier.trackWindowFrame(window)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .keyboardAware()
                .reserveCloudLane()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            WalkthroughStepCard(
                step = WalkthroughStep.TYPE_IN,
                actions = actions,
                modifier = Modifier.onGloballyPositioned { card = it.boundsInWindow() },
                // La pantalla entera ya se desplaza.
                scrollable = false,
                confirmed = matched
            )
            Spacer(modifier = Modifier.height(ARROW_ROOM))
            PracticeWeatherSample(state = state, actions = actions, onBarPlaced = { bar = it })
        }
        PracticeCue(window = window, card = card, bar = bar, motion = motion)
    }
}

/**
 * El círculo a mano alrededor de la barra de la réplica y la flecha con rizo que sale de
 * la tarjeta ([card] y [bar] en coordenadas de la ventana).
 */
@Composable
private fun PracticeCue(window: WindowFrame, card: Rect?, bar: Rect?, motion: WalkthroughMotion) {
    val cloud = LocalCloudAnchor.current?.bounds
    // Con el teclado abierto la tarjeta puede quedar bajo la barra de estado: sin su
    // borde a la vista, la flecha saldría de lo alto de la pantalla; queda el círculo.
    val statusBar = WindowInsets.statusBars.getTop(LocalDensity.current)
    HandDrawnCue(
        target = bar?.let(window::toLocal),
        card = card?.takeIf { it.bottom > statusBar }?.let(window::toLocal),
        avoid = listOfNotNull(cloud?.let(window::toLocal)),
        color = GuidePalette.strokeOnLight,
        draw = motion.draw,
        breath = motion.breath,
        key = WalkthroughStep.TYPE_IN,
        area = Rect(0f, 0f, window.size.width.toFloat(), window.size.height.toFloat()),
        // El círculo cae sobre el cielo oscuro de la réplica y la flecha sobre el
        // lavanda: el halo blanco hace legible el mismo trazo sobre los dos.
        halo = V2Palette.cardSurface
    )
}

/**
 * Réplica de la pantalla de clima (v2-01) con las piezas de la fachada
 * (`core/ui/v2/FacadeSky`) y datos fijos de ejemplo, sin red: el cielo, la barra con
 * su texto de ayuda y la ciudad con temperatura y condición. Va en un marco con
 * esquinas redondeadas para que se lea como una muestra y no como la app. Solo es
 * visual: no comparte estado con la barra real ni abre nada; lo único que responde es
 * su barra, que escribe en el estado de la práctica. [onBarPlaced] recibe dónde quedó la
 * barra en la ventana, para el círculo y la flecha.
 */
@Composable
private fun PracticeWeatherSample(state: WalkthroughUiState, actions: WalkthroughActions, onBarPlaced: (Rect) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GuideCorner.card)
            .background(FacadeSky.brush)
            .padding(horizontal = SAMPLE_SIDE)
    ) {
        Spacer(modifier = Modifier.height(SAMPLE_TOP))
        PracticeSearchBar(
            value = state.practiceInput,
            matched = state.practiceStatus == PracticeStatus.MATCHED,
            onValueChange = actions.onPracticeInputChange,
            onSearch = actions.onPracticeSubmit,
            modifier = Modifier.onGloballyPositioned { onBarPlaced(it.boundsInWindow()) }
        )
        // Aire bajo la barra para la parte baja del círculo; en la fachada son 28 dp.
        Spacer(modifier = Modifier.height(CIRCLE_BELOW))
        if (state.practiceStatus == PracticeStatus.MISMATCH) {
            PracticeMismatchNotice()
            Spacer(modifier = Modifier.height(12.dp))
        }
        FacadeConditionsSummary(
            city = stringResource(R.string.access_walk_sample_city),
            temperature = stringResource(R.string.access_walk_sample_temperature),
            condition = stringResource(R.string.weather_condition_partly_cloudy)
        )
        Spacer(modifier = Modifier.height(SAMPLE_BOTTOM))
    }
}

/**
 * «Ese no es tu código de acceso. Inténtalo de nuevo.», justo debajo de la barra: una
 * pastilla blanca con texto oscuro, legible sobre el cielo. Región viva: TalkBack la
 * anuncia al aparecer; desaparece al volver a escribir. Al aparecer se trae a la vista:
 * con el teclado abierto, la barra queda al pie de lo visible y el aviso, debajo.
 * `BringIntoViewRequester` sigue experimental en foundation 1.7.8 (BOM fijado), como
 * en `SpotlightTargets`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PracticeMismatchNotice() {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(requester) { requester.bringIntoView() }
    Text(
        text = stringResource(R.string.access_walk_practice_retry),
        style = GuideTypeVoice.action,
        color = GuidePalette.ink,
        modifier = Modifier
            .fillMaxWidth()
            // Antes del relleno: se trae a la vista la pastilla entera, no solo el texto.
            .bringIntoViewRequester(requester)
            .clip(GuideCorner.button)
            .background(V2Palette.cardSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/**
 * La barra de la fachada en reposo (v2-01): la misma píldora blanca, la lupa, el texto
 * de ayuda y el marcador, sin contorno porque va sobre el cielo, como en la fachada.
 * «Buscar» valida en el acto. Coincidido, lleva un contorno y una marca de logrado en
 * lugar del marcador.
 */
@Composable
private fun PracticeSearchBar(
    value: String,
    matched: Boolean,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val success = if (matched) {
        Modifier.border(width = 2.dp, color = V2Palette.accentCare.stroke, shape = V2Corner.pill)
    } else {
        Modifier
    }
    SecureTextEntry(hideSuggestions = true) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
                .then(success),
            textStyle = FacadeTypeVoice.body,
            placeholder = { FacadeSearchPlaceholder() },
            leadingIcon = { FacadeSearchIcon() },
            trailingIcon = { PracticeTrailingIcon(matched) },
            singleLine = true,
            // El teclado de la barra real; que no aprenda el código lo consigue el
            // `SecureTextEntry` de alrededor, no estas opciones (RF-14, P-11, H-7/P-94).
            keyboardOptions = facadeSearchKeyboardOptions,
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            shape = V2Corner.pill,
            colors = facadeSearchFieldColors()
        )
    }
}

/** Al final de la barra, el marcador de la fachada o, coincidido, la marca de logrado. */
@Composable
private fun PracticeTrailingIcon(matched: Boolean) {
    if (matched) {
        Icon(
            painter = painterResource(V2Icons.synced),
            contentDescription = null,
            tint = V2Palette.accentCare.stroke,
            modifier = Modifier.size(20.dp)
        )
    } else {
        FacadePinIcon()
    }
}

/** Alto libre entre la tarjeta y la réplica: ahí van la flecha y la parte alta del círculo. */
private val ARROW_ROOM = 72.dp

/** Lados de la réplica, los de la fachada: el círculo rodea la barra dentro del marco. */
private val SAMPLE_SIDE = 20.dp

/** Cielo sobre la barra: el círculo la rodea sin salirse mucho del marco. */
private val SAMPLE_TOP = 16.dp

/** Aire bajo la barra para la parte baja del círculo. */
private val CIRCLE_BELOW = 22.dp

/** Cielo bajo la condición. */
private val SAMPLE_BOTTOM = 12.dp

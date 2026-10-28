package com.auroraclima.app.access.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.LocalCloudAnchor
import com.auroraclima.app.core.security.SecureTextEntry
import com.auroraclima.app.core.security.reserveCloudLane
import com.auroraclima.app.core.ui.v2.GuidePalette
import com.auroraclima.app.core.ui.v2.GuideSurface
import com.auroraclima.app.core.ui.v2.GuideTypeVoice
import com.auroraclima.app.core.ui.v2.HandDrawnCue
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.keyboardAware
import com.auroraclima.app.core.ui.v2.rememberWindowFrame
import com.auroraclima.app.core.ui.v2.trackWindowFrame

/**
 * Parte 1 de la guía, «Entrar» (RF-10): la tarjeta y, debajo, una barra igual a la de
 * la fachada con un círculo a mano alrededor y una flecha con rizo que sale de la
 * tarjeta. La barra valida el código de acceso sin abrir nada (la lógica es del
 * ViewModel); al coincidir, la confirmación se ve un momento, se cierra el teclado y
 * la guía pasa sola a Inicio.
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
    LaunchedEffect(matched) {
        if (matched) focusManager.clearFocus()
    }
    GuideSurface(modifier = Modifier.trackWindowFrame(window)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .keyboardAware()
                .reserveCloudLane()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            WalkthroughStepCard(
                step = WalkthroughStep.TYPE_IN,
                actions = actions,
                modifier = Modifier.onGloballyPositioned { card = it.boundsInWindow() }
            )
            Spacer(modifier = Modifier.height(ARROW_ROOM))
            PracticeSearchBar(
                value = state.practiceInput,
                onValueChange = actions.onPracticeInputChange,
                modifier = Modifier
                    .padding(horizontal = CIRCLE_ROOM)
                    .onGloballyPositioned { bar = it.boundsInWindow() }
            )
            Spacer(modifier = Modifier.height(CIRCLE_BELOW))
            PracticeStatusText(state.practiceStatus)
        }
        val cloud = LocalCloudAnchor.current?.bounds
        HandDrawnCue(
            target = bar?.let(window::toLocal),
            card = card?.let(window::toLocal),
            avoid = listOfNotNull(cloud?.let(window::toLocal)),
            color = GuidePalette.strokeOnLight,
            draw = motion.draw,
            breath = motion.breath,
            key = WalkthroughStep.TYPE_IN
        )
    }
}

/**
 * Bajo la barra: la confirmación al coincidir (región viva: TalkBack la anuncia) o la
 * ayuda si el texto no coincide.
 */
@Composable
private fun PracticeStatusText(status: PracticeStatus) {
    when (status) {
        PracticeStatus.MATCHED -> Text(
            text = stringResource(R.string.access_guide_practice_done),
            style = GuideTypeVoice.action,
            color = GuidePalette.ink,
            modifier = Modifier
                .padding(horizontal = CIRCLE_ROOM)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        PracticeStatus.MISMATCH -> Text(
            text = stringResource(R.string.access_guide_practice_retry),
            style = GuideTypeVoice.body,
            color = GuidePalette.inkSoft,
            modifier = Modifier.padding(horizontal = CIRCLE_ROOM)
        )
        PracticeStatus.TYPING -> Box(modifier = Modifier.height(0.dp))
    }
}

/**
 * Réplica visual de la barra V2 de la fachada (v2-01): la práctica se ve igual que el
 * uso real, por eso usa el icono meteorológico de búsqueda y no la librería interna.
 * Lleva el contorno gris que la barra real muestra sobre fondo plano (la píldora
 * blanca sola no alcanza el 3:1 de WCAG §1.4.11 sobre el lavanda).
 */
@Composable
private fun PracticeSearchBar(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    SecureTextEntry(hideSuggestions = true) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
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
            // Como en la barra real: sin autocorrección. Que el IME no aprenda el
            // código tecleado aquí NO lo consigue esta bandera —solo retira la
            // autocorrección— sino el envoltorio `SecureTextEntry` de alrededor
            // (RF-14, P-11, H-7/P-94).
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

/** Alto libre entre la tarjeta y la barra: ahí van la flecha y la parte alta del círculo. */
private val ARROW_ROOM = 104.dp

/** Margen a los lados de la barra: el círculo la rodea sin salirse de la pantalla. */
private val CIRCLE_ROOM = 24.dp

/** Aire bajo la barra para la parte baja del círculo. */
private val CIRCLE_BELOW = 30.dp

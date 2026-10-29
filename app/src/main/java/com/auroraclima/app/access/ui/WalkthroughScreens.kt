package com.auroraclima.app.access.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.security.reserveCloudLane
import com.auroraclima.app.core.ui.v2.GuideAction
import com.auroraclima.app.core.ui.v2.GuideCard
import com.auroraclima.app.core.ui.v2.GuidePalette
import com.auroraclima.app.core.ui.v2.GuidePrimaryButton
import com.auroraclima.app.core.ui.v2.GuideSurface
import com.auroraclima.app.core.ui.v2.GuideTextButton
import com.auroraclima.app.core.ui.v2.GuideTypeVoice

/**
 * «Conoce Aurora» (pantalla 0): título, «3 pasos. 2 minutos.», las tres partes
 * numeradas y «Empezar» (lleno) o «Ahora no» (texto, a Inicio sin guía).
 */
@Composable
internal fun OverviewScreen(onStart: () -> Unit, onNotNow: () -> Unit) {
    GuideScreen {
        ScreenTitle(title = stringResource(R.string.access_walk_intro_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.access_walk_intro_sub),
            style = GuideTypeVoice.partLabel,
            color = GuidePalette.label
        )
        Spacer(modifier = Modifier.height(28.dp))
        GuideListRow(lead = "1", title = R.string.access_walk_part_enter, body = R.string.access_walk_intro_enter)
        GuideListRow(lead = "2", title = R.string.access_walk_part_space, body = R.string.access_walk_intro_space)
        GuideListRow(lead = "3", title = R.string.access_walk_part_exit, body = R.string.access_walk_intro_exit)
        Spacer(modifier = Modifier.height(28.dp))
        GuidePrimaryButton(
            text = stringResource(R.string.access_walk_start),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        GuideTextButton(
            action = GuideAction(stringResource(R.string.access_walk_not_now), onNotNow),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * «¡Listo!» (pantalla 4): tras la salida real, «Saliste y volviste. Así de fácil.»;
 * si se llegó con «Después» o «Saltar» ([reachedEarly]), «Para recordar:». Los
 * cuatro recordatorios y «Ir a mi espacio».
 */
@Composable
internal fun DoneScreen(reachedEarly: Boolean, onFinish: () -> Unit) {
    GuideScreen {
        ScreenTitle(title = stringResource(R.string.access_walk_done_title))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(
                if (reachedEarly) R.string.access_walk_done_early else R.string.access_walk_done_back
            ),
            style = GuideTypeVoice.partLabel,
            color = GuidePalette.label
        )
        Spacer(modifier = Modifier.height(28.dp))
        GuideListRow(lead = "›", title = R.string.access_walk_part_enter, body = R.string.access_walk_recall_enter)
        GuideListRow(lead = "›", title = R.string.access_walk_part_exit, body = R.string.access_walk_recall_exit)
        GuideListRow(lead = "›", title = R.string.access_walk_part_recall, body = R.string.access_walk_recall_review)
        GuideListRow(
            lead = "›",
            title = R.string.access_walk_part_discreet,
            body = R.string.access_walk_recall_discreet
        )
        Spacer(modifier = Modifier.height(28.dp))
        GuidePrimaryButton(
            text = stringResource(R.string.access_walk_finish),
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Pantalla completa de la guía: tapa la app, se desplaza si la letra es grande y
 * termina antes de la nube, así que ningún botón queda debajo de ella.
 */
@Composable
internal fun GuideScreen(content: @Composable ColumnScope.() -> Unit) {
    GuideSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .reserveCloudLane()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp)
                .widthIn(max = SCREEN_MAX_WIDTH),
            content = content
        )
    }
}

@Composable
private fun ScreenTitle(title: String) {
    Text(
        text = title,
        style = GuideTypeVoice.screenTitle,
        color = GuidePalette.ink,
        modifier = Modifier.semantics { heading() }
    )
}

/** Una fila de lista: el número (o la marca) a mano, el nombre a mano y la frase. */
@Composable
private fun GuideListRow(lead: String, @StringRes title: Int, @StringRes body: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = lead,
            style = GuideTypeVoice.number,
            color = GuidePalette.label,
            modifier = Modifier.width(LEAD_WIDTH)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(title), style = GuideTypeVoice.itemTitle, color = GuidePalette.ink)
            Text(text = stringResource(body), style = GuideTypeVoice.body, color = GuidePalette.inkSoft)
        }
    }
}

/**
 * La tarjeta de un paso de las partes 1 a 3 con sus controles: «Saltar» arriba;
 * «Atrás» donde hay a dónde volver; «Siguiente» donde hay siguiente; en la parte 3,
 * «Después» en lugar de «Siguiente», porque ahí la acción es tocar la nube. Con
 * [confirmed] (la práctica de la parte 1 coincidió), la frase pasa a la confirmación;
 * la región viva de la tarjeta la anuncia.
 */
@Composable
internal fun WalkthroughStepCard(
    step: WalkthroughStep,
    actions: WalkthroughActions,
    modifier: Modifier = Modifier,
    actionsEndInset: Dp = 0.dp,
    scrollable: Boolean = true,
    confirmed: Boolean = false
) {
    GuideCard(
        tag = stepTag(step),
        title = stringResource(step.titleRes),
        body = stringResource(if (confirmed) R.string.access_walk_practice_done else step.bodyRes),
        modifier = modifier,
        note = if (step == WalkthroughStep.CLOUD) stringResource(R.string.access_walk_cloud_idle) else null,
        primary = step.next?.let { GuideAction(stringResource(R.string.access_walk_next), actions.onNext) },
        back = step.previous?.let { GuideAction(stringResource(R.string.access_walk_back), actions.onBack) },
        secondary = if (step == WalkthroughStep.CLOUD) {
            GuideAction(stringResource(R.string.access_walk_later), actions.onFinishEarly)
        } else {
            null
        },
        skip = GuideAction(stringResource(R.string.access_walk_skip), actions.onFinishEarly),
        actionsEndInset = actionsEndInset,
        scrollable = scrollable
    )
}

/** «1/3 · Entrar», «2/3 · Tu espacio · N de 6» o «3/3 · Salir». */
@Composable
internal fun stepTag(step: WalkthroughStep): String = when (step.part) {
    1 -> stringResource(R.string.access_walk_tag_enter)
    WalkthroughStep.SPACE_PART -> stringResource(
        R.string.access_walk_tag_space,
        step.numberInSpace,
        WalkthroughStep.spaceCount
    )
    else -> stringResource(R.string.access_walk_tag_exit)
}

private val LEAD_WIDTH = 36.dp
private val SCREEN_MAX_WIDTH = 560.dp

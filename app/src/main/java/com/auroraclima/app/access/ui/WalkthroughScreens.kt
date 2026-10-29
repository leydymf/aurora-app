package com.auroraclima.app.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.CloudLaneBar
import com.auroraclima.app.core.ui.v2.CloudLaneStack
import com.auroraclima.app.core.ui.v2.GuideAction
import com.auroraclima.app.core.ui.v2.GuideCard
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.SoftCard
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2PrimaryButton
import com.auroraclima.app.core.ui.v2.blockTouchesBelow

/**
 * «Conoce Aurora» (pantalla 0): título, cuántos pasos y cuánto dura, los tres
 * momentos (sin números: la numeración es la de los pasos) y «Empezar» (lleno) o
 * «Ahora no» (texto, a Inicio sin guía). No se superpone a ninguna pantalla, así que
 * se ve como el interior; el estilo propio de la guía queda en las tarjetas.
 */
@Composable
internal fun OverviewScreen(onStart: () -> Unit, onNotNow: () -> Unit) {
    InteriorGuideScreen(
        title = stringResource(R.string.access_walk_intro_title),
        subtitle = stringResource(R.string.access_walk_intro_sub),
        actions = {
            CloudLaneStack(
                first = { V2PrimaryButton(text = stringResource(R.string.access_walk_start), onClick = onStart) },
                second = {
                    TextButton(onClick = onNotNow) {
                        Text(
                            text = stringResource(R.string.access_walk_not_now),
                            style = InteriorTypeVoice.bodyStrong,
                            color = V2Palette.inkSecondary
                        )
                    }
                }
            )
        }
    ) {
        SoftCard {
            IconListRow(
                iconRes = V2Icons.code,
                accent = V2Palette.accentRecognize,
                title = stringResource(R.string.access_walk_part_enter),
                supportingText = stringResource(R.string.access_walk_intro_enter)
            )
            IconListRow(
                iconRes = V2Icons.home,
                accent = V2Palette.accentGroup,
                title = stringResource(R.string.access_walk_part_space),
                supportingText = stringResource(R.string.access_walk_intro_space)
            )
            IconListRow(
                iconRes = V2Icons.cloud,
                accent = V2Palette.accentCloud,
                title = stringResource(R.string.access_walk_part_exit),
                supportingText = stringResource(R.string.access_walk_intro_exit)
            )
        }
    }
}

/**
 * «¡Listo!» (pantalla 4): tras la salida real, «Saliste y volviste. Así de fácil.»;
 * si se llegó con «Después» o «Saltar» ([reachedEarly]), «Para recordar:». Los
 * cuatro recordatorios y «Ir a mi espacio», con el estilo del interior.
 */
@Composable
internal fun DoneScreen(reachedEarly: Boolean, onFinish: () -> Unit) {
    InteriorGuideScreen(
        title = stringResource(R.string.access_walk_done_title),
        subtitle = stringResource(
            if (reachedEarly) R.string.access_walk_done_early else R.string.access_walk_done_back
        ),
        actions = {
            CloudLaneBar {
                V2PrimaryButton(text = stringResource(R.string.access_walk_finish), onClick = onFinish)
            }
        }
    ) {
        SoftCard {
            IconListRow(
                iconRes = V2Icons.code,
                accent = V2Palette.accentRecognize,
                title = stringResource(R.string.access_walk_part_enter),
                supportingText = stringResource(R.string.access_walk_recall_enter)
            )
            IconListRow(
                iconRes = V2Icons.cloud,
                accent = V2Palette.accentCloud,
                title = stringResource(R.string.access_walk_part_exit),
                supportingText = stringResource(R.string.access_walk_recall_exit)
            )
            IconListRow(
                iconRes = V2Icons.settings,
                accent = V2Palette.accentGroup,
                title = stringResource(R.string.access_walk_part_recall),
                supportingText = stringResource(R.string.access_walk_recall_review)
            )
            IconListRow(
                iconRes = V2Icons.info,
                accent = V2Palette.accentGroup,
                title = stringResource(R.string.access_walk_part_discreet),
                supportingText = stringResource(R.string.access_walk_recall_discreet)
            )
        }
    }
}

/**
 * Pantalla completa de la guía con el aspecto del interior: tapa la app y se traga
 * sus toques; el contenido se desplaza si la letra es grande y [actions] quedan fijas
 * abajo, en el carril de la nube, así que siempre están a la vista y la nube no las tapa.
 */
@Composable
private fun InteriorGuideScreen(
    title: String,
    subtitle: String,
    actions: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(V2Palette.screenBackground)
            .blockTouchesBelow()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp)
            ) {
                Text(
                    text = title,
                    style = InteriorTypeVoice.screenTitle,
                    color = V2Palette.inkPrimary,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = subtitle, style = InteriorTypeVoice.body, color = V2Palette.inkSecondary)
                Spacer(modifier = Modifier.height(20.dp))
                content()
            }
            actions()
        }
    }
}

/**
 * La tarjeta de uno de los ocho pasos con sus controles: «Paso N de 8» y «Saltar»
 * arriba; «Atrás» donde hay a dónde volver; «Siguiente» donde hay siguiente; en el
 * último, «Después» en lugar de «Siguiente», porque ahí la acción es tocar la nube. Con
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

/** «Paso N de 8»: una sola numeración, la de las tarjetas. */
@Composable
internal fun stepTag(step: WalkthroughStep): String =
    stringResource(R.string.common_step_label, step.number, WalkthroughStep.cardCount)

package com.auroraclima.app.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.auroraclima.app.core.security.LocalCloudAnchor
import com.auroraclima.app.core.ui.v2.GuidePalette
import com.auroraclima.app.core.ui.v2.LocalSpotlightTargets
import com.auroraclima.app.core.ui.v2.SpotlightLayer
import com.auroraclima.app.core.ui.v2.SpotlightStyle

/**
 * Partes 2 y 3 de la guía, sobre el Inicio real: la capa oscura con un hueco de luz
 * sobre cada elemento —las dos pestañas, las dos tarjetas, Acciones rápidas y el
 * engranaje— y, en la parte 3, sobre la nube. El hueco se desliza de uno al siguiente,
 * la tarjeta aparece con un fundido corto, y el círculo a mano y la flecha con rizo se
 * trazan en el lila claro de la capa oscura.
 *
 * En cada paso primero desplaza lo necesario para que el elemento se vea entero (con
 * letra grande puede estar fuera) y, si se puede, con sitio debajo para la tarjeta; y
 * después muestra la tarjeta. Mientras tanto el hueco lo sigue. La nube no se ilumina
 * con un objetivo propio: su sitio lo da el grafo seguro ([LocalCloudAnchor]), y queda
 * por encima de la capa en todos los pasos.
 */
@Composable
internal fun HomeScene(step: WalkthroughStep, actions: WalkthroughActions, motion: WalkthroughMotion) {
    val targets = LocalSpotlightTargets.current
    val cloud = LocalCloudAnchor.current?.bounds
    var revealed by remember { mutableStateOf<WalkthroughStep?>(null) }
    val roomForCard = with(LocalDensity.current) { CARD_ROOM.toPx() }
    LaunchedEffect(step) {
        if (step != WalkthroughStep.CLOUD) targets?.bringIntoView(step, roomBelow = roomForCard)
        revealed = step
    }
    // Un elemento fuera de la vista mide cero (sus límites vienen recortados): mientras
    // se trae a la vista, el hueco se queda donde estaba en lugar de irse a una esquina.
    val bounds = (if (step == WalkthroughStep.CLOUD) cloud else targets?.boundsOf(step))?.takeUnless { it.isEmpty }
    SpotlightLayer(
        targetBounds = bounds,
        // La nube queda libre: ni la tarjeta baja de ella, ni un botón queda debajo, ni
        // la flecha pasa por encima.
        keepClear = cloud,
        style = SpotlightStyle(
            holeMillis = motion.holeMillis,
            cardMillis = motion.cardMillis,
            draw = motion.draw,
            breath = motion.breath,
            cueColor = GuidePalette.strokeOnScrim
        ),
        stepKey = step,
        showBubble = revealed == step
    ) { actionsEndInset ->
        WalkthroughStepCard(step = step, actions = actions, actionsEndInset = actionsEndInset)
    }
}

/** Sitio que se pide debajo del elemento al traerlo a la vista: el de una tarjeta y su flecha. */
private val CARD_ROOM = 300.dp

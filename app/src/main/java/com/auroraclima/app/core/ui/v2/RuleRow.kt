package com.auroraclima.app.core.ui.v2

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R

/**
 * Renglón de regla en vivo del mockup P06; lo comparten la creación y el cambio
 * del código de acceso (RF-08, RF-09) y las reglas de la contraseña de la cuenta
 * (RF-07, RF-39). Al cumplirse, el icono cambia al visto en círculo y el verde de
 * «listo/cumplido» (regla 1); pendiente, círculo vacío en tinta secundaria.
 * El lector de pantalla recibe un solo elemento, «texto, cumplida» o «texto, pendiente»
 * (el icono y el estilo no se leen), y anuncia el cambio cuando la regla pasa de un estado al otro.
 */
@Composable
fun RuleRow(text: String, satisfied: Boolean, modifier: Modifier = Modifier) {
    val state = stringResource(if (satisfied) R.string.rule_state_met else R.string.rule_state_pending)
    val description = ruleDescription(text, state)
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(
                if (satisfied) V2Icons.synced else R.drawable.ic_rule_pending
            ),
            contentDescription = null,
            tint = if (satisfied) V2Palette.accentCare.stroke else V2Palette.inkSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = if (satisfied) InteriorTypeVoice.bodyStrong else InteriorTypeVoice.body,
            color = if (satisfied) V2Palette.inkPrimary else V2Palette.inkSecondary
        )
    }
}

/** Lo que lee el lector de pantalla de una regla: su texto y su estado, separados por coma. */
internal fun ruleDescription(text: String, state: String): String = "$text, $state"

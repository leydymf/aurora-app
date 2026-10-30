package com.auroraclima.app.access.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.IconListRow
import com.auroraclima.app.core.ui.v2.SoftCard

/**
 * Una fila de texto fijo del interior: icono con su acento fijo (D2), título corto y
 * una frase. La usan las pantallas de solo lectura de `access/ui` («Conoce Aurora»,
 * «¡Listo!» y «Seguridad en Aurora»), como `ConsentPoint` en el consentimiento.
 */
internal class IconNote(
    @DrawableRes val iconRes: Int,
    val accent: AccentPair,
    @StringRes val title: Int,
    @StringRes val body: Int
)

/** Las filas en una tarjeta blanca del interior, separadas por [rowSpacing]. */
@Composable
internal fun IconNoteCard(notes: List<IconNote>, rowSpacing: Dp = 0.dp) {
    SoftCard {
        notes.forEachIndexed { index, note ->
            if (index > 0 && rowSpacing > 0.dp) Spacer(modifier = Modifier.height(rowSpacing))
            IconListRow(
                iconRes = note.iconRes,
                accent = note.accent,
                title = stringResource(note.title),
                supportingText = stringResource(note.body)
            )
        }
    }
}

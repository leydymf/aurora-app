package com.auroraclima.app.records.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment

/**
 * Piezas compartidas de los adjuntos (RF-33; mockups P19/P20): chips cuadrados
 * con el ícono de la familia — el mockup usa marcadores de ícono, no miniaturas,
 * y así ningún contenido se descifra fuera del visor. En el editor cada chip
 * lleva su «×»; en el detalle, tocar abre el visor interno (imagen y PDF).
 */
internal fun AttachmentKind.iconRes(): Int = when (this) {
    AttachmentKind.IMAGE -> R.drawable.ic_media_image
    AttachmentKind.DOCUMENT -> R.drawable.ic_media_file
    AttachmentKind.AUDIO -> R.drawable.ic_media_audio
}

/** ¿Este adjunto tiene visor interno? El audio no reproduce en el MVP (ADR RF-33). */
internal fun AttachmentKind.isViewable(): Boolean = this != AttachmentKind.AUDIO

@Composable
internal fun AttachmentChipGrid(
    attachments: List<JournalAttachment>,
    onOpen: ((JournalAttachment) -> Unit)?,
    onRemove: ((JournalAttachment) -> Unit)?,
    modifier: Modifier = Modifier
) {
    // Rejilla propia por filas (5 chips de 56 dp caben en 360 dp con margen):
    // FlowRow sigue siendo experimental y el máximo es 10 (política RF-33).
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        attachments.chunked(CHIPS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { attachment ->
                    AttachmentChip(
                        attachment = attachment,
                        onOpen = onOpen?.takeIf { attachment.kind.isViewable() },
                        onRemove = onRemove
                    )
                }
            }
        }
    }
}

private const val CHIPS_PER_ROW = 5

@Composable
private fun AttachmentChip(
    attachment: JournalAttachment,
    onOpen: ((JournalAttachment) -> Unit)?,
    onRemove: ((JournalAttachment) -> Unit)?
) {
    Box {
        Surface(
            shape = V2Corner.control,
            color = V2Palette.cardSurface,
            border = BorderStroke(1.dp, V2Palette.controlOutline)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .then(
                        if (onOpen != null) {
                            Modifier.clickable(onClick = { onOpen(attachment) })
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(attachment.kind.iconRes()),
                    contentDescription = if (onOpen != null) stringResource(R.string.journal_media_open) else null,
                    tint = V2Palette.inkSecondary
                )
            }
        }
        if (onRemove != null) {
            val removeLabel = stringResource(R.string.journal_media_remove)
            Surface(
                shape = CircleShape,
                color = V2Palette.inkPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(20.dp)
                    .clickable(onClick = { onRemove(attachment) })
                    .semantics { contentDescription = removeLabel }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "×",
                        style = InteriorTypeVoice.label,
                        color = V2Palette.cardSurface
                    )
                }
            }
        }
    }
}

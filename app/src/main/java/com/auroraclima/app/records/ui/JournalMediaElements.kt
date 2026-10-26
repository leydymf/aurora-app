package com.auroraclima.app.records.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.InteriorTypeVoice
import com.auroraclima.app.core.ui.v2.RowChevron
import com.auroraclima.app.core.ui.v2.V2Corner
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment

/**
 * Piezas compartidas de los adjuntos (RF-33; mockups P19/P20): una fila por
 * adjunto con el ícono de su familia y su nombre genérico («Imagen 1 · 12 oct»,
 * H-6) — el mockup usa marcadores de ícono, no miniaturas, y así ningún contenido
 * se descifra fuera del visor. En el editor cada fila lleva su «×»; en el
 * detalle, tocar abre el visor interno (imagen y PDF). El lector de pantalla
 * anuncia el mismo nombre que se ve.
 */
internal fun AttachmentKind.iconRes(): Int = when (this) {
    AttachmentKind.IMAGE -> R.drawable.ic_media_image
    AttachmentKind.DOCUMENT -> R.drawable.ic_media_file
    AttachmentKind.AUDIO -> R.drawable.ic_media_audio
}

/** ¿Este adjunto tiene visor interno? El audio no reproduce en el MVP (ADR RF-33). */
internal fun AttachmentKind.isViewable(): Boolean = this != AttachmentKind.AUDIO

@Composable
internal fun AttachmentList(
    attachments: List<JournalAttachment>,
    onOpen: ((JournalAttachment) -> Unit)?,
    onRemove: ((JournalAttachment) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val names = remember(attachments) { attachments.names() }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            val name = names.getValue(attachment.id)
            AttachmentRow(
                attachment = attachment,
                name = name.text(),
                onOpen = onOpen?.takeIf { attachment.kind.isViewable() },
                onRemove = onRemove
            )
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: JournalAttachment,
    name: String,
    onOpen: ((JournalAttachment) -> Unit)?,
    onRemove: ((JournalAttachment) -> Unit)?
) {
    val openLabel = stringResource(R.string.journal_media_open)
    Surface(
        shape = V2Corner.control,
        color = V2Palette.cardSurface,
        border = BorderStroke(1.dp, V2Palette.controlOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(start = 14.dp, end = if (onRemove != null) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Un solo nodo para el lector de pantalla: el nombre, igual que en pantalla.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .then(
                        if (onOpen != null) {
                            Modifier.clickable(onClickLabel = openLabel, role = Role.Button) { onOpen(attachment) }
                        } else {
                            Modifier
                        }
                    )
                    .semantics(mergeDescendants = true) { contentDescription = name },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(attachment.kind.iconRes()),
                    contentDescription = null,
                    tint = V2Palette.inkSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = name,
                    style = InteriorTypeVoice.bodyStrong,
                    color = V2Palette.inkPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (onOpen != null) RowChevron()
            }
            if (onRemove != null) {
                RemoveButton(
                    label = stringResource(R.string.journal_media_remove, name),
                    onClick = { onRemove(attachment) }
                )
            }
        }
    }
}

/** «×» de quitar: objetivo táctil de 48 dp con el círculo oscuro de siempre al centro. */
@Composable
private fun RemoveButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = CircleShape, color = V2Palette.inkPrimary, modifier = Modifier.size(22.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "×", style = InteriorTypeVoice.label, color = V2Palette.cardSurface)
            }
        }
    }
}

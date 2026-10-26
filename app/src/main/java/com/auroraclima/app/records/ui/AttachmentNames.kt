package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Nombre genérico de un adjunto (H-6): tipo, número dentro de ese tipo y fecha en
 * que se adjuntó, p. ej. «Imagen 1 · 12 oct». NUNCA el nombre del archivo original:
 * puede revelar información y la app aplica minimización de datos (RNF-04) — de
 * hecho ni siquiera se guarda.
 */
internal data class AttachmentName(val kind: AttachmentKind, val number: Int, val date: LocalDate)

/**
 * Nombre de cada adjunto por id. El número cuenta dentro de su tipo, en el orden en
 * que se adjuntaron (fecha y, a igual instante, id), así que no cambia al quitar uno
 * de otro tipo.
 */
internal fun List<JournalAttachment>.names(zone: ZoneId = ZoneId.systemDefault()): Map<Long, AttachmentName> {
    val nextNumber = mutableMapOf<AttachmentKind, Int>()
    return sortedWith(compareBy({ it.createdAtEpochMillis }, { it.id })).associate { attachment ->
        val number = (nextNumber[attachment.kind] ?: 0) + 1
        nextNumber[attachment.kind] = number
        val date = Instant.ofEpochMilli(attachment.createdAtEpochMillis).atZone(zone).toLocalDate()
        attachment.id to AttachmentName(attachment.kind, number, date)
    }
}

@StringRes
internal fun AttachmentKind.nameRes(): Int = when (this) {
    AttachmentKind.IMAGE -> R.string.journal_media_kind_image
    AttachmentKind.DOCUMENT -> R.string.journal_media_kind_document
    AttachmentKind.AUDIO -> R.string.journal_media_kind_audio
}

/** El texto del nombre, el mismo que ve la vista y que anuncia el lector de pantalla. */
@Composable
internal fun AttachmentName.text(): String = stringResource(
    R.string.journal_media_name,
    stringResource(kind.nameRes()),
    number,
    date.toDayMonthText()
)

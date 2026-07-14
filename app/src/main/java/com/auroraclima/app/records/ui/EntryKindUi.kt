package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.R
import com.auroraclima.app.records.domain.model.EntryKind

/**
 * Etiquetas visibles de los tipos (P19/P18). Claves neutras `journal_*` en
 * strings.xml, sin términos vedados; el riesgo residual de leerlas juntas en
 * resources.arsc queda registrado en PENDIENTES (mismo criterio que P-40).
 */
@StringRes
internal fun EntryKind.labelRes(): Int = when (this) {
    EntryKind.PSYCHOLOGICAL -> R.string.journal_kind_psychological
    EntryKind.PHYSICAL -> R.string.journal_kind_physical
    EntryKind.SEXUAL -> R.string.journal_kind_sexual
    EntryKind.ECONOMIC -> R.string.journal_kind_economic
}

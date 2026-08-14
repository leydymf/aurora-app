package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.ContentVisual
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
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

/**
 * Icono y matiz de cada tipo (P18/P19/P20, mockups v2-20/21/22): ESPEJO del
 * mapeo AUTOMÁTICO por id de la Guía (`topicVisual`, decisión D2 ratificada en
 * S3) — el catálogo local `EntryKind` y los topics t01–t04 nombran los mismos
 * cuatro tipos de la Ley 1257 (regla 1 del anexo). Desde el esquema v2 (ADR
 * 2026-08-14) la Guía puede traer un visual explícito editado en el panel;
 * este espejo local NO lo sigue (records no lee contenido remoto) y la
 * divergencia posible queda registrada como P-68.
 */
internal fun EntryKind.visual(): ContentVisual = when (this) {
    EntryKind.PHYSICAL -> ContentVisual(V2Icons.figure, V2Palette.accentProtection)
    EntryKind.PSYCHOLOGICAL -> ContentVisual(V2Icons.lobes, V2Palette.accentRecognize)
    EntryKind.SEXUAL -> ContentVisual(V2Icons.slashCircle, V2Palette.accentNotice)
    EntryKind.ECONOMIC -> ContentVisual(V2Icons.card, V2Palette.accentJustice)
}

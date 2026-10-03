package com.auroraclima.app.records.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.auroraclima.app.R
import com.auroraclima.app.core.ui.v2.ContentVisual
import com.auroraclima.app.core.ui.v2.neutralContentVisual
import com.auroraclima.app.core.ui.v2.topicVisual
import com.auroraclima.app.records.domain.model.ResolvedKind

/**
 * Icono y matiz del tipo de un registro (P18/P19/P20, mockups v2-20/21/22).
 * Desde el cierre de P-68 ya no hay espejo local: un tipo publicado se pinta
 * con `topicVisual` — EXACTAMENTE lo que la Guía pinta para ese tema, incluida
 * la selección del esquema v2 y su degradación por campo —; un tipo retirado o
 * un registro sin tipo cae al visual neutro de la Guía (ADR 2026-10-03). El
 * nombre visible sale del catálogo o de la instantánea del registro; el único
 * texto del APK es el rótulo neutro `journal_kind_none`.
 */
internal fun ResolvedKind.visual(): ContentVisual =
    if (isPublished) topicVisual(id, icon, accent) else neutralContentVisual()

/** Nombre visible del tipo: el del catálogo o la instantánea; sin ninguno, el rótulo neutro. */
@Composable
internal fun ResolvedKind.labelText(): String = label ?: stringResource(R.string.journal_kind_none)

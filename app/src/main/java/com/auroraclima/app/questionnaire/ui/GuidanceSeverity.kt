package com.auroraclima.app.questionnaire.ui

import com.auroraclima.app.core.ui.v2.AccentPair
import com.auroraclima.app.core.ui.v2.V2Palette

/**
 * Escala cerrada de severidad del resultado orientativo (decisión directora
 * 2026-08-14, reversión informada de P-41): el ORDEN del nivel — nunca su id,
 * que es dato administrable — elige su par semafórico. Un orden fuera de la
 * escala degrada al neutro de grupo, el mismo fallback tolerante del contenido
 * (D2/RF-25): un documento mal configurado jamás rompe la pantalla.
 */
internal fun severityAccent(order: Int): AccentPair = when (order) {
    1 -> V2Palette.accentSeverity1
    2 -> V2Palette.accentSeverity2
    3 -> V2Palette.accentSeverity3
    4 -> V2Palette.accentSeverity4
    else -> V2Palette.accentGroup
}

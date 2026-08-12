package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes

/**
 * Asignación fija de icono y matiz para el contenido administrable (decisión D2
 * de PLAN_UI_V2/README mockups-v2 §4, ratificada 2026-08-12): el esquema actual
 * (`schemaVersion` 1) no trae campo de icono/color, así que la app mapea por id
 * de documento — ids opacos que ya viajan en el tráfico (RNF-05) — hacia la
 * librería cerrada. Todo id no mapeado cae al neutro con el icono de Guía
 * (regla 5 del anexo: lo que agrupa o no tiene acento propio va en neutro).
 * Si algún día el panel quiere elegir icono/color, será con `schemaVersion`
 * nuevo (opción a de D2) — este mapa muere ese día.
 */
data class ContentVisual(@DrawableRes val iconRes: Int, val accent: AccentPair)

/** Tipos de la Guía (v2-12): matiz por significado del tema, aprobado 2026-08-12. */
fun topicVisual(topicId: String): ContentVisual = when (topicId) {
    // Física — daño al cuerpo: azul de protección.
    "t01" -> ContentVisual(V2Icons.figure, V2Palette.accentProtection)
    // Psicológica — la mente: lila de reconocer.
    "t02" -> ContentVisual(V2Icons.lobes, V2Palette.accentRecognize)
    // Sexual — límite vulnerado: rosa de aviso y cuidado.
    "t03" -> ContentVisual(V2Icons.slashCircle, V2Palette.accentNotice)
    // Económica y patrimonial — lo material: arena de justicia.
    "t04" -> ContentVisual(V2Icons.card, V2Palette.accentJustice)
    else -> ContentVisual(V2Icons.guide, V2Palette.accentGroup)
}

/** Sectores de las rutas (v2-14): significados fijos del anexo, sin decisión nueva. */
fun routeVisual(routeId: String): ContentVisual = when (routeId) {
    // Salud — verde de cuidado.
    "r01" -> ContentVisual(V2Icons.health, V2Palette.accentCare)
    // Protección — azul y escudo.
    "r02" -> ContentVisual(V2Icons.protection, V2Palette.accentProtection)
    // Justicia — arena y balanza.
    "r03" -> ContentVisual(V2Icons.justice, V2Palette.accentJustice)
    else -> ContentVisual(V2Icons.place, V2Palette.accentGroup)
}

package com.auroraclima.app.core.ui.v2

import androidx.annotation.DrawableRes

/**
 * Icono y matiz del contenido administrable. Desde el esquema v2 (ADR
 * 2026-08-14, opción a de D2) los documentos pueden traer `icon`/`accent`
 * que SELECCIONAN dentro de la biblioteca cerrada — la biblioteca no crece
 * con datos. La asignación fija por id de documento (decisión D2 original,
 * ratificada 2026-08-12) sobrevive como contrato de degradación: campo
 * ausente, de tipo inesperado o fuera de la biblioteca → mapeo automático,
 * que a su vez cae al neutro con el icono de Guía para ids no mapeados
 * (regla 5 del anexo). Un valor corrupto jamás rompe la pantalla (RF-25).
 */
data class ContentVisual(@DrawableRes val iconRes: Int, val accent: AccentPair)

// Biblioteca seleccionable desde los datos (espejo en el panel:
// admin/src/components/contentVisuals.ts — deben moverse juntos). Los ids de
// dato nombran el DIBUJO o el MATIZ, nunca el significado: estos literales
// quedan en el DEX y deben ser tan inocuos como los nombres de drawable
// (RNF-05). El violeta de la nube (accentCloud) queda fuera a propósito:
// es exclusivo de la salida rápida (anexo v2-29). Visibilidad `internal`
// SOLO para que ContentVisualTest fije esa exclusión estructuralmente.
internal val selectableIcons: Map<String, Int> = mapOf(
    "figure" to V2Icons.figure,
    "lobes" to V2Icons.lobes,
    "slash_circle" to V2Icons.slashCircle,
    "card" to V2Icons.card,
    "device" to V2Icons.device,
    "book" to V2Icons.guide,
    "cross" to V2Icons.health,
    "shield" to V2Icons.protection,
    "scales" to V2Icons.justice,
    "pin" to V2Icons.place
)

internal val selectableAccents: Map<String, AccentPair> = mapOf(
    "blue" to V2Palette.accentProtection,
    "lilac" to V2Palette.accentRecognize,
    "pink" to V2Palette.accentNotice,
    "sand" to V2Palette.accentJustice,
    "green" to V2Palette.accentCare,
    "neutral" to V2Palette.accentGroup
)

/** Tipos de la Guía (v2-12): selección explícita del dato con degradación al matiz por significado. */
fun topicVisual(topicId: String, icon: String? = null, accent: String? = null): ContentVisual =
    defaultTopicVisual(topicId).select(icon, accent)

/** Sectores de las rutas (v2-14): selección explícita del dato con degradación al significado fijo. */
fun routeVisual(routeId: String, icon: String? = null, accent: String? = null): ContentVisual =
    defaultRouteVisual(routeId).select(icon, accent)

// Degradación POR CAMPO (tolerancia estilo RF-25): cada campo desconocido cae
// por separado al mapeo automático — un `icon` válido no se pierde porque el
// `accent` venga corrupto, y viceversa.
private fun ContentVisual.select(icon: String?, accent: String?) = ContentVisual(
    iconRes = icon?.let(selectableIcons::get) ?: iconRes,
    accent = accent?.let(selectableAccents::get) ?: this.accent
)

/** Mapeo automático de tipos (D2 original): matiz por significado del tema, aprobado 2026-08-12. */
private fun defaultTopicVisual(topicId: String): ContentVisual = when (topicId) {
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

/** Mapeo automático de sectores: significados fijos del anexo, sin decisión nueva. */
private fun defaultRouteVisual(routeId: String): ContentVisual = when (routeId) {
    // Salud — verde de cuidado.
    "r01" -> ContentVisual(V2Icons.health, V2Palette.accentCare)
    // Protección — azul y escudo.
    "r02" -> ContentVisual(V2Icons.protection, V2Palette.accentProtection)
    // Justicia — arena y balanza.
    "r03" -> ContentVisual(V2Icons.justice, V2Palette.accentJustice)
    else -> ContentVisual(V2Icons.place, V2Palette.accentGroup)
}

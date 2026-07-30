package com.auroraclima.app.core.network

/**
 * Helpers de parsing con casts seguros sobre el mapa crudo de un documento
 * Firestore: un campo con tipo inesperado queda nulo, igual que un campo
 * ausente, y el mapper decide omitir solo ese documento (RF-25) — los getters
 * tipados del SDK lanzarían y tumbarían el fetch completo del módulo.
 * Extraídos de content/ y questionnaire/ cuando records/ sumó la tercera
 * fuente remota Firestore (P-51; junto a `networkResult`, mismo criterio).
 * El receptor es `Map<*, *>` para servir igual al mapa raíz del documento
 * (`Map<String, Any?>`) y a los submapas embebidos sin tipar.
 */

internal fun Map<*, *>.str(field: String): String? = this[field] as? String

// Solo valores enteros: truncar un decimal alteraría órdenes, versiones,
// pesos o umbrales calibrados sin descartar el documento — un decimal se
// trata como campo corrupto.
internal fun Map<*, *>.num(field: String): Long? =
    (this[field] as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toLong()

internal fun Map<*, *>.strList(field: String): List<String> =
    (this[field] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

package com.auroraclima.app.questionnaire.domain.model

/**
 * Un nivel orientativo de riesgo (documento validado §5.3/§6; niveles de
 * referencia INMLCF). El nivel base es el de mayor [minScore] menor o igual al
 * puntaje total; a igual [minScore] rige el de mayor [order] — ante
 * configuración ambigua se sobre-orienta, nunca al revés.
 *
 * Los accesos directos del resultado (§6) se arman con:
 *  - [lineIds]: líneas nacionales y departamental (feature content), siempre
 *    visibles.
 *  - [agencyIds]: entidades fijas del nivel, no dependen del municipio (p. ej.
 *    Centro Integral de la Mujer, URI de la Fiscalía).
 *  - [municipalAgencyIds]: municipio → entidades de ese municipio (Comisaría de
 *    Familia, urgencias). Se resuelven según el municipio que elija la usuaria.
 *
 * Las referencias a líneas y entidades se resuelven por un contrato de `core`
 * que implementa content (§2): las features no se importan entre sí.
 */
data class GuidanceLevel(
    val id: String,
    val order: Int,
    val minScore: Int,
    val title: String,
    val summary: String,
    val advice: List<String>,
    val lineIds: List<String>,
    val agencyIds: List<String>,
    val municipalAgencyIds: Map<String, List<String>>
)

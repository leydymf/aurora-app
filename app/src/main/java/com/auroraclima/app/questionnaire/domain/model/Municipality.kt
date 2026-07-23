package com.auroraclima.app.questionnaire.domain.model

/**
 * Uno de los municipios de cobertura del piloto (documento validado §6):
 * Bucaramanga, Floridablanca, Girón o Piedecuesta. La usuaria elige el suyo en
 * el resultado para ver, junto a las líneas nacionales, las entidades de su
 * municipio (Comisaría de Familia, urgencias…). El [id] es la clave que usan
 * los accesos de cada nivel ([GuidanceLevel.municipalAgencyIds]).
 */
data class Municipality(
    val id: String,
    val order: Int,
    val label: String
)

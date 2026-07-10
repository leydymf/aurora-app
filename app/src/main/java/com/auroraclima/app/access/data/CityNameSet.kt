package com.auroraclima.app.access.data

import java.text.Normalizer

/**
 * Conjunto de nombres de ciudad con comparación normalizada: minúsculas, sin
 * tildes ni diacríticos y con espacios internos colapsados, para que «Bogotá»,
 * «bogota» y «BOGOTA» cuenten igual (RF-08 criterio 2). Lógica pura separada de
 * la lectura del recurso para probarse por unidad en JVM (mismo criterio de
 * extracción que InteractionThrottle/SecureFlagLedger).
 */
class CityNameSet(names: Sequence<String>) {
    private val normalizedNames: Set<String> = names
        .map { normalize(it) }
        .filter { it.isNotEmpty() }
        .toHashSet()

    val size: Int get() = normalizedNames.size

    operator fun contains(candidate: String): Boolean = normalize(candidate) in normalizedNames

    companion object {
        private val diacritics = Regex("\\p{Mn}+")
        private val whitespace = Regex("\\s+")

        fun normalize(raw: String): String = Normalizer
            .normalize(raw.trim().lowercase(), Normalizer.Form.NFD)
            .replace(diacritics, "")
            .replace(whitespace, " ")
    }
}

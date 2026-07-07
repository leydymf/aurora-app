package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de regresión del camuflaje (RNF-05, regla 6 de CLAUDE.md).
 *
 * - Recursos (XML bajo res/ + manifest): contenido Y nombre de archivo llegan al APK
 *   (resources.arsc), así que se auditan contra la lista amplia en español e inglés.
 * - Fuentes Kotlin: los nombres de clase llegan al DEX (hasta activar R8 en release);
 *   se auditan solo términos que jamás serían identificadores legítimos. Nombres en
 *   inglés previstos por la arquitectura (p. ej. ValidateSecretCodeUseCase) se
 *   protegen con R8/minify (pendiente P-05), no con este guardián.
 */
class CamouflageResourcesTest {
    /** Términos prohibidos en cualquier archivo de src/main, incluido código Kotlin. */
    private val forbiddenEverywhere = listOf(
        "violencia",
        "violence",
        "víctima",
        "victima",
        "victim",
        "agresor",
        "agresión",
        "agresion",
        "abuser",
        "maltrato",
        "denuncia",
        "doble fondo",
        "revictimiz"
    )

    /** Términos prohibidos adicionalmente en recursos y manifest (contenido y nombre de archivo). */
    private val forbiddenInResources = listOf(
        "incidente",
        "incident",
        "evidencia",
        "evidence",
        "emergencia",
        "emergency",
        "auxilio",
        "ayuda",
        "sos",
        "pánico",
        "panico",
        "panic",
        "refugio",
        "shelter",
        "secreto",
        "secret",
        "oculto",
        "oculta",
        "hidden",
        "vault",
        "abuso",
        "abuse",
        "consentimiento",
        "consent",
        "cuestionario",
        "questionnaire",
        "salida rápida",
        "salida rapida",
        "modo seguro"
    )

    @Test
    fun `los recursos y fuentes visibles no revelan el proposito real`() {
        val mainDir = requireNotNull(
            sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
        ) { "No se encontró el directorio src/main" }

        val violations = mainDir
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("xml", "kt") }
            .flatMap { file ->
                val isResource = file.extension == "xml"
                val terms = if (isResource) forbiddenEverywhere + forbiddenInResources else forbiddenEverywhere
                val haystack = file.readText().lowercase() +
                    if (isResource) "\n" + file.name.lowercase() else ""
                terms
                    .filter { it in haystack }
                    .map { term -> "${file.relativeTo(mainDir)}: contiene \"$term\"" }
            }
            .toList()

        assertTrue(
            "Archivos con términos que rompen el camuflaje (RNF-05):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}

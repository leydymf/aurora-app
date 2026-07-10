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
 * - Literales de cadena en Kotlin: R8 NO ofusca literales — llegan al DEX tan
 *   legibles como los recursos al arsc (auditoría D3 accounts H-1). Se barren con
 *   la misma lista amplia que los recursos; mover un texto de strings.xml a una
 *   constante Kotlin no lo protege ni lo saca del alcance de este guardián.
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
        "modo seguro",
        // Describir el mecanismo de entrada en un recurso delataría el doble
        // fondo ante una inspección del APK (auditoría D5 H-1).
        "barra de búsqueda",
        "barra de busqueda",
        // Léxico propio de Aurora (manual de marca §05): distinguidor si se lee
        // en el APK (auditoría D3 accounts H-2).
        "espacio seguro",
        // Nombre canónico en inglés de los botones de escape en apps de violencia
        // doméstica: legible en resources.arsc incluso con R8 (auditoría D3 H-1).
        "quick_exit",
        "quick exit",
        "quickexit",
        "escape"
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

    @Test
    fun `los literales de cadena en codigo no revelan el proposito real`() {
        val mainDir = requireNotNull(
            sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
        ) { "No se encontró el directorio src/main" }
        val stringLiteral = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")

        val violations = mainDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val literals = stringLiteral.findAll(file.readText())
                    .joinToString("\n") { it.value.lowercase() }
                (forbiddenEverywhere + forbiddenInResources)
                    .filter { it in literals }
                    .map { term -> "${file.relativeTo(mainDir)}: literal contiene \"$term\"" }
            }
            .toList()

        assertTrue(
            "Literales de cadena que rompen el camuflaje — quedan legibles en el " +
                "DEX aun con R8 (RNF-05, auditoría D3 H-1):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}

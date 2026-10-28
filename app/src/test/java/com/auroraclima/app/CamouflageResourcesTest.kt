package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de regresión del camuflaje (RNF-05, regla 6 de CLAUDE.md).
 *
 * Los términos NO viven aquí: se leen de `camuflaje-terminos-vedados.txt`, que es
 * la fuente única que comparte con el guardián del DEX (`verificarCamuflajeDex`).
 * Antes había una copia embebida en este archivo y el guardián del DEX leía otra
 * lista con siete términos menos; las dos divergieron en silencio y el guardián
 * del DEX dejó de comprobar justo los más propios del proyecto, entre ellos
 * «quick exit» —que estaba, legible, en el DEX del release (hallazgo H-8)—.
 *
 * Qué barre cada prueba y por qué:
 *
 * - **Recursos** (XML bajo res/ + manifest): contenido Y nombre de archivo llegan
 *   al APK (resources.arsc), así que se auditan contra la lista completa.
 * - **Texto de las fuentes Kotlin**: solo contra el ámbito `[todo]`, porque los
 *   comentarios y el KDoc nombran legítimamente los requerimientos en español
 *   («salida rápida» es el nombre de RF-16) y nada de eso llega al artefacto.
 * - **Literales de cadena en Kotlin**: lista completa. R8 NO ofusca literales —
 *   llegan al DEX tan legibles como los recursos al arsc (auditoría D3 accounts
 *   H-1). Mover un texto de strings.xml a una constante Kotlin no lo protege.
 * - **Identificadores que sobreviven a R8**: lista completa. R8 renombra clases,
 *   pero hay dos formas en que un nombre propio llega legible al DEX igual: la
 *   plantilla de `toString()` que el compilador genera para cada `data class` o
 *   `data object`, y los nombres de las constantes de enum, que R8 conserva
 *   porque `name` y `valueOf` son reflexión. Esa es la categoría que encontraron
 *   H-1 y H-8, y la premisa de que «R8 protege los nombres internos» es falsa
 *   para ella. Los demás nombres de clase sí los protege R8 (verificado en P-05),
 *   y por eso no se barren: `ValidateSecretCodeUseCase` es legítimo.
 */
class CamouflageResourcesTest {
    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    private val terminos: Map<String, List<String>> = run {
        val archivo = requireNotNull(
            sequenceOf(
                File("src/test/resources/camuflaje-terminos-vedados.txt"),
                File("app/src/test/resources/camuflaje-terminos-vedados.txt")
            ).firstOrNull { it.exists() }
        ) { "Falta camuflaje-terminos-vedados.txt, la fuente única de términos vedados" }
        val porAmbito = mutableMapOf<String, MutableList<String>>()
        var ambito = ""
        archivo.readLines().map { it.trim() }.forEach { linea ->
            when {
                linea.isEmpty() || linea.startsWith("#") -> Unit
                linea.startsWith("[") -> ambito = linea.trim('[', ']')
                else -> porAmbito.getOrPut(ambito) { mutableListOf() } += linea
            }
        }
        porAmbito
    }

    /** Prohibidos en cualquier archivo de src/main, código Kotlin incluido. */
    private val forbiddenEverywhere =
        requireNotNull(terminos["todo"]) { "El archivo de términos no declara el ámbito [todo]" }

    /** Prohibidos además en recursos, manifest, DEX e identificadores. */
    private val forbiddenInResources =
        requireNotNull(terminos["recursos"]) { "El archivo de términos no declara el ámbito [recursos]" }

    /**
     * Identificadores que HOY incumplen y se aceptan con motivo, los mismos que la
     * línea base del guardián del DEX: son la deuda de **P-78**, que desaparece con
     * el rename `questionnaire`→`form` y `Consent*`→`Terms*` antes del piloto.
     */
    private val identificadoresAceptados = setOf("Questionnaire", "ConsentPoint", "ConsentUiState")

    /** Largo hasta el que un término se busca como palabra entera en los recursos. */
    private val largoDePalabraCorta = 3

    /**
     * Cómo se busca un término en el texto de un recurso. Los de más de tres letras,
     * como subcadena. Los de tres o menos («sos») solo como palabra: sin una letra
     * pegada a ningún lado, la misma frontera que aplica el guardián del DEX
     * (ADR 2026-10-24). Como subcadena, «sos» cazaba palabras corrientes del español
     * («pasos», «casos», «precisos»), y un texto de la guía no podía decir «3 pasos»
     * (v0.3.4, decisión de la autora). Así «SOS» suelto, «ic_sos» o «btn-sos» se
     * siguen cazando.
     *
     * Solo para el texto de los recursos, que es prosa. Los literales y los
     * identificadores de Kotlin conservan la subcadena: ahí un `sosBoton` sí sería un
     * nombre propio, y es la red que el guardián del DEX declara que lo cubre.
     */
    private fun contieneEnTexto(texto: String, termino: String): Boolean {
        if (termino.length > largoDePalabraCorta) return termino in texto
        var desde = 0
        while (true) {
            val i = texto.indexOf(termino, desde)
            if (i < 0) return false
            val antes = texto.getOrNull(i - 1)
            val despues = texto.getOrNull(i + termino.length)
            if (antes?.isLetter() != true && despues?.isLetter() != true) return true
            desde = i + 1
        }
    }

    @Test
    fun `un termino corto se caza como palabra y no dentro de otra`() {
        assertTrue(contieneEnTexto("toca sos ahora", "sos"))
        assertTrue(contieneEnTexto("ic_sos.xml", "sos"))
        assertTrue(contieneEnTexto("<string>sos</string>", "sos"))
        assertFalse(contieneEnTexto("3 pasos. 2 minutos.", "sos"))
        assertFalse(contieneEnTexto("casos precisos", "sos"))
        // Los largos siguen como subcadena.
        assertTrue(contieneEnTexto("autoayuda", "ayuda"))
    }

    @Test
    fun `los recursos y fuentes visibles no revelan el proposito real`() {
        val violations = mainDir
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("xml", "kt") }
            .flatMap { file ->
                val isResource = file.extension == "xml"
                val terms = if (isResource) forbiddenEverywhere + forbiddenInResources else forbiddenEverywhere
                val haystack = file.readText().lowercase() +
                    if (isResource) "\n" + file.name.lowercase() else ""
                terms
                    .filter { if (isResource) contieneEnTexto(haystack, it) else it in haystack }
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

    @Test
    fun `los identificadores que sobreviven a R8 no revelan el proposito real`() {
        // Las dos formas en que un nombre propio llega LEGIBLE al DEX pese a R8.
        val dataClassODataObject = Regex("""\bdata\s+(?:class|object)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        // Cualquier sangría y también la forma con argumentos de constructor: la
        // primera versión exigía exactamente cuatro espacios y que al nombre le
        // siguiera `,`, `;` o fin de línea, así que `CLOSE_NOW(R.string.x),` y los
        // enum anidados en una clase se le escapaban — formas ordinarias en Kotlin
        // (hallazgo F-4 de la auditoría).
        val constanteDeEnum = Regex("""^[ \t]+([A-Z][A-Z0-9_]{2,})\s*(?:[,;(]|$)""", RegexOption.MULTILINE)

        val fuentes = mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val identificadores = fuentes.flatMap { file ->
            val texto = file.readText()
            val nombres = dataClassODataObject.findAll(texto).map { it.groupValues[1] }.toMutableSet()
            if ("enum class" in texto) {
                nombres += constanteDeEnum.findAll(texto).map { it.groupValues[1] }
            }
            nombres.map { file.relativeTo(mainDir) to it }
        }
        // Que el barrido no sea vacuo: si un refactor cambiara la forma de declarar
        // y el regex dejara de casar, esta prueba pasaría sin mirar nada.
        assertTrue(
            "El barrido de identificadores no encontró nada: el regex dejó de casar.",
            identificadores.size > 100
        )

        val violations = identificadores
            .filterNot { (_, nombre) -> nombre in identificadoresAceptados }
            .flatMap { (ruta, nombre) ->
                (forbiddenEverywhere + forbiddenInResources)
                    .filter { it in nombre.lowercase() }
                    .map { term -> "$ruta: el identificador \"$nombre\" contiene \"$term\"" }
            }

        assertTrue(
            "Identificadores que rompen el camuflaje. R8 NO los protege: la plantilla " +
                "de toString() de una data class y los nombres de las constantes de enum " +
                "llegan legibles al DEX (RNF-05, hallazgos H-1 y H-8):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}

package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de regresión del hallazgo H-7 de la evaluación OWASP (P-94).
 *
 * Lo que se descubrió: el campo del relato era el único campo de texto del espacio
 * interno que no era de contraseña, y por eso su menú de selección ofrecía «copiar»
 * y el teclado podía aprender lo escrito. El dato más sensible de la app podía salir
 * por el portapapeles hacia cualquier otra aplicación.
 *
 * Lo que este guardia fija, para que no vuelva por descuido en un refactor:
 *
 *  1. Ningún campo de texto editable se construye fuera del kit `core/ui/v2`, salvo
 *     las dos barras de búsqueda, que tienen su propia forma y están envueltas.
 *  2. Todo campo del kit que NO enmascare su contenido está envuelto en
 *     `SecureTextEntry`, el envoltorio de `core/security` que quita copiar y cortar
 *     y pide al teclado que no aprenda.
 *  3. El envoltorio sigue haciendo las dos cosas: si alguien borra una de las dos
 *     protecciones, el test cae.
 *
 * **Las aserciones se hacen sobre el CUERPO de cada función, no sobre el texto del
 * archivo.** La primera versión de este guardia buscaba literales en todo el
 * archivo, y una auditoría demostró con mutaciones que se podía deshacer la
 * corrección dejándolo en verde: bastaba vaciar `SecureTextEntry` (la clase
 * `NoCopyTextToolbar` seguía ahí, huérfana, y el `grep` la encontraba) o borrar el
 * flag del IME del código, porque el KDoc lo menciona y eso satisfacía la búsqueda.
 * Lo que no puede fijar ninguna prueba de JVM —que el menú real no ofrezca
 * «copiar»— vive en la comprobación manual de P-94 y en `SecureTextEntryTest` de
 * las instrumentadas, que sí compone el envoltorio y mira lo que recibe la barra.
 */
class SensitiveFieldsGuardTest {
    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    private fun kotlin(ruta: String) = File(mainDir, "java/com/auroraclima/app/$ruta")

    /**
     * Texto del archivo SIN comentarios. Es la pieza que hace que este guardia sirva
     * de algo, y la añadió una verificación que demostró el agujero: bastaba quitar
     * `SecureTextEntry {` del relato y dejar un comentario como
     * `// pendiente: volver a envolver en SecureTextEntry { } al subir a Compose 1.8`
     * para que el guardia siguiera verde con el hallazgo ALTO deshecho. Una línea, y
     * además es el comentario que P-97 y la §10.1 del informe invitan a escribir.
     *
     * La variante con KDoc era igual de mala: al rebanar el cuerpo de un campo sin
     * enmascarar se tragaba el KDoc del campo siguiente, y si ese KDoc mencionaba
     * `KeyboardType.Password` el campo quedaba clasificado como enmascarado y
     * excusado del envoltorio. Esa sobrecaptura ocurría ya hoy, sin consecuencias.
     *
     * Límite conocido: un `//` dentro de un literal de cadena se descontaría también.
     * Ninguno de los archivos que este guardia mira tiene uno.
     */
    private fun codigoDe(file: File): String = file.readText()
        .replace(Regex("""/\*(?:.|\n)*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    /** El único archivo del kit que declara campos editables. */
    private val kit = "core/ui/v2/Fields.kt"

    /** Toda forma de construir un campo de texto editable en Compose. */
    private val constructores = listOf(
        "OutlinedTextField(",
        "BasicTextField(",
        "BasicTextField2(",
        "TextField(",
        "SearchBar(",
        "DockedSearchBar("
    )

    /**
     * La coincidencia exige frontera de identificador por delante. Sin ella,
     * `SearchBar(` casa dentro de `WeatherSearchBar(` y el guardia acusaba a quien
     * solo LLAMA a la barra, no a quien construye el campo.
     */
    private val constructorRegex = Regex(
        "(?<![A-Za-z0-9_])(" + constructores.joinToString("|") { Regex.escape(it) } + ")"
    )

    /**
     * Tokens que, al principio de una línea, abren una declaración nueva y por tanto
     * cierran el cuerpo de la anterior. `val` y `const` están por un motivo concreto:
     * sin ellos, el cuerpo de un campo puede tragarse la declaración siguiente, y si
     * esa fuera un campo enmascarado, el campo SIN enmascarar quedaría clasificado
     * como enmascarado y excusado del envoltorio — un falso negativo en la prueba que
     * vigila el hallazgo ALTO (A-3 de la revisión de arquitectura).
     */
    private val cierreDeCuerpo = Regex(
        "\n(?:" + listOf(
            "@", "fun ", "private ", "internal ", "public ", "class ", "object ",
            "interface ", "sealed ", "data ", "enum ", "val ", "var ", "const ",
            "typealias ", "abstract ", "open ", "inline ", "suspend "
        ).joinToString("|") { Regex.escape(it) } + ")"
    )

    /** Lo que hay en el cuerpo antes de construir el campo; vacío si no hay campo. */
    private fun antesDelCampo(cuerpo: String): String =
        constructorRegex.find(cuerpo)?.let { cuerpo.substring(0, it.range.first) } ?: ""

    /**
     * Las dos barras de búsqueda viven fuera del kit porque su forma es de la fachada
     * (píldora, slots propios), no del espacio interno. Están exentas de la regla 1
     * pero NO de la 2: se comprueban una por una más abajo.
     */
    private val camposFueraDelKit = listOf(
        "weather/ui/WeatherScreen.kt",
        "access/ui/TutorialScreen.kt"
    )

    private fun fuentes(): Sequence<File> = mainDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }

    /**
     * Cuerpo de una declaración de nivel superior: desde su firma hasta la siguiente
     * declaración que empiece en la columna cero. Rebanar es lo que hace que las
     * aserciones hablen de la función y no del archivo entero.
     */
    private fun cuerpoDe(texto: String, firma: String): String {
        val inicio = texto.indexOf(firma)
        assertTrue("No se encontró «$firma»: ¿se renombró o se borró?", inicio >= 0)
        val resto = texto.substring(inicio + firma.length)
        val siguiente = cierreDeCuerpo.find(resto)?.range?.first ?: resto.length
        return resto.substring(0, siguiente)
    }

    /** Funciones de un archivo que construyen un campo de texto, con su firma. */
    private fun funcionesConCampo(texto: String): List<String> =
        Regex("""(?:^|\n)(?:(?:private|internal|public|inline|suspend|operator)\s+)*fun ([A-Za-z0-9_]+)\(""")
            .findAll(texto)
            .map { it.groupValues[1] }
            .filter { nombre -> constructorRegex.containsMatchIn(cuerpoDe(texto, "fun $nombre(")) }
            .toList()

    @Test
    fun `ningun campo de texto editable se crea fuera del kit v2`() {
        // La exención es del ARCHIVO del kit, no de todo `core/ui/v2`: con la
        // exención por ruta, un segundo archivo del kit con un campo sin envolver
        // quedaba invisible para este caso y el siguiente solo lee `Fields.kt`
        // (mutación M11 de la verificación).
        val infractores = fuentes()
            .filterNot { it.path.endsWith(kit) }
            .filterNot { f -> camposFueraDelKit.any { f.path.endsWith(it) } }
            .filter { f -> constructorRegex.containsMatchIn(codigoDe(f)) }
            .map { it.name }
            .toList()
        assertTrue(
            "Campos de texto creados fuera del archivo del kit ($kit): $infractores. " +
                "Todo campo editable debe declararse ahí, que es donde se aplican las " +
                "protecciones de H-7. La exención es de ESE archivo y no de todo " +
                "core/ui/v2, para que un segundo archivo del kit no quede invisible.",
            infractores.isEmpty()
        )
    }

    @Test
    fun `todo campo del kit que no enmascara esta envuelto en SecureTextEntry`() {
        val fields = codigoDe(kotlin(kit))
        val campos = funcionesConCampo(fields)
        // Que la lista no se derive de una constante escrita a mano: si mañana
        // aparece un V2NumberField sin envolver, esta prueba lo ve sin tocarla.
        // Cualquier visibilidad, además: la primera versión solo casaba `fun` y
        // `private fun`, y un `internal fun` —que es la convención de este código
        // para los composables de pantalla— se le escapaba (mutación M19). Lo que
        // fija es la visibilidad y los modificadores, no toda forma posible: un campo
        // declarado como `val` de tipo función lambda se le escaparía, y eso no se
        // cubre aquí sino en el guardián del DEX y en la comprobación manual.
        assertTrue(
            "El kit debería declarar al menos tres campos editables; se encontraron $campos. " +
                "Si el regex dejó de casar, esta prueba estaría pasando sin mirar nada.",
            campos.size >= 3
        )

        val sinProteger = campos.filter { campo ->
            val cuerpo = cuerpoDe(fields, "fun $campo(")
            val enmascara = "KeyboardType.Password" in cuerpo && "PasswordVisualTransformation" in cuerpo
            // Enmascarar ya hace las dos cosas de un golpe; si no enmascara, el
            // envoltorio tiene que abrir el cuerpo, antes de construir el campo.
            val envuelto = "SecureTextEntry {" in antesDelCampo(cuerpo)
            !enmascara && !envuelto
        }
        assertTrue(
            "Campos del kit que no enmascaran y no están envueltos en SecureTextEntry: " +
                "$sinProteger. Su contenido podría copiarse al portapapeles y el teclado " +
                "podría aprenderlo (H-7).",
            sinProteger.isEmpty()
        )
    }

    @Test
    fun `las dos barras de busqueda estan envueltas`() {
        camposFueraDelKit.forEach { ruta ->
            val texto = codigoDe(kotlin(ruta))
            val barras = funcionesConCampo(texto)
            assertTrue("No se encontró ningún campo de texto en $ruta.", barras.isNotEmpty())
            barras.forEach { barra ->
                val cuerpo = cuerpoDe(texto, "fun $barra(")
                assertTrue(
                    "$ruta: la función $barra construye un campo de texto sin envolverlo en " +
                        "SecureTextEntry. Por esa barra se teclea la clave personal (RF-12): " +
                        "el teclado no debe aprenderla.",
                    "SecureTextEntry {" in antesDelCampo(cuerpo)
                )
            }
        }
    }

    @Test
    fun `el envoltorio conserva las dos protecciones`() {
        val envoltorio = codigoDe(kotlin("core/security/SecureTextEntry.kt"))

        // 1. Que SecureTextEntry INSTALE las dos cosas, no que existan en el archivo.
        val cuerpo = cuerpoDe(envoltorio, "fun SecureTextEntry(")
        assertTrue(
            "SecureTextEntry ya no instala la barra sin copiar: el relato volvería a " +
                "poder copiarse aunque NoCopyTextToolbar siga definida en el archivo.",
            "LocalTextToolbar provides" in cuerpo
        )
        assertTrue(
            "SecureTextEntry ya no aplica la protección del teclado.",
            "NoImeLearning(" in cuerpo
        )

        // 2. Que la barra anule copiar y cortar, y deje pegar.
        val menu = cuerpoDe(envoltorio, "private class NoCopyTextToolbar(")
        assertTrue("NoCopyTextToolbar ya no anula «copiar».", "onCopyRequested = null" in menu)
        assertTrue("NoCopyTextToolbar ya no anula «cortar».", "onCutRequested = null" in menu)
        assertTrue(
            "«pegar» debe seguir permitido: la usuaria lo necesita en el relato.",
            "onPasteRequested = onPasteRequested" in menu
        )

        // 3. Que el flag del IME esté en el CÓDIGO y no solo en el KDoc, que también
        //    lo nombra: buscarlo en todo el archivo dejaba pasar su borrado.
        val interceptor = cuerpoDe(envoltorio, "private fun NoImeLearning(")
        assertTrue(
            "SecureTextEntry ya no pide al teclado que no aprenda: falta " +
                "IME_FLAG_NO_PERSONALIZED_LEARNING en el cuerpo del interceptor.",
            "IME_FLAG_NO_PERSONALIZED_LEARNING" in interceptor
        )
    }

    @Test
    fun `el envoltorio vive solo en core-security`() {
        val definiciones = fuentes()
            .filter { "fun SecureTextEntry(" in codigoDe(it) }
            .map { it.path.substringAfter("com/auroraclima/app/") }
            .toList()
        assertEquals(
            "SecureTextEntry debe definirse en un único sitio, dentro de core/security.",
            listOf("core/security/SecureTextEntry.kt"),
            definiciones
        )
    }
}

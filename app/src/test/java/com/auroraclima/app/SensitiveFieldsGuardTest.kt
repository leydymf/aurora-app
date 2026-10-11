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
     * Los archivos del kit y del envoltorio no tienen ninguno; las dos constantes
     * `BASE_URL` de `weather/data/remote` sí, y ahí el recorte no alcanza a nada que
     * este guardia busque.
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

    /** Dónde empieza cada campo construido en este cuerpo. */
    private fun camposDe(cuerpo: String): List<Int> = constructorRegex.findAll(cuerpo)
        .map { it.range.first }
        .toList()

    /**
     * Rango de la lambda que abre `apertura`, contando llaves hasta la que cierra.
     *
     * Hace falta para comprobar que el campo está DENTRO del envoltorio y no
     * simplemente DESPUÉS de que el envoltorio abra. La versión anterior miraba si
     * el literal aparecía antes de la primera coincidencia, y una auditoría demostró
     * que eso deja pasar dos formas: `SecureTextEntry { Spacer(...) }` con el campo
     * a continuación, y un envoltorio correcto con un SEGUNDO campo después de que
     * la lambda cierre. Las dos dejaban el relato o un campo nuevo sin proteger.
     */
    private fun rangoDeLambda(cuerpo: String, apertura: String): IntRange? {
        val inicio = cuerpo.indexOf(apertura)
        if (inicio < 0) return null
        var profundidad = 0
        for (indice in (inicio + apertura.length - 1) until cuerpo.length) {
            when (cuerpo[indice]) {
                '{' -> profundidad++
                '}' -> {
                    profundidad--
                    if (profundidad == 0) return (inicio + apertura.length)..indice
                }
            }
        }
        return null
    }

    /** Todos los campos de este cuerpo están dentro de la lambda de `SecureTextEntry`. */
    private fun estaEnvuelto(cuerpo: String): Boolean {
        val campos = camposDe(cuerpo)
        if (campos.isEmpty()) return true
        val rango = rangoDeLambda(cuerpo, "SecureTextEntry {") ?: return false
        return campos.all { it in rango }
    }

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

    /**
     * Firma de una función de nivel superior, con cualquier combinación de
     * modificadores, parámetro de tipo y receptor de extensión. Las dos últimas
     * formas las añadió una auditoría: `fun ColumnScope.V2ParagraphField(` y
     * `fun <T> V2ParagraphField(` no eran campos para la versión anterior, así que
     * el relato podía quedar desnudo en verde en cuanto el kit tuviera un cuarto
     * campo que repusiera el piso de no vacuidad.
     */
    private val firmaDeFuncion = Regex(
        """(?:^|\n)(?:@[A-Za-z0-9_]+\s+)*(?:(?:private|internal|public|inline|suspend|operator)\s+)*""" +
            """fun\s*(?:<[^>]*>\s*)?(?:[A-Za-z0-9_]+\.)?([A-Za-z0-9_]+)\("""
    )

    /** Nombre y cuerpo de cada función del archivo que construye un campo de texto. */
    private fun funcionesConCampo(texto: String): List<Pair<String, String>> = firmaDeFuncion
        .findAll(texto)
        .map { coincidencia ->
            val resto = texto.substring(coincidencia.range.last + 1)
            val hasta = cierreDeCuerpo.find(resto)?.range?.first ?: resto.length
            coincidencia.groupValues[1] to resto.substring(0, hasta)
        }
        .filter { (_, cuerpo) -> constructorRegex.containsMatchIn(cuerpo) }
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
        // Cualquier visibilidad, modificador, parámetro de tipo o receptor de
        // extensión: todas esas formas se le escapaban a versiones anteriores y se
        // cerraron una por una con mutaciones.
        //
        // Qué pasa con una forma de declaración que el regex NO reconozca —el `val` de
        // tipo función lambda, o la que se invente mañana—: ya no hay que enumerarla.
        // El caso `todo campo de texto cae dentro de una funcion reconocida` cuenta los
        // campos del archivo y los atribuidos, y si no cuadran, cae. Eso cierra la
        // especie en vez de la forma.
        //
        // Y un aviso, porque estuvo escrito al contrario: el respaldo de este guardia
        // NO es el guardián del DEX. R8 borra `SecureTextEntry`, `NoCopyTextToolbar` y
        // `NoImeLearning` del artefacto —medido: 0 ocurrencias de los tres—, así que
        // ese guardián no puede ver un campo sin envolver. El respaldo es la
        // comprobación manual §8-S1.
        assertTrue(
            "El kit debería declarar al menos tres campos editables; se encontraron $campos. " +
                "Si el regex dejó de casar, esta prueba estaría pasando sin mirar nada.",
            campos.size >= 3
        )

        val sinProteger = campos.filter { (_, cuerpo) ->
            val enmascara = "KeyboardType.Password" in cuerpo && "PasswordVisualTransformation" in cuerpo
            // Enmascarar ya hace las dos cosas de un golpe; si no enmascara, TODOS
            // los campos del cuerpo tienen que estar dentro de la lambda.
            !enmascara && !estaEnvuelto(cuerpo)
        }.map { (nombre, _) -> nombre }
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
            barras.forEach { (barra, cuerpo) ->
                assertTrue(
                    "$ruta: la función $barra construye un campo de texto fuera de la lambda " +
                        "de SecureTextEntry. Por esa barra se teclea la clave personal (RF-12): " +
                        "el teclado no debe aprenderla.",
                    estaEnvuelto(cuerpo)
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

    /**
     * Cierra la ESPECIE, no una forma.
     *
     * Las cuatro rondas anteriores fueron un juego del topo: cada revisión encontraba
     * una declaración que el regex de firmas no reconocía —`internal fun`, receptor de
     * extensión, parámetro de tipo, anotación en la misma línea— y el campo quedaba
     * sin vigilar con el guardia en verde, porque el piso de no vacuidad lo siguen
     * cumpliendo los tres campos reales.
     *
     * Esto lo zanja por construcción: cada campo de texto del kit tiene que caer
     * DENTRO de alguna función reconocida. Si una declaración se le escapa al regex,
     * sus campos no se atribuyen a nadie y la cuenta no cuadra — sin que haya que
     * enumerar la forma. Se lleva por delante también el `val` de tipo función lambda,
     * que era el residual declarado, y cualquier forma futura.
     */
    @Test
    fun `todo campo de texto cae dentro de una funcion reconocida`() {
        (listOf(kit) + camposFueraDelKit).forEach { ruta ->
            val texto = codigoDe(kotlin(ruta))
            val enElArchivo = camposDe(texto).size
            val atribuidos = funcionesConCampo(texto).sumOf { (_, cuerpo) -> camposDe(cuerpo).size }
            assertEquals(
                "$ruta: hay $enElArchivo campos de texto y solo $atribuidos caen dentro de una " +
                    "función que el guardia reconozca. Los que faltan no se vigilan: su envoltorio " +
                    "no se comprueba (H-7). Pasa si la declaración usa una forma que el regex de " +
                    "firmas no casa — y si eso ocurre, hay que ensanchar el regex, no la lista de " +
                    "residuales.",
                enElArchivo,
                atribuidos
            )
        }
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

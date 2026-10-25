package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián de regresión del teclado (evaluación heurística, v0.3.2).
 *
 * Lo que se descubrió: con edge-to-edge la ventana no se achica al abrir el teclado,
 * y ninguna pantalla leía su inset. En todas las pantallas con campos el teclado
 * tapaba el botón final, que no se podía presionar. La corrección es una sola
 * (`core/ui/v2/KeyboardAware`), pero hay que aplicarla en cada pantalla, y una
 * pantalla nueva con un campo puede olvidarla sin que nada falle.
 *
 * Lo que fija: todo archivo de pantalla que use un campo de texto aplica
 * `keyboardAware()` en su código, no en un comentario. La fachada es la única
 * exención y va como dato con su causa (regla de guardianes, §5 de la guía): allí
 * el inset va solo sobre los resultados y el toque fuera se aplica aparte.
 *
 * Límite conocido: mira el ARCHIVO, no la raíz de cada pantalla. Dos pantallas en un
 * mismo archivo, una con el modificador y otra con el campo, pasarían. Hoy no existe
 * ese caso; la comprobación real de que el botón queda alcanzable vive en
 * `KeyboardAwareTest` de las instrumentadas.
 */
class KeyboardAwareGuardTest {
    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    private val appDir get() = File(mainDir, "java/com/auroraclima/app")

    /** Mismo recorte que SensitiveFieldsGuardTest: un comentario no cuenta como código. */
    private fun codigoDe(file: File): String = file.readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    /**
     * Los campos del kit y las dos formas propias de las barras de búsqueda. El resto
     * de constructores (`OutlinedTextField`, `BasicTextField`, `SearchBar`…) no
     * puede aparecer en una pantalla porque `SensitiveFieldsGuardTest` los veda fuera
     * del kit: si alguien relaja ese guardián, debe ampliar también este regex.
     */
    private val campo = Regex("""(?<![A-Za-z0-9_])(V2PlainField|V2SecretField|V2ParagraphField|TextField)\(""")

    /** El kit declara los campos; no es una pantalla. */
    private val kit = "core/ui/v2/Fields.kt"

    /** Declara SecureDialog y SecureSheet; no es una pantalla que los use. */
    private val envoltorios = "core/security/"

    /**
     * Exenciones, cada una con su causa. La fachada usa dos de las tres partes por
     * separado porque, con la barra enfocada y la consulta vacía, su barra de
     * pestañas sigue a la vista: con `keyboardAware()` en la raíz saltaría sobre el
     * teclado. La franja de la nube no aplica: la fachada no tiene nube. Si esa barra
     * deja de verse mientras se busca, la exención sobra.
     */
    private val exenciones = mapOf(
        "weather/ui/WeatherScreen.kt" to listOf(".clearFocusOnTap()", ".imePadding()")
    )

    private fun pantallasConCampo(): List<Pair<String, String>> = appDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(appDir).invariantSeparatorsPath to codigoDe(it) }
        .filter { (ruta, codigo) -> ruta != kit && campo.containsMatchIn(codigo) }
        .toList()

    @Test
    fun `toda pantalla con campos de texto se ajusta al teclado`() {
        val sinAjuste = pantallasConCampo().filter { (ruta, codigo) ->
            val requeridos = exenciones[ruta] ?: listOf(".keyboardAware()")
            requeridos.any { it !in codigo }
        }.map { it.first }

        assertTrue(
            "Pantallas con campos que no se ajustan al teclado (usar Modifier.keyboardAware() en la raíz, " +
                "en lugar de systemBarsPadding()): $sinAjuste",
            sinAjuste.isEmpty()
        )
    }

    /**
     * `SecureDialog` y `SecureSheet` desplazan su contenido para que el teclado no
     * deje botones debajo (v0.3.2). Una lista perezosa dentro recibiría alto infinito
     * y reventaría; un `verticalScroll` anidado también, y ese no lo ve esta prueba.
     * Mira el archivo, no la lambda: mismo límite que la prueba de
     * arriba, y por eso las dos pantallas que hoy tienen lista pero ningún diálogo ni
     * hoja no cuentan.
     */
    @Test
    fun `ningún diálogo ni hoja segura lleva listas perezosas`() {
        val ventanas = Regex("""(?<![A-Za-z0-9_])(SecureDialog|SecureSheet)\(""")
        val perezosas = listOf(
            "Column",
            "Row",
            "VerticalGrid",
            "HorizontalGrid",
            "VerticalStaggeredGrid",
            "HorizontalStaggeredGrid"
        ).joinToString("|").let { Regex("""(?<![A-Za-z0-9_])Lazy($it)\(""") }
        val conVentana = appDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(appDir).invariantSeparatorsPath to codigoDe(it) }
            .filter { (ruta, codigo) -> !ruta.startsWith(envoltorios) && ventanas.containsMatchIn(codigo) }
            .toList()
        assertTrue(
            "Se esperaban al menos 5 archivos con diálogos u hojas y hay ${conVentana.size}",
            conVentana.size >= 5
        )
        val conLista = conVentana.filter { (_, codigo) -> perezosas.containsMatchIn(codigo) }.map { it.first }
        assertTrue("Archivos con diálogo u hoja segura y lista perezosa: $conLista", conLista.isEmpty())
    }

    @Test
    fun `el guardián encuentra pantallas con campos`() {
        // Piso de no vacuidad: si el regex dejara de casar, la prueba de arriba
        // pasaría sin revisar nada. Hoy son diez archivos.
        val rutas = pantallasConCampo().map { it.first }
        assertTrue("Se esperaban al menos 10 pantallas con campos y hay ${rutas.size}: $rutas", rutas.size >= 10)
        assertTrue("La exención de la fachada ya no aplica: revisarla", exenciones.keys.all { it in rutas })
    }
}

package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardián de los insets de las ventanas propias (RF-16; v0.3.3).
 *
 * Lo que se descubrió: la nube de salida rápida (`QuickExitCloud`) aparta barras y
 * teclado (`systemBarsPadding().imePadding()`), pero el contenido del selector de fecha
 * solo apartaba las barras. En API 26 la plataforma reporta a veces un inset de teclado
 * sin teclado; la nube subía y la tarjeta, que no seguía ese inset, quedaba debajo de
 * ella (`SecureDayPickerInsetsTest`).
 *
 * Lo que fija: la nube lleva `imePadding()` y todo `SecureWindow` lo lleva en su
 * `contentModifier`, para que contenido y nube lean siempre los mismos insets. Una
 * ventana nueva que lo olvide hace caer la prueba.
 *
 * Límite conocido: comprueba el texto del modificador, no el diseño resultante; el
 * cruce real lo mide `SecureDayPickerInsetsTest` en las instrumentadas.
 */
class SecureWindowInsetsGuardTest {
    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    private val securityDir get() = File(mainDir, "java/com/auroraclima/app/core/security")

    /** Un comentario no cuenta como código (mismo recorte que los otros guardianes). */
    private fun codigoDe(file: File): String = file.readText()
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val llamada = Regex("""(?<![A-Za-z0-9_.])SecureWindow\(""")

    /** Las llamadas a `SecureWindow(` de cada archivo, sin su declaración. */
    private fun llamadasPorArchivo(): List<Pair<File, List<MatchResult>>> = securityDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { archivo ->
            val codigo = codigoDe(archivo)
            archivo to llamada.findAll(codigo)
                .filterNot { codigo.substring(0, it.range.first).trimEnd().endsWith("fun") }
                .toList()
        }
        .filter { it.second.isNotEmpty() }
        .toList()

    /** El argumento `contentModifier = …` de cada llamada a `SecureWindow(` que lo nombra, con su archivo. */
    private fun modificadoresDeContenido(): List<Pair<String, String>> {
        val argumento = Regex("""contentModifier\s*=""")
        return llamadasPorArchivo().flatMap { (archivo, llamadas) ->
            val codigo = codigoDe(archivo)
            llamadas.mapNotNull { inicio ->
                val desde = argumento.find(codigo, inicio.range.last)?.range?.last?.plus(1)
                desde?.let { archivo.name to hastaLaComaDelArgumento(codigo, it) }
            }
        }
    }

    /** El argumento termina en la primera coma que no está dentro de paréntesis. */
    private fun hastaLaComaDelArgumento(codigo: String, desde: Int): String {
        var profundidad = 0
        for (i in desde until codigo.length) {
            when (codigo[i]) {
                '(' -> profundidad++
                ')' -> if (profundidad == 0) return codigo.substring(desde, i) else profundidad--
                ',' -> if (profundidad == 0) return codigo.substring(desde, i)
            }
        }
        return codigo.substring(desde)
    }

    @Test
    fun `la nube aparta las barras y el teclado`() {
        val codigo = codigoDe(File(securityDir, "SecureNavigation.kt"))
        assertTrue(
            "QuickExitCloud ya no está en SecureNavigation.kt: revisar el guardián",
            "fun QuickExitCloud(" in codigo
        )
        val nube = codigo.substringAfter("fun QuickExitCloud(").substringBefore("\n}\n")
        assertTrue("QuickExitCloud debe llevar .systemBarsPadding()", ".systemBarsPadding()" in nube)
        assertTrue("QuickExitCloud debe llevar .imePadding()", ".imePadding()" in nube)
    }

    @Test
    fun `todo SecureWindow aparta el teclado como la nube`() {
        val sinTeclado = modificadoresDeContenido().filter { (_, modificador) -> ".imePadding()" !in modificador }
        assertTrue(
            "Ventanas propias cuyo contenido no lleva .imePadding() (la nube sí; con un inset de teclado " +
                "espurio en API 26 contenido y nube se cruzarían): ${sinTeclado.map { it.first }}",
            sinTeclado.isEmpty()
        )
    }

    @Test
    fun `el guardián encuentra las ventanas propias`() {
        // Piso de no vacuidad: hoy son tres (diálogo, hoja y selector de fecha).
        val archivos = modificadoresDeContenido().map { it.first }
        assertTrue(
            "Se esperaban al menos 3 llamadas a SecureWindow y hay ${archivos.size}: $archivos",
            archivos.size >= 3
        )
        assertTrue("El selector de fecha ya no se encuentra: $archivos", "SecureDayPicker.kt" in archivos)
        // Una llamada sin `contentModifier = …` literal (posicional, o en una variable) no se puede
        // juzgar: que falle aquí en lugar de quedar fuera del guardián.
        val llamadas = llamadasPorArchivo().sumOf { it.second.size }
        assertTrue(
            "Hay $llamadas llamadas a SecureWindow y solo ${archivos.size} nombran su contentModifier",
            llamadas == archivos.size
        )
    }
}

package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de la terminología de v0.3.2: la app nombra siempre igual a sus tres
 * credenciales — código de activación, contraseña de la cuenta y código de acceso —
 * para que nadie confunda la contraseña de Firebase con lo que se teclea en el buscador.
 */
class TerminologyGuardTest {
    private val strings: Map<String, String> = run {
        val file = requireNotNull(
            sequenceOf(File("src/main/res/values/strings.xml"), File("app/src/main/res/values/strings.xml"))
                .firstOrNull { it.exists() }
        ) { "No se encontró strings.xml" }
        Regex("""<string name="([^"]+)">([^<]*)</string>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    // Exención declarada: la contraseña del PDF exportado es un cuarto concepto, ajeno a las tres credenciales.
    private val prefijosExentosDeContrasena = listOf("journal_export_")

    // Exención declarada: va justo bajo el título «Tu código de acceso.», así que «Este código» no es ambiguo.
    private val idsExentosDeCodigoCompleto = setOf("access_setup_difference")

    private fun violaciones(patron: Regex, exentos: List<String> = emptyList()): List<String> =
        strings.filter { (id, texto) -> exentos.none { id.startsWith(it) } && patron.containsMatchIn(texto) }
            .map { (id, texto) -> "$id: $texto" }

    @Test
    fun `ningun texto usa clave PIN ni llave para nombrar el codigo de acceso`() {
        val encontrados = violaciones(Regex("""\b(clave|claves|pin|llave|llaves)\b""", RegexOption.IGNORE_CASE))

        assertTrue("Variantes prohibidas:\n" + encontrados.joinToString("\n"), encontrados.isEmpty())
    }

    @Test
    fun `toda contrasena nombrada es la de la cuenta`() {
        val sinCuenta = strings.filter { (id, texto) ->
            prefijosExentosDeContrasena.none { id.startsWith(it) } &&
                Regex("""contraseña""", RegexOption.IGNORE_CASE).containsMatchIn(texto) &&
                !texto.contains("cuenta", ignoreCase = true)
        }.map { (id, texto) -> "$id: $texto" }

        assertTrue("Contraseña sin «cuenta»:\n" + sinCuenta.joinToString("\n"), sinCuenta.isEmpty())
    }

    @Test
    fun `todo codigo nombrado es de acceso o de activacion`() {
        val ambiguos = strings.filter { (id, texto) ->
            id !in idsExentosDeCodigoCompleto &&
                Regex("""códigos?\b""", RegexOption.IGNORE_CASE).containsMatchIn(texto) &&
                !Regex("""códigos? de (acceso|activación)""", RegexOption.IGNORE_CASE).containsMatchIn(texto)
        }.map { (id, texto) -> "$id: $texto" }

        assertTrue("Código ambiguo:\n" + ambiguos.joinToString("\n"), ambiguos.isEmpty())
    }

    @Test
    fun `los textos de v0 3 2 explican la diferencia, qué es Aurora y el alcance de eliminar`() {
        val diferencia = strings.getValue("access_setup_difference")
        assertTrue(diferencia.contains("distinto de la contraseña de tu cuenta"))
        assertTrue(diferencia.contains("buscador de ciudades"))

        assertTrue(strings.getValue("account_intro_what").startsWith("Aurora es un espacio privado"))

        val aviso = strings.getValue("account_delete_final_notice")
        assertTrue(aviso.contains("de este teléfono y de la nube"))
        assertTrue(aviso.contains("instalarla de nuevo"))
    }
}

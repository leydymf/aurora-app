package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guardia de la terminología de v0.3.2: cuatro credenciales, cada una con un solo nombre en los textos. */
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

    // Exención declarada: la contraseña del archivo PDF es el cuarto término y se vigila en su propia prueba.
    private val prefijosExentosDeContrasena = listOf("journal_export_")

    // Exención declarada: esta frase nombra la contraseña de la cuenta justamente para distinguirla de la del PDF.
    private val idsExentosDeContrasenaDelArchivo = setOf("journal_export_password_scope")

    // Exención declarada: va justo bajo el título «Tu código de acceso.», así que «Este código» no es ambiguo.
    private val idsExentosDeCodigoCompleto = setOf("access_setup_difference")

    // Exención declarada (v0.3.3, decisión de la autora): el paso «Mi registro» del recorrido
    // cuenta que el PDF se protege «con contraseña» sin nombrar ninguna credencial de la cuenta;
    // la contraseña del archivo PDF se nombra entera en la pantalla de exportación.
    private val idsExentosDeContrasenaDeCuenta = setOf("access_tour_journal_body")

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
            prefijosExentosDeContrasena.none { id.startsWith(it) } && id !in idsExentosDeContrasenaDeCuenta &&
                Regex("""contraseña""", RegexOption.IGNORE_CASE).containsMatchIn(texto) &&
                !texto.contains("cuenta", ignoreCase = true)
        }.map { (id, texto) -> "$id: $texto" }

        assertTrue("Contraseña sin «cuenta»:\n" + sinCuenta.joinToString("\n"), sinCuenta.isEmpty())
    }

    @Test
    fun `toda contrasena de los textos de exportacion es la del archivo PDF`() {
        val sueltas = strings.filter { (id, texto) ->
            id.startsWith("journal_export_") && id !in idsExentosDeContrasenaDelArchivo &&
                Regex("""contraseñas?(?! del archivo PDF)""", RegexOption.IGNORE_CASE).containsMatchIn(texto)
        }.map { (id, texto) -> "$id: $texto" }

        assertTrue("Contraseña sola en la exportación:\n" + sueltas.joinToString("\n"), sueltas.isEmpty())
    }

    @Test
    fun `la contrasena del archivo PDF solo se nombra en los textos de exportacion`() {
        val fuera = strings.filter { (id, texto) ->
            !id.startsWith("journal_export_") && texto.contains("contraseña del archivo PDF", ignoreCase = true)
        }.map { (id, texto) -> "$id: $texto" }

        val detalle = fuera.joinToString("\n")
        assertTrue("«contraseña del archivo PDF» fuera de la exportación:\n$detalle", fuera.isEmpty())
    }

    @Test
    fun `los cuatro terminos se escriben igual`() {
        assertEquals("Contraseña del archivo PDF", strings.getValue("journal_export_password_label"))
        assertEquals("Contraseña de la cuenta", strings.getValue("account_register_password_label"))
        assertEquals("Nuevo código de acceso", strings.getValue("access_change_new_label"))
        assertTrue(strings.getValue("access_setup_title").contains("código de acceso"))
        val alMenosUnaVez = listOf("contraseña del archivo PDF", "contraseña de la cuenta", "código de acceso")
        alMenosUnaVez.forEach { termino ->
            assertTrue(
                "«$termino» no aparece en ningún texto",
                strings.values.any { it.contains(termino, ignoreCase = true) }
            )
        }
    }

    @Test
    fun `el flujo de restablecer nombra la contrasena de la cuenta y no el acceso`() {
        assertEquals("Restablecer la contraseña de tu cuenta", strings.getValue("account_reset_title"))
        val conAcceso = strings.filter { (id, texto) ->
            id.startsWith("account_reset_") && texto.contains("acceso", ignoreCase = true)
        }.map { (id, texto) -> "$id: $texto" }

        assertTrue("«acceso» en el flujo de restablecer:\n" + conAcceso.joinToString("\n"), conAcceso.isEmpty())
    }

    @Test
    fun `el bloqueo del inicio de sesion lo nombra y no lo llama acceso`() {
        val cuerpo = strings.getValue("account_login_lock_body")

        assertTrue(cuerpo.contains("el inicio de sesión queda en pausa"))
        assertTrue("«acceso» en el bloqueo: $cuerpo", !cuerpo.contains("acceso", ignoreCase = true))
    }

    @Test
    fun `la frase bajo la contrasena del PDF la distingue de la cuenta y del codigo de acceso`() {
        val frase = strings.getValue("journal_export_password_scope")

        assertEquals(
            "Solo sirve para abrir este archivo. No es la contraseña de tu cuenta ni tu código de acceso.",
            frase
        )
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

        assertTrue(strings.getValue("welcome_what").startsWith("Aurora es un espacio privado"))

        val aviso = strings.getValue("account_delete_final_notice")
        assertTrue(aviso.contains("de este teléfono y de la nube"))
        assertTrue(aviso.contains("instalarla de nuevo"))
    }
}

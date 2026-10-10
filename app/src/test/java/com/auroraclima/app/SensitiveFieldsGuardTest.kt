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
 */
class SensitiveFieldsGuardTest {
    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    private fun kotlin(ruta: String) = File(mainDir, "java/com/auroraclima/app/$ruta")

    /**
     * Las dos barras de búsqueda viven fuera del kit porque su forma es de la fachada
     * (píldora, slots propios), no del espacio interno. Están exentas de la regla 1
     * pero NO de la 2: se comprueban una por una más abajo.
     */
    private val camposFueraDelKit = listOf(
        "weather/ui/WeatherScreen.kt",
        "access/ui/TutorialScreen.kt"
    )

    private fun fuentesDeUi(): Sequence<File> = mainDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filter { "/ui/" in it.path }

    @Test
    fun `ningun campo de texto editable se crea fuera del kit v2`() {
        val constructores = listOf("OutlinedTextField(", "BasicTextField(", "TextField(")
        val infractores = fuentesDeUi()
            .filterNot { "core/ui/v2" in it.path }
            .filterNot { f -> camposFueraDelKit.any { f.path.endsWith(it) } }
            .filter { f -> constructores.any { it in f.readText() } }
            .map { it.name }
            .toList()
        assertTrue(
            "Campos de texto creados fuera de core/ui/v2: $infractores. " +
                "Todo campo editable debe usar el kit, que aplica las protecciones de H-7.",
            infractores.isEmpty()
        )
    }

    @Test
    fun `todo campo del kit que no enmascara esta envuelto en SecureTextEntry`() {
        val fields = kotlin("core/ui/v2/Fields.kt").readText()
        // Los campos que no enmascaran: los declara el kit y deben abrir con el envoltorio.
        listOf("V2PlainField", "V2ParagraphField").forEach { campo ->
            val firma = fields.substringAfter("fun $campo(").substringBefore("OutlinedTextField(")
            assertTrue(
                "$campo no está envuelto en SecureTextEntry: su contenido podría copiarse " +
                    "al portapapeles y el teclado podría aprenderlo (H-7).",
                "SecureTextEntry" in firma
            )
        }
        // El campo enmascarado no lo necesita: KeyboardType.Password ya hace las dos cosas.
        val secreto = fields.substringAfter("fun V2SecretField(").substringBefore("keyboardActions = keyboardActions")
        assertTrue(
            "V2SecretField debe seguir usando KeyboardType.Password y PasswordVisualTransformation.",
            "KeyboardType.Password" in secreto && "PasswordVisualTransformation" in secreto
        )
    }

    @Test
    fun `las dos barras de busqueda estan envueltas`() {
        camposFueraDelKit.forEach { ruta ->
            val texto = kotlin(ruta).readText()
            assertTrue(
                "$ruta tiene un campo de texto sin envolver en SecureTextEntry. Por esa barra " +
                    "se teclea la clave personal (RF-12): el teclado no debe aprenderla.",
                "SecureTextEntry {" in texto
            )
        }
    }

    @Test
    fun `el envoltorio conserva las dos protecciones`() {
        val envoltorio = kotlin("core/security/SecureTextField.kt").readText()
        assertTrue(
            "SecureTextEntry ya no anula «copiar»: el relato volvería a poder copiarse.",
            "onCopyRequested = null" in envoltorio
        )
        assertTrue(
            "SecureTextEntry ya no anula «cortar».",
            "onCutRequested = null" in envoltorio
        )
        assertTrue(
            "SecureTextEntry ya no pide al teclado que no aprenda.",
            "IME_FLAG_NO_PERSONALIZED_LEARNING" in envoltorio
        )
        assertTrue(
            "«pegar» debe seguir permitido: la usuaria lo necesita en el relato.",
            "onPasteRequested = onPasteRequested" in envoltorio
        )
    }

    @Test
    fun `el envoltorio vive solo en core-security`() {
        val definiciones = mainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { "fun SecureTextEntry(" in it.readText() }
            .map { it.path.substringAfter("com/auroraclima/app/") }
            .toList()
        assertEquals(
            "SecureTextEntry debe definirse en un único sitio, dentro de core/security.",
            listOf("core/security/SecureTextField.kt"),
            definiciones
        )
    }
}

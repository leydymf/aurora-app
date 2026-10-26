package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de la transición de entrada. Se calcula sobre la navegación real de
 * `AuroraApp.kt` (§5: la afirmación sale del código, no de la prosa):
 *
 *  - la transición se alcanza solo desde la rama `OpenAccess` de la puerta de la
 *    fachada, es decir, solo con el código de acceso correcto;
 *  - un código que no coincide (`PassThrough`) y la activación (`OpenActivation`)
 *    no pasan por ella: la fachada se comporta igual que ante una búsqueda fallida
 *    (RF-12, RF-13);
 *  - es una pantalla del grafo seguro (`screen`), así que FLAG_SECURE rige desde su
 *    primer cuadro (RNF-02);
 *  - la salida rápida no la toca: ni el cierre ni los guardianes de sesión conocen
 *    la transición, y siguen sin animaciones (RF-16).
 */
class EntryTransitionGuardTest {
    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    private val appDir = File(mainDir, "java/com/auroraclima/app")
    private val navText = File(appDir, "AuroraApp.kt").readText()

    private val gateBranch = Regex("""SearchGateResult\.(\w+)\s*->([^\n]*)""")

    @Test
    fun `la transicion se alcanza solo desde la rama del codigo correcto`() {
        val navigations = Regex("""navigate\(ARRIVAL_ROUTE\)""").findAll(navText).toList()
        assertEquals("Debe haber exactamente una navegación hacia la transición", 1, navigations.size)

        val branch = gateBranch.findAll(navText).single { it.groupValues[2].contains("ARRIVAL_ROUTE") }
        assertEquals("OpenAccess", branch.groupValues[1])
    }

    @Test
    fun `un codigo que no coincide y la activacion no pasan por la transicion`() {
        val branches = gateBranch.findAll(navText).associate { it.groupValues[1] to it.groupValues[2].trim() }

        assertEquals("Unit", branches.getValue("PassThrough"))
        assertEquals("navController.navigate(INTRO_ROUTE)", branches.getValue("OpenActivation"))
    }

    @Test
    fun `la transicion es una pantalla del grafo seguro`() {
        assertTrue(Regex("""\bscreen\(ARRIVAL_ROUTE\)""").containsMatchIn(navText))
        // Las rutas de la fachada se registran con composable(...): la transición no.
        assertFalse(Regex("""composable\(ARRIVAL_ROUTE""").containsMatchIn(navText))
    }

    @Test
    fun `la transicion sale de la pila al terminar`() {
        val block = navText.substringAfter("screen(ARRIVAL_ROUTE)").substringBefore("screen(ENTRY_ROUTE)")
        assertTrue(block.contains("navigate(ENTRY_ROUTE)"))
        assertTrue(block.contains("popUpTo(ARRIVAL_ROUTE) { inclusive = true }"))
    }

    @Test
    fun `terminar la transicion no abre el interior si la persona ya salio`() {
        // La espera es un efecto diferido: entre el pop a la fachada (nube, atrás, segundo
        // plano) y su cancelación puede quedar un cuadro. Navegar a ENTRY desde ahí
        // apilaría el interior sobre la fachada, así que navegar exige seguir en `arrival`.
        val block = navText.substringAfter("screen(ARRIVAL_ROUTE)").substringBefore("screen(ENTRY_ROUTE)")
        val guard = block.indexOf("currentBackStackEntry?.destination?.route == ARRIVAL_ROUTE")
        assertTrue("onFinished debe comprobar que sigue en la transición", guard >= 0)
        assertTrue("la comprobación debe preceder a la navegación", guard < block.indexOf("navigate(ENTRY_ROUTE)"))

        // Y la pantalla solo avanza con su entrada reanudada.
        val screen = File(appDir, "access/ui/EntryTransitionScreen.kt").readText()
        assertTrue(screen.contains("lifecycle.withResumed { currentOnFinished() }"))
    }

    @Test
    fun `la salida rapida y la sesion no conocen la transicion`() {
        val security = File(appDir, "core/security").walkTopDown().filter { it.isFile && it.extension == "kt" }
        security.forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} no debe depender de la transición", text.contains("EntryTransition"))
            assertFalse("${file.name} no debe depender de la transición", text.contains("ARRIVAL_ROUTE"))
        }
        // El grafo seguro conserva sus transiciones nulas (salida inmediata, RF-16 criterio 2).
        val secureNavigation = File(appDir, "core/security/SecureNavigation.kt").readText()
        listOf("enterTransition", "exitTransition", "popEnterTransition", "popExitTransition").forEach {
            assertTrue(
                "$it debe seguir en None",
                Regex("""$it = \{ (?:Enter|Exit)Transition\.None }""").containsMatchIn(secureNavigation)
            )
        }
    }

    @Test
    fun `la fachada vuelve sin animacion tras la salida`() {
        // popEnterTransition None en la fachada: la transición de entrada no cambia la salida.
        assertTrue(navText.contains("composable(HOME_ROUTE, popEnterTransition = { EnterTransition.None })"))
    }
}

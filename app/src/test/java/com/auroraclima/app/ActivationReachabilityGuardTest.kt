package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia del hallazgo A′ (v0.3.2). Al descartar una activación sin terminar se
 * borran los datos locales de cuenta (`DiscardUnfinishedActivationUseCase`); eso
 * solo es seguro si, mientras no existe la ranura USER del código de acceso, NO
 * hay registros creados localmente que se puedan perder. Lo único local en ese
 * estado es lo que el login de activación restauró de la nube (RF-39), y la
 * nube lo repone al volver a escribir la contraseña.
 *
 * La afirmación se CALCULA sobre la navegación real de `AuroraApp.kt` (§5,
 * diseño de guardianes): desde la entrada de activación, sin cruzar la creación
 * del código, solo se alcanzan pantallas de `accounts`, y `accounts` no importa
 * ninguna feature que cree registros. Si alguien agrega un camino nuevo antes
 * del código, esta prueba lo detecta antes de que el descarte borre algo real.
 *
 * Fija además la otra mitad del hallazgo: todo reconciliador que suba espera al
 * código de acceso, y las exenciones del descarte son exactamente las declaradas.
 */
class ActivationReachabilityGuardTest {
    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    private val appDir = File(mainDir, "java/com/auroraclima/app")
    private val navText = File(appDir, "AuroraApp.kt").readText()

    /** Constantes de ruta (`private const val X = "..."`) con su valor literal. */
    private val routeConstants: Map<String, String> =
        Regex("""private const val (\w+) = "([^"]*)"""").findAll(navText)
            .associate { it.groupValues[1] to it.groupValues[2] }

    /** Donde termina el grafo seguro y empieza la navegación global de AuroraApp. */
    private val graphEnd = navText.indexOf("LaunchedEffect(silentSession").also {
        assertTrue("No se encontró el fin del grafo seguro en AuroraApp.kt", it >= 0)
    }

    /**
     * Aristas globales (fuera de toda pantalla, disparables desde cualquiera del
     * grafo) que el recorrido NO sigue, cada una con su causa (§5: la exención es
     * dato). Una arista global nueva hace fallar la prueba hasta declararse aquí.
     */
    private val excludedGlobalEdges = mapOf(
        "ENTRY_ROUTE" to "solo la dispara el reingreso silencioso, que nace al conceder la entrada con blob " +
            "(LoginViewModel.decideReentry, con código de acceso); en activación nunca arranca (hallazgo A)"
    )

    /** Cada `screen(RUTA)` del grafo seguro con el texto de su bloque. */
    private val screenBlocks: Map<String, String> = run {
        val starts = Regex("""\bscreen\(\s*(\w+)""").findAll(navText).toList()
        starts.mapIndexed { index, match ->
            val end = starts.getOrNull(index + 1)?.range?.first ?: graphEnd
            match.groupValues[1] to navText.substring(match.range.first, end)
        }.toMap()
    }

    /** Pantallas a las que puede llevar un token de `navigate(...)` (aproximación por exceso). */
    private fun resolve(token: String): Set<String> {
        val reference = Regex("""\$\{?$token\b""")
        val matches = screenBlocks.keys.filter { route ->
            route == token || routeConstants[route]?.let { reference.containsMatchIn(it) } == true
        }.toSet()
        assertTrue("navigate($token) no corresponde a ninguna pantalla conocida", matches.isNotEmpty())
        return matches
    }

    private val navigateTarget = Regex("""navigate\(\s*(?:"\$\{?(\w+)|(\w+))""")

    private fun targetsIn(text: String): Set<String> =
        navigateTarget.findAll(text).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toSet()

    private fun destinationsOf(route: String): Set<String> =
        targetsIn(screenBlocks.getValue(route)).flatMap { resolve(it) }.toSet()

    /** Lo alcanzable desde la entrada de activación sin cruzar la creación del código. */
    private fun reachableBeforeAccessCode(): Set<String> {
        val entry = requireNotNull(
            Regex("""OpenActivation\s*->\s*navController\.navigate\((\w+)\)""").find(navText)
        ) { "No se encontró la entrada de activación en AuroraApp.kt" }.groupValues[1]
        val reached = mutableSetOf(entry)
        val pending = ArrayDeque(listOf(entry))
        while (pending.isNotEmpty()) {
            val route = pending.removeFirst()
            // Crear el código es la frontera: de ahí en adelante la ranura USER existe.
            if (route == "SETUP_ROUTE") continue
            destinationsOf(route).filter { reached.add(it) }.forEach { pending.addLast(it) }
        }
        return reached
    }

    @Test
    fun `antes del codigo de acceso solo se alcanzan consentimiento cuenta login y creacion del codigo`() {
        assertEquals(
            "Un camino nuevo antes de crear el código de acceso debe revisarse contra el " +
                "descarte de la activación sin terminar (hallazgo A′), que borra los datos locales",
            setOf("INTRO_ROUTE", "REGISTER_ROUTE", "ENTRY_SETUP_ROUTE", "SETUP_ROUTE"),
            reachableBeforeAccessCode()
        )
    }

    @Test
    fun `las aristas globales que el recorrido no sigue son exactamente las declaradas`() {
        // Entre el fin del grafo y la fachada viven los colectores globales de
        // AuroraApp: lo que naveguen aplica a cualquier pantalla del grafo.
        val globalSection = navText.substring(graphEnd, navText.indexOf("fun HomeRoute"))

        assertEquals(excludedGlobalEdges.keys, targetsIn(globalSection))
    }

    @Test
    fun `las pantallas previas al codigo solo navegan con navigate o popBackStack`() {
        // Si una de ellas recibiera el navController (o una referencia a
        // navigate), navegaría sin que el recorrido lo vea.
        val leaks = (reachableBeforeAccessCode() - "SETUP_ROUTE").flatMap { route ->
            Regex("""navController(?!\.navigate\(|\.popBackStack\()""").findAll(screenBlocks.getValue(route))
                .map { "$route: …${screenBlocks.getValue(route).substring(it.range.first).take(40)}" }
        }

        assertTrue("Uso del navController que el recorrido no modela:\n" + leaks.joinToString("\n"), leaks.isEmpty())
    }

    @Test
    fun `todo reconciliador o restaurador que sube datos espera al codigo de acceso`() {
        // Hallazgo A′ (v0.3.2): la guarda de subida vive en cada reconciliador;
        // uno nuevo que suba sin ella dejaría subir datos antes del código.
        val syncContract = Regex("""\)\s*:[^{]*\b(AccountDataRestorer|AccountDataReconciler)\b""")
        val pushers = appDir.walkTopDown()
            .filter { it.extension == "kt" }
            .filter { file -> file.readText().let { syncContract.containsMatchIn(it) && ".push(" in it } }
            .toList()
        val unguarded = pushers.filterNot { "accessCode.isSet()" in it.readText() }.map { it.name }

        assertTrue("Se esperaban los reconciliadores del historial y de los resultados", pushers.size >= 2)
        assertTrue("Reconciliadores que suben sin esperar al código de acceso: $unguarded", unguarded.isEmpty())
    }

    @Test
    fun `antes del codigo de acceso ninguna pantalla puede crear registros locales`() {
        val beforeCode = reachableBeforeAccessCode() - "SETUP_ROUTE"
        val routeComposables = beforeCode.flatMap { route ->
            Regex("""\b([A-Z]\w*Route)\(""").findAll(screenBlocks.getValue(route)).map { it.groupValues[1] }
        }.toSet()
        val accountsDir = File(appDir, "accounts")
        val outsideAccounts = routeComposables.filterNot { name ->
            accountsDir.walkTopDown().any { it.extension == "kt" && "fun $name(" in it.readText() }
        }
        assertTrue(
            "Pantallas previas al código de acceso fuera de accounts: $outsideAccounts",
            routeComposables.isNotEmpty() && outsideAccounts.isEmpty()
        )

        // Quien crea registros (historial, evidencias, resultados guardados) vive
        // en records y questionnaire; accounts no puede tocarlos.
        val recordWriters = listOf("com.auroraclima.app.records", "com.auroraclima.app.questionnaire")
        val violations = accountsDir.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                file.readLines().filter { line -> line.startsWith("import ") && recordWriters.any { it in line } }
                    .map { "${file.relativeTo(mainDir)}: $it" }
            }
            .toList()
        assertTrue(
            "accounts importa una feature que crea registros:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `las exenciones del descarte de activacion son exactamente las declaradas y con su causa`() {
        // §5: la exención es dato con su causa al lado. Agregar una aquí obliga a
        // pasarla por revisión, porque deja datos de otra cuenta en el dispositivo.
        // Cualquier override cuenta, escrito como sea; solo la forma literal se acepta.
        val anyOverride = Regex("""override\s+val\s+keptOnUnfinishedActivation\b""")
        val literalForm = "override val keptOnUnfinishedActivation = true"
        // Sobre el texto completo, no línea a línea: un override partido en dos
        // líneas también cuenta (y no es la forma literal, así que falla).
        val overrides = appDir.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                anyOverride.findAll(text).map { match ->
                    val lineStart = text.lastIndexOf('\n', match.range.first) + 1
                    val lineEnd = text.indexOf('\n', match.range.first).takeIf { it >= 0 } ?: text.length
                    val previousLine = text.substring(0, lineStart).trimEnd('\n').substringAfterLast('\n')
                    Triple(file.nameWithoutExtension, text.substring(lineStart, lineEnd).trim(), previousLine.trim())
                }
            }
            .toList()

        assertEquals(
            listOf("AccessCredentialsEraser", "FirestoreCacheEraser", "SignInAttemptsEraser"),
            overrides.map { it.first }.sorted()
        )
        val malformed = overrides.filterNot { (_, line, previous) -> line == literalForm && previous.startsWith("//") }
        assertTrue("Exención que no es el literal `= true` con su causa encima: $malformed", malformed.isEmpty())
    }
}

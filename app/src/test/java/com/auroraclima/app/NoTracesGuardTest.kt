package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de regresión de RF-06 (ausencia de indicios) y RNF-02.
 *
 * Mientras el espacio interno está inactivo no puede existir ningún rastro visible:
 * ni notificaciones, ni accesos directos, ni widgets, ni tiles. Y la fachada de clima
 * jamás bloquea capturas (una app de clima con FLAG_SECURE sería un distinguidor):
 * la bandera solo puede vivir en core/security, aplicada por el grafo seguro.
 */
class NoTracesGuardTest {
    /**
     * APIs de Android que dejan rastro visible fuera de la app. Si algún día un RF
     * exigiera una de estas, debe discutirse contra RNF-05 antes de levantar la veda.
     */
    private val visibleTraceApis = listOf(
        "NotificationManager",
        "NotificationCompat",
        "NotificationChannel",
        "POST_NOTIFICATIONS",
        "ShortcutManager",
        "ShortcutInfo",
        "pushDynamicShortcut",
        "AppWidgetProvider",
        "GlanceAppWidget",
        "TileService"
        // setRecentsScreenshotEnabled dejó esta lista el 2026-10-05 (P-14 cerrado):
        // vive SOLO en core/security/RecentsThumbnailGuard, ver el test de abajo.
    )

    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    /** Los builds debug se instalan en dispositivos durante el piloto: se barren igual. */
    private val debugDir = File(mainDir.parentFile, "debug")

    private fun mainFiles(vararg extensions: String): Sequence<File> = sequenceOf(mainDir, debugDir)
        .filter { it.exists() }
        .flatMap { it.walkTopDown() }
        .filter { it.isFile && it.extension in extensions }

    @Test
    fun `el codigo no usa notificaciones accesos directos widgets ni tiles`() {
        val violations = mainFiles("kt", "xml").flatMap { file ->
            val text = file.readText()
            visibleTraceApis
                .filter { it in text }
                .map { api -> "${file.relativeTo(mainDir)}: usa \"$api\"" }
        }.toList()

        assertTrue(
            "APIs que dejan rastro visible fuera del espacio interno (RF-06):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `res no declara shortcuts ni widgets`() {
        val violations = mainFiles("xml")
            .filter { file ->
                val text = file.readText()
                file.name.startsWith("shortcuts") ||
                    "<shortcut" in text ||
                    "appwidget-provider" in text
            }
            .map { "${it.relativeTo(mainDir)}" }
            .toList()

        assertTrue(
            "Recursos que crean accesos directos o widgets (RF-06):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `FLAG_SECURE solo existe en core security`() {
        val allowedDir = File(mainDir, "java/com/auroraclima/app/core/security")
        val violations = mainFiles("kt", "xml")
            .filter { "FLAG_SECURE" in it.readText() || "setSecure" in it.readText() }
            .filterNot { it.startsWith(allowedDir) }
            .map { "${it.relativeTo(mainDir)}" }
            .toList()

        assertTrue(
            "FLAG_SECURE fuera del componente central de core/security (RNF-02):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * La miniatura de recientes solo se oculta mientras se teclea en la barra (P-14,
     * ADR 2026-10-05) y desde un único componente: fuera de core/security sería un
     * distinguidor (una app de clima sin miniatura) o un rastro mal acotado.
     */
    @Test
    fun `setRecentsScreenshotEnabled solo existe en core security`() {
        val allowedFile = File(mainDir, "java/com/auroraclima/app/core/security/RecentsThumbnailGuard.kt")
        val violations = mainFiles("kt", "xml")
            .filter { "setRecentsScreenshotEnabled" in it.readText() }
            .filterNot { it == allowedFile }
            .map { "${it.relativeTo(mainDir)}" }
            .toList()

        assertTrue(
            "setRecentsScreenshotEnabled fuera de RecentsThumbnailGuard (RF-14/P-14):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * APIs que crean una ventana PROPIA, distinta de la ventana de la Activity: el
     * grafo seguro no las cubre y saldrían en capturas/recientes (auditoría A1).
     * Solo core/security puede usarlas, envueltas con securePolicy = SecureOn
     * (ver SecureDialog).
     */
    private val ownWindowApis = listOf(
        "androidx.compose.ui.window.Dialog",
        "androidx.compose.ui.window.Popup",
        "ModalBottomSheet",
        "BasicAlertDialog",
        "androidx.compose.material3.AlertDialog",
        "android.app.Dialog",
        "android.app.AlertDialog",
        "DatePickerDialog",
        "TimePickerDialog",
        "android.widget.Toast",
        // Crean un Popup internamente sin nombrarlo (auditoría N2).
        "DropdownMenu",
        "TooltipBox",
        "PopupWindow"
    )

    @Test
    fun `las ventanas propias solo se crean a traves de core security`() {
        val allowedDir = File(mainDir, "java/com/auroraclima/app/core/security")
        val violations = mainFiles("kt")
            .filterNot { it.startsWith(allowedDir) }
            .flatMap { file ->
                val text = file.readText()
                ownWindowApis
                    .filter { it in text }
                    .map { api -> "${file.relativeTo(mainDir)}: usa \"$api\"" }
            }
            .toList()

        assertTrue(
            "Ventanas propias fuera de los envoltorios de core/security (RNF-02, A1):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `fuera del grafo seguro solo se registran rutas de la fachada`() {
        // Rutas de la fachada de clima: la principal y el pronóstico extendido (RF-04).
        val facadeRoutes = setOf("HOME_ROUTE", "FORECAST_ROUTE")
        val navRoot = File(mainDir, "java/com/auroraclima/app/AuroraApp.kt")
        val registrations = Regex("""\bcomposable\(\s*([A-Za-z_]\w*|"[^"]*")""")
            .findAll(navRoot.readText())
            .map { it.groupValues[1] }
            .toList()
        val violations = registrations.filterNot { it in facadeRoutes }

        assertTrue(
            "Rutas registradas con composable() directo en el NavHost raíz; toda ruta " +
                "interna debe declararse dentro de secureGraph { screen(...) } (M1):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `la navegacion solo se declara en la raiz y en core security`() {
        val allowedDir = File(mainDir, "java/com/auroraclima/app/core/security")
        val navRoot = File(mainDir, "java/com/auroraclima/app/AuroraApp.kt")
        val navApis = listOf("NavHost(", "composable(")
        val violations = mainFiles("kt")
            .filterNot { it.startsWith(allowedDir) || it == navRoot }
            .flatMap { file ->
                val text = file.readText()
                navApis
                    .filter { it in text }
                    .map { api -> "${file.relativeTo(mainDir)}: usa \"$api\"" }
            }
            .toList()

        assertTrue(
            "Un NavHost o ruta declarado fuera de AuroraApp.kt evadiría la whitelist " +
                "de la fachada y el grafo seguro (RF-06):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `la fachada de clima nunca referencia el mecanismo seguro`() {
        val weatherDir = File(mainDir, "java/com/auroraclima/app/weather")
        val secureTokens = listOf(
            "FLAG_SECURE",
            "SecureScreen",
            "secureGraph",
            "SecureBackStackGuard",
            // La fachada tampoco debe leer ni gobernar la sesión interna (auditoría D3 H-5).
            "SessionManager",
            "SessionGuard"
        )
        // Excepción deliberada y acotada, registrada en el ADR 2026-10-10 (c):
        // `weather/ui/WeatherScreen` SÍ importa `core/security/SecureTextEntry`.
        // No es mecanismo del doble fondo —es el envoltorio que impide que el
        // teclado aprenda lo que se teclea en la barra, que es por donde se teclea
        // la clave personal (RF-12, H-7/P-94)— y su nombre es neutro. Esta lista
        // enumera TOKENS DE MECANISMO: no veda `core.security` bajo `weather/`, y
        // no debe hacerlo, porque rompería la corrección de H-7.
        val violations = weatherDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                secureTokens
                    .filter { it in text }
                    .map { token -> "${file.relativeTo(mainDir)}: referencia \"$token\"" }
            }
            .toList()

        assertTrue(
            "La fachada no debe bloquear capturas ni conocer el mecanismo seguro (RNF-05):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * Regla 4 de CLAUDE.md, innegociable y hasta ahora sin guarda: nada del
     * doble fondo puede ir a logs ni a telemetría. Se barre `src/main` entero
     * porque un `Log.d` en cualquier capa basta para volcar contenido
     * descifrado a logcat, legible por `adb` sin root (auditoría 2026-10-06).
     */
    @Test
    fun `no hay registro en logs ni telemetria en ninguna capa`() {
        val forbidden = listOf(
            "android.util.Log",
            "Log.d(", "Log.e(", "Log.i(", "Log.v(", "Log.w(",
            "println(",
            "printStackTrace",
            "System.out", "System.err",
            "FirebaseCrashlytics",
            "FirebaseAnalytics"
        )
        val violations = mainFiles("kt")
            .filter { file -> forbidden.any { it in file.readText().withoutComments() } }
            .map { "${it.relativeTo(mainDir)}" }
            .toList()

        assertTrue(
            "Registro en logs o telemetría fuera de lo permitido (regla 4):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * Quita comentarios antes de barrer: varios KDoc explican precisamente que
     * la librería de PDF escribe en logcat y que Aurora la silencia, y nombrar
     * la API en una explicación no es usarla.
     */
    private fun String.withoutComments(): String = replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
        .lines()
        .joinToString("\n") { it.substringBefore("//") }
}

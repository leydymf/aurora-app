package com.auroraclima.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia del sistema visual V2 (Fase 1 de PLAN_UI_V2.md; ADR 2026-08-11 en
 * docs/ARQUITECTURA.md).
 *
 * El sistema visual vive UNA sola vez en core/ui: si una pantalla define un color
 * con Color(0xFF...), una fuente propia o importa iconos de Material, el sistema
 * deriva y la revisión pantalla-por-pantalla deja de garantizar nada. Las features
 * solo consumen tokens (V2Palette, voces tipográficas, V2Corner) y la librería
 * cerrada V2Icons — o, mientras dura la migración, los tokens del tema V1.
 */
class VisualSystemGuardTest {
    private val mainDir = requireNotNull(
        sequenceOf(File("src/main"), File("app/src/main")).firstOrNull { it.exists() }
    ) { "No se encontró el directorio src/main" }

    /** Todas las features; core/ui es la única fuente y queda fuera del barrido. */
    private val featureDirs = listOf("weather", "access", "accounts", "content", "questionnaire", "records")
        .map { File(mainDir, "java/com/auroraclima/app/$it") }

    private fun featureFiles(): Sequence<File> = featureDirs.asSequence()
        .filter { it.exists() }
        .flatMap { it.walkTopDown() }
        .filter { it.isFile && it.extension == "kt" }

    @Test
    fun `las features no definen colores hardcodeados`() {
        val violations = featureFiles()
            .filter { "Color(0x" in it.readText() }
            .map { "${it.relativeTo(mainDir)}" }
            .toList()

        assertTrue(
            "Colores hardcodeados fuera de core/ui; todo color viene del tema " +
                "(V2Palette o tokens V1 mientras dura la migración):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `las features no definen tipografia propia`() {
        // "R.font." subsume cualquier Font(R.font...); no hace falta un tercer token.
        val fontTokens = listOf("FontFamily(", "R.font.")
        val violations = featureFiles()
            .flatMap { file ->
                val text = file.readText()
                fontTokens
                    .filter { it in text }
                    .map { token -> "${file.relativeTo(mainDir)}: usa \"$token\"" }
            }
            .toList()

        assertTrue(
            "Fuentes construidas fuera de core/ui; las familias se consumen desde " +
                "las voces del tema (InteriorTypeVoice/FacadeTypeVoice o Type.kt V1):\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    /**
     * Pantallas V1 que ya usaban iconos de Material antes de la librería cerrada.
     * Esta lista SOLO puede menguar: cada pantalla que se migre a la V2 debe salir
     * de aquí y quedarse con V2Icons (o el set meteorológico ic_weather_* en la
     * fachada). Prohibido agregar entradas.
     *
     * Nota de alcance: los componentes V1 de core/ui (p. ej. StatusElements.kt)
     * quedan fuera del barrido porque core/ui ES la fuente; mueren con el resto
     * del tema V1 en la Fase 4 de PLAN_UI_V2.md.
     */
    private val legacyMaterialIconsFiles = setOf(
        "java/com/auroraclima/app/access/ui/TutorialScreen.kt",
        "java/com/auroraclima/app/access/ui/PanelScreen.kt",
        "java/com/auroraclima/app/access/ui/SettingsScreen.kt",
        "java/com/auroraclima/app/content/ui/ContentElements.kt",
        "java/com/auroraclima/app/content/ui/AgencyDetailScreen.kt",
        "java/com/auroraclima/app/content/ui/LinesScreen.kt",
        "java/com/auroraclima/app/content/ui/TopicDetailScreen.kt",
        "java/com/auroraclima/app/content/ui/TopicsScreen.kt",
        "java/com/auroraclima/app/questionnaire/ui/GuidanceScreen.kt",
        "java/com/auroraclima/app/records/ui/JournalListScreen.kt",
        "java/com/auroraclima/app/records/ui/JournalEditorScreen.kt"
    )

    @Test
    fun `las features no importan iconos por fuera de la libreria cerrada`() {
        val violations = featureFiles()
            .filter { "androidx.compose.material.icons" in it.readText() }
            .map { it.relativeTo(mainDir).path }
            .filterNot { it in legacyMaterialIconsFiles }
            .toList()

        assertTrue(
            "Iconos de Material importados por fuera de la librería cerrada V2Icons " +
                "(regla 4 del anexo v2-29); la lista legada solo puede menguar:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}

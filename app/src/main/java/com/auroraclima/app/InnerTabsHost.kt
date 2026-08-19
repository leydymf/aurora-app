package com.auroraclima.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroraclima.app.access.ui.PanelScreen
import com.auroraclima.app.content.ui.GuideHomeScreen
import com.auroraclima.app.core.ui.v2.AccentChip
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2TabBar
import com.auroraclima.app.core.ui.v2.V2TabItem
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.records.ui.JournalListRoute

/**
 * Pestañas del espacio interno — Inicio · Guía · Mi registro (v2-10, decisión D3;
 * Ajustes se abre desde el engranaje del Inicio). Vive en el paquete raíz porque
 * compone varias features, el único nivel donde eso está permitido (§2 de la guía).
 *
 * La pestaña seleccionada es ESTADO LOCAL de la única entrada `panel` del grafo
 * seguro, no una ruta: cambiar de pestaña no navega. Consecuencias deliberadas:
 * - RNF-02/RF-16 por construcción: una sola `screen()` envuelve las tres pestañas —
 *   el bloqueo de capturas, la nube de salida y el reporte de interacción no cambian
 *   de mecanismo.
 * - Cuando `SecureBackStackGuard`/`SessionGuard` hacen pop a la fachada, este estado
 *   muere con la entrada: el reingreso con la clave SIEMPRE cae en Inicio, sin rastro
 *   de qué pestaña estaba abierta (RF-06).
 * - Atrás no cambia de pestaña: sale del grafo a la fachada, como hoy desde el panel.
 *
 * Los ViewModels de las pestañas (hoy solo Mi registro: la portada de Guía y el
 * Inicio son estáticos) quedan scoped a la entrada `panel` y el
 * [rememberSaveableStateHolder] conserva scroll y estado de UI al alternar pestañas;
 * todo muere junto con el pop del grafo (salida rápida, inactividad, background).
 */
@Composable
fun InnerTabsHost(
    isOffline: Boolean,
    onOpenGuidance: () -> Unit,
    onOpenGuidanceHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTopics: () -> Unit,
    onOpenRoutes: () -> Unit,
    onOpenLines: () -> Unit,
    onAddJournalEntry: () -> Unit,
    onOpenJournalEntry: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(HOME_TAB) }
    val tabStateHolder = rememberSaveableStateHolder()
    AuroraV2Theme(V2Voice.Interior) {
        Column(modifier = modifier.fillMaxSize()) {
            // Indicador sobrio del modo local (P-31 opción C): sin jerga técnica
            // y con icono además del color (regla 3 — el color nunca informa solo).
            if (isOffline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AccentChip(
                        text = stringResource(R.string.access_offline_chip),
                        accent = V2Palette.accentGroup,
                        iconRes = V2Icons.waiting
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    GUIDE_TAB -> tabStateHolder.SaveableStateProvider(GUIDE_TAB) {
                        // Portada de la Guía (v2-11): las tres secciones son
                        // destinos del grafo seguro; la pestaña solo agrupa.
                        GuideHomeScreen(
                            onOpenTopics = onOpenTopics,
                            onOpenRoutes = onOpenRoutes,
                            onOpenLines = onOpenLines
                        )
                    }
                    JOURNAL_TAB -> tabStateHolder.SaveableStateProvider(JOURNAL_TAB) {
                        JournalListRoute(
                            onBack = null,
                            onAddEntry = onAddJournalEntry,
                            onEntrySelected = onOpenJournalEntry
                        )
                    }
                    else -> tabStateHolder.SaveableStateProvider(HOME_TAB) {
                        // Accesos rápidos de P09 (v2-10): Llamar → líneas de
                        // atención (RF-23), guardar → editor del registro
                        // (RF-32/33), Informarme → pestaña Guía (RF-21). Todo
                        // por rutas o estado local — sin imports entre features.
                        PanelScreen(
                            onStartGuidance = onOpenGuidance,
                            onOpenHistory = onOpenGuidanceHistory,
                            onOpenSettings = onOpenSettings,
                            onCall = onOpenLines,
                            onSaveNote = onAddJournalEntry,
                            onLearn = { selectedTab = GUIDE_TAB }
                        )
                    }
                }
            }
            V2TabBar(
                items = listOf(
                    V2TabItem(V2Icons.home, stringResource(R.string.access_tab_home)),
                    V2TabItem(V2Icons.guide, stringResource(R.string.access_tab_guide)),
                    V2TabItem(V2Icons.journal, stringResource(R.string.access_tab_journal))
                ),
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it }
            )
        }
    }
}

private const val HOME_TAB = 0
private const val GUIDE_TAB = 1
private const val JOURNAL_TAB = 2

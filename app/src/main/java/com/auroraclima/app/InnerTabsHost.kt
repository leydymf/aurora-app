package com.auroraclima.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.auroraclima.app.access.ui.HomeTourHost
import com.auroraclima.app.access.ui.HomeTourStep
import com.auroraclima.app.access.ui.PanelScreen
import com.auroraclima.app.content.ui.GuideHomeScreen
import com.auroraclima.app.core.ui.v2.AccentChip
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2Icons
import com.auroraclima.app.core.ui.v2.V2Palette
import com.auroraclima.app.core.ui.v2.V2TabBar
import com.auroraclima.app.core.ui.v2.V2TabItem
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.core.ui.v2.spotlightTarget
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
 *
 * El recorrido por Inicio (v0.3.3, [HomeTourHost]) envuelve las pestañas y su barra:
 * ilumina elementos de Inicio, dos pestañas de la barra y la nube. Su estado también
 * es de esta entrada; [replayTour] llega de Ajustes y se apaga con [onReplayTourHandled].
 * Los destinos llegan juntos en [actions].
 */
@Composable
fun InnerTabsHost(
    isOffline: Boolean,
    actions: InnerTabsActions,
    replayTour: Boolean,
    onReplayTourHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(HOME_TAB) }
    val tabStateHolder = rememberSaveableStateHolder()
    AuroraV2Theme(V2Voice.Interior) {
        HomeTourHost(
            replayRequested = replayTour,
            onReplayHandled = onReplayTourHandled,
            onShowHome = { selectedTab = HOME_TAB }
        ) {
            InnerTabsLayout(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                modifier = modifier,
                isOffline = isOffline
            ) {
                TabPage(
                    selectedTab = selectedTab,
                    tabStateHolder = tabStateHolder,
                    actions = actions,
                    onShowGuide = { selectedTab = GUIDE_TAB }
                )
            }
        }
    }
}

/**
 * La maquetación de las pestañas: el chip sin conexión, la pestaña elegida ([page]) y
 * la barra. La usan [InnerTabsHost] y las pruebas que necesitan la composición real (la
 * franja de la nube, el recorrido por Inicio), para que no haya copias que diverjan.
 */
@Composable
internal fun InnerTabsLayout(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isOffline: Boolean = false,
    page: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        OfflineChip(isOffline)
        Box(modifier = Modifier.weight(1f)) { page() }
        InnerTabBar(selectedTab = selectedTab, onSelect = onSelectTab)
    }
}

/**
 * Los destinos del espacio interno que se abren desde las pestañas; los decide la
 * navegación raíz (guardia RF-06). Agrupados para que pasen de una vez.
 */
class InnerTabsActions(
    val onOpenGuidance: () -> Unit,
    val onOpenGuidanceHistory: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenTopics: () -> Unit,
    val onOpenRoutes: () -> Unit,
    val onOpenLines: () -> Unit,
    val onAddJournalEntry: () -> Unit,
    val onOpenJournalEntry: (Long) -> Unit,
    val onExportJournalEntries: (List<Long>) -> Unit
)

/**
 * Indicador sobrio del modo local (P-31 opción C): sin jerga técnica y con icono
 * además del color (regla 3 — el color nunca informa solo).
 */
@Composable
private fun OfflineChip(isOffline: Boolean) {
    if (!isOffline) return
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

/** La pestaña elegida, con su estado guardado en [tabStateHolder] al alternar. */
@Composable
private fun TabPage(
    selectedTab: Int,
    tabStateHolder: SaveableStateHolder,
    actions: InnerTabsActions,
    onShowGuide: () -> Unit
) {
    when (selectedTab) {
        GUIDE_TAB -> tabStateHolder.SaveableStateProvider(GUIDE_TAB) {
            // Portada de la Guía (v2-11): las tres secciones son
            // destinos del grafo seguro; la pestaña solo agrupa.
            GuideHomeScreen(
                onOpenTopics = actions.onOpenTopics,
                onOpenRoutes = actions.onOpenRoutes,
                onOpenLines = actions.onOpenLines
            )
        }
        JOURNAL_TAB -> tabStateHolder.SaveableStateProvider(JOURNAL_TAB) {
            JournalListRoute(
                onBack = null,
                onAddEntry = actions.onAddJournalEntry,
                onEntrySelected = actions.onOpenJournalEntry,
                onExportSelected = actions.onExportJournalEntries
            )
        }
        else -> tabStateHolder.SaveableStateProvider(HOME_TAB) {
            // Accesos rápidos de P09 (v2-10): Llamar → líneas de atención (RF-23),
            // guardar → editor del registro (RF-32/33), Informarme → pestaña Guía
            // (RF-21). Todo por rutas o estado local — sin imports entre features.
            PanelScreen(
                onStartGuidance = actions.onOpenGuidance,
                onOpenHistory = actions.onOpenGuidanceHistory,
                onOpenSettings = actions.onOpenSettings,
                onCall = actions.onOpenLines,
                onSaveNote = actions.onAddJournalEntry,
                onLearn = onShowGuide
            )
        }
    }
}

/**
 * La barra Inicio · Guía · Mi registro. Guía y Mi registro son dos pasos del
 * recorrido por Inicio: aquí se marcan, en el único sitio que sabe qué pestaña es
 * cuál.
 */
@Composable
private fun InnerTabBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    V2TabBar(
        items = listOf(
            V2TabItem(V2Icons.home, stringResource(R.string.access_tab_home)),
            V2TabItem(V2Icons.guide, stringResource(R.string.access_tab_guide)),
            V2TabItem(V2Icons.journal, stringResource(R.string.access_tab_journal))
        ),
        selectedIndex = selectedTab,
        onSelect = onSelect,
        itemModifier = { index ->
            when (index) {
                GUIDE_TAB -> Modifier.spotlightTarget(HomeTourStep.GUIDE)
                JOURNAL_TAB -> Modifier.spotlightTarget(HomeTourStep.JOURNAL)
                else -> Modifier
            }
        }
    )
}

// Índices de las pestañas; los usan también las pruebas que arman la maquetación real.
internal const val HOME_TAB = 0
internal const val GUIDE_TAB = 1
internal const val JOURNAL_TAB = 2

package com.auroraclima.app

import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auroraclima.app.access.ui.CodeSetupRoute
import com.auroraclima.app.access.ui.PanelScreen
import com.auroraclima.app.access.ui.TutorialRoute
import com.auroraclima.app.accounts.ui.ConsentRoute
import com.auroraclima.app.accounts.ui.CreateAccountRoute
import com.auroraclima.app.accounts.ui.LoginRoute
import com.auroraclima.app.content.ui.AGENCY_ID_ARG
import com.auroraclima.app.content.ui.AgencyDetailRoute
import com.auroraclima.app.content.ui.LinesRoute
import com.auroraclima.app.content.ui.ROUTE_ID_ARG
import com.auroraclima.app.content.ui.RoutesRoute
import com.auroraclima.app.content.ui.TOPIC_ID_ARG
import com.auroraclima.app.content.ui.TopicDetailRoute
import com.auroraclima.app.content.ui.TopicsRoute
import com.auroraclima.app.core.security.LocalSessionManager
import com.auroraclima.app.core.security.SearchGateResult
import com.auroraclima.app.core.security.SecureBackStackGuard
import com.auroraclima.app.core.security.SessionGuard
import com.auroraclima.app.core.security.SessionManager
import com.auroraclima.app.core.security.secureGraph
import com.auroraclima.app.questionnaire.ui.GuidanceRoute
import com.auroraclima.app.records.ui.JournalEditorRoute
import com.auroraclima.app.weather.ui.WeatherScreen
import com.auroraclima.app.weather.ui.WeatherViewModel

/**
 * Navegación raíz: la fachada de clima es el inicio y el único camino hacia el
 * espacio interno son los eventos de la puerta de búsqueda (RF-05 criterio 2, RF-12).
 * Las rutas usan nombres neutros porque quedan legibles en el APK (RNF-05).
 */
@Composable
fun AuroraApp(sessionManager: SessionManager) {
    val navController = rememberNavController()
    CompositionLocalProvider(LocalSessionManager provides sessionManager) {
        // popEnterTransition None: la fachada reaparece en el mismo frame del pop de
        // la salida rápida — «sin animaciones» (RF-16 criterio 2, decisión F2).
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE
        ) {
            composable(HOME_ROUTE, popEnterTransition = { EnterTransition.None }) { HomeRoute(navController) }
            // Toda ruta interna vive dentro del grafo seguro: bloqueo de capturas y
            // de recientes, botón nube de salida rápida y reporte de interacción
            // garantizados por construcción (RNF-02, RF-16, RNF-03).
            secureGraph(route = INNER_ROUTE, startDestination = INTRO_ROUTE) {
                // Primer uso (RF-05 → RF-17 → RF-07 → RF-08): consentimiento,
                // cuenta y código. Rechazar el consentimiento converge en la
                // salida rápida (RF-18); las pantallas no navegan por sí mismas —
                // los destinos se deciden solo aquí (guardia RF-06).
                screen(INTRO_ROUTE) {
                    ConsentRoute(onAccepted = { navController.navigate(REGISTER_ROUTE) })
                }
                screen(REGISTER_ROUTE) {
                    CreateAccountRoute(
                        onAccountCreated = {
                            // Atrás no debe regresar al formulario ni al
                            // consentimiento ya otorgado.
                            navController.navigate(SETUP_ROUTE) {
                                popUpTo(INTRO_ROUTE) { inclusive = true }
                            }
                        }
                    )
                }
                screen(SETUP_ROUTE) {
                    CodeSetupRoute(
                        onCodeCreated = {
                            // Atrás no debe regresar al formulario de la clave ya creada.
                            navController.navigate(GUIDE_ROUTE) {
                                popUpTo(SETUP_ROUTE) { inclusive = true }
                            }
                        }
                    )
                }
                // Paso 3 de 3: tutorial con práctica real (RF-10). No navega a
                // ningún lado por sí mismo: terminar ES salir con la nube (la
                // práctica de salida usa la salida real del grafo seguro) y el
                // reingreso con la clave ejercita RF-12 de verdad.
                screen(GUIDE_ROUTE) {
                    // onClose inalcanzable en primer uso: la primera página no
                    // muestra «Atrás» (showBack en TutorialScreen) y detrás ya
                    // no hay nada — el setup se sacó de la pila al navegar aquí.
                    TutorialRoute(isReview = false, onClose = {})
                }
                // Reapertura del tutorial (RF-11): mismo contenido en modo
                // repaso; «Atrás» en la primera página regresa a donde estaba.
                // La futura pantalla de configuración navegará a esta ruta.
                screen(GUIDE_REVIEW_ROUTE) {
                    TutorialRoute(isReview = true, onClose = { navController.popBackStack() })
                }
                // Reingreso (RF-12, RF-15): con sesión vigente salta directo al
                // panel; sin sesión, inicio de sesión con bloqueo temporal.
                screen(ENTRY_ROUTE) {
                    LoginRoute(
                        onAccessGranted = {
                            // El formulario no debe quedar en el back stack.
                            navController.navigate(PANEL_ROUTE) {
                                popUpTo(ENTRY_ROUTE) { inclusive = true }
                            }
                        }
                    )
                }
                // Hogar del espacio interno (P09) y módulo de información
                // (RF-21, RF-22): tipos → detalle → sectores → entidad.
                screen(PANEL_ROUTE) {
                    PanelScreen(
                        onOpenInfo = { navController.navigate(TOPICS_ROUTE) },
                        onOpenGuidance = { navController.navigate(GUIDANCE_ROUTE) }
                    )
                }
                // Cuestionario orientador (RF-27): una sola ruta — el flujo
                // pregunta a pregunta es estado del ViewModel, que muere con el
                // pop de esta entrada (las respuestas jamás tocan disco, RF-28).
                screen(GUIDANCE_ROUTE) {
                    GuidanceRoute(
                        onClose = { navController.popBackStack() },
                        // Acceso a las rutas recomendadas (RF-29 criterio 2):
                        // hacia content por navegación, nunca por import (§2).
                        onOpenRoutes = { navController.navigate(ROUTES_ROUTE) }
                    )
                }
                // Creación de un registro del historial (RF-32, P19). El pop
                // tras guardar regresa al listado (RF-34), que se refresca al
                // volver a componerse.
                screen(JOURNAL_NEW_ROUTE) {
                    JournalEditorRoute(
                        onSaved = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
                screen(TOPICS_ROUTE) {
                    TopicsRoute(
                        onBack = { navController.popBackStack() },
                        onTopicSelected = { topicId ->
                            navController.navigate("$TOPICS_ROUTE/$topicId")
                        },
                        onOpenRoutes = { navController.navigate(ROUTES_ROUTE) },
                        onOpenLines = { navController.navigate(LINES_ROUTE) }
                    )
                }
                screen(TOPIC_DETAIL_ROUTE) {
                    TopicDetailRoute(
                        onBack = { navController.popBackStack() },
                        onOpenRoutes = { navController.navigate(ROUTES_ROUTE) }
                    )
                }
                screen(ROUTES_ROUTE) {
                    RoutesRoute(
                        onBack = { navController.popBackStack() },
                        onAgencySelected = { routeId, agencyId ->
                            navController.navigate("$ROUTES_ROUTE/$routeId/$agencyId")
                        }
                    )
                }
                screen(AGENCY_DETAIL_ROUTE) {
                    AgencyDetailRoute(onBack = { navController.popBackStack() })
                }
                screen(LINES_ROUTE) {
                    LinesRoute(onBack = { navController.popBackStack() })
                }
            }
        }
    }
    // Al pasar a background dentro del grafo seguro, la pila vuelve a la fachada:
    // recientes, muerte de proceso y botón atrás nunca reabren el espacio interno
    // sin código (RF-06, auditoría M-02 / P-10).
    SecureBackStackGuard(
        navController = navController,
        secureGraphRoute = INNER_ROUTE,
        facadeRoute = HOME_ROUTE
    )
    // Sesión del espacio interno: entrar al grafo la inicia; salida rápida,
    // inactividad de 5 min o background prolongado la cierran y hacen pop a la
    // fachada (RF-16, RNF-03).
    SessionGuard(
        navController = navController,
        sessionManager = sessionManager,
        secureGraphRoute = INNER_ROUTE,
        facadeRoute = HOME_ROUTE
    )
}

/** Fachada de clima (RF-01..03) y puerta de búsqueda hacia el espacio interno (RF-12). */
@Composable
private fun HomeRoute(navController: NavHostController) {
    val viewModel: WeatherViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    // Mejor esfuerzo: limpiar la barra en ON_PAUSE para que la miniatura de
    // recientes no muestre un código a medio teclear (RF-14, auditoría M-03/M-04).
    // El snapshot lo toma el sistema del último frame visible, así que ON_STOP
    // llegaría tarde; la verificación en emulador queda en P-14 de PENDIENTES.md.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.onSearchCancel()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewModel) {
        viewModel.gateEvents.collect { destination ->
            when (destination) {
                SearchGateResult.OpenActivation -> navController.navigate(INTRO_ROUTE)
                SearchGateResult.OpenAccess -> navController.navigate(ENTRY_ROUTE)
                SearchGateResult.PassThrough -> Unit
            }
        }
    }
    WeatherScreen(
        state = state,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onCitySelected = viewModel::onCitySelected,
        onSearchCancel = viewModel::onSearchCancel
    )
}

private const val HOME_ROUTE = "home"
private const val INNER_ROUTE = "inner"
private const val INTRO_ROUTE = "intro"
private const val REGISTER_ROUTE = "register"
private const val SETUP_ROUTE = "setup"
private const val GUIDE_ROUTE = "guide"
private const val GUIDE_REVIEW_ROUTE = "guide_review"
private const val ENTRY_ROUTE = "entry"
private const val PANEL_ROUTE = "panel"
private const val GUIDANCE_ROUTE = "guidance"
private const val JOURNAL_NEW_ROUTE = "journal_new"
private const val TOPICS_ROUTE = "topics"
private const val TOPIC_DETAIL_ROUTE = "$TOPICS_ROUTE/{$TOPIC_ID_ARG}"
private const val ROUTES_ROUTE = "routes"
private const val AGENCY_DETAIL_ROUTE = "$ROUTES_ROUTE/{$ROUTE_ID_ARG}/{$AGENCY_ID_ARG}"
private const val LINES_ROUTE = "lines"

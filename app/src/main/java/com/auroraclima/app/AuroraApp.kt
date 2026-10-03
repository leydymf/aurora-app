package com.auroraclima.app

import androidx.compose.animation.EnterTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.auroraclima.app.access.ui.ChangeCodeRoute
import com.auroraclima.app.access.ui.CodeSetupRoute
import com.auroraclima.app.access.ui.SettingsScreen
import com.auroraclima.app.access.ui.TutorialRoute
import com.auroraclima.app.accounts.ui.ChangePasswordRoute
import com.auroraclima.app.accounts.ui.ConsentRoute
import com.auroraclima.app.accounts.ui.CreateAccountRoute
import com.auroraclima.app.accounts.ui.DeleteAccountRoute
import com.auroraclima.app.accounts.ui.LoginRoute
import com.auroraclima.app.accounts.ui.PrivacyScreen
import com.auroraclima.app.accounts.ui.SignOutViewModel
import com.auroraclima.app.accounts.ui.SilentSessionViewModel
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
import com.auroraclima.app.core.ui.v2.AuroraV2Theme
import com.auroraclima.app.core.ui.v2.V2TabBarHeight
import com.auroraclima.app.core.ui.v2.V2Voice
import com.auroraclima.app.questionnaire.ui.GuidanceHistoryRoute
import com.auroraclima.app.questionnaire.ui.GuidanceRoute
import com.auroraclima.app.records.ui.ENTRY_IDS_ARG
import com.auroraclima.app.records.ui.ENTRY_ID_ARG
import com.auroraclima.app.records.ui.JournalDetailRoute
import com.auroraclima.app.records.ui.JournalEditorRoute
import com.auroraclima.app.records.ui.JournalExportRoute
import com.auroraclima.app.records.ui.JournalMediaRoute
import com.auroraclima.app.records.ui.MEDIA_ID_ARG
import com.auroraclima.app.weather.ui.ForecastRoute
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
    // Observación del reingreso silencioso (P-31 opción C), scoped a la Activity:
    // sobrevive a los destinos del grafo seguro para poder interponer el login.
    val silentSession: SilentSessionViewModel = hiltViewModel()
    CompositionLocalProvider(LocalSessionManager provides sessionManager) {
        // popEnterTransition None: la fachada reaparece en el mismo frame del pop de
        // la salida rápida — «sin animaciones» (RF-16 criterio 2, decisión F2).
        NavHost(
            navController = navController,
            startDestination = HOME_ROUTE
        ) {
            composable(HOME_ROUTE, popEnterTransition = { EnterTransition.None }) { entry ->
                HomeRoute(navController = navController, entry = entry)
            }
            // Pronóstico extendido (RF-04): pantalla de la fachada, fuera del
            // grafo seguro — refuerza la credibilidad de la app de clima (RNF-05).
            composable(FORECAST_ROUTE) {
                ForecastRoute(
                    onBack = { navController.popBackStack() },
                    // «Lugares» (D4): volver a P01 y abrir la búsqueda; la señal
                    // viaja por el savedStateHandle del destino de la fachada.
                    onOpenPlaces = {
                        navController.previousBackStackEntry?.savedStateHandle?.set(FOCUS_SEARCH_KEY, true)
                        navController.popBackStack()
                    }
                )
            }
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
                        },
                        // RF-39: cuenta existente en un dispositivo nuevo —
                        // inicia sesión dentro del flujo de activación; «Atrás»
                        // regresa al formulario de creación.
                        onOpenLogin = { navController.navigate(ENTRY_SETUP_ROUTE) }
                    )
                }
                // Inicio de sesión del flujo de activación (RF-39): mismo login
                // de RF-15, pero al conceder acceso continúa a crear el código
                // nuevo (RF-08) — en este dispositivo aún no existe. La llave
                // del respaldo y la restauración ya corrieron dentro del caso
                // de uso del login; nada de la pila anterior debe quedar.
                screen(ENTRY_SETUP_ROUTE) {
                    LoginRoute(
                        onAccessGranted = {
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
                    TutorialRoute(
                        isReview = false,
                        onClose = {},
                        // Saltar por ahora (refinamiento RF-10, 2026-08): aterriza
                        // donde termina el flujo completo — el panel (tras la
                        // práctica, el reingreso con sesión vigente salta directo
                        // allí). La sesión ya existe: la creó RF-07 o el login de
                        // RF-39. El tutorial no queda en la pila, igual que el
                        // reingreso normal (ENTRY → PANEL).
                        onSkip = {
                            navController.navigate(PANEL_ROUTE) {
                                popUpTo(GUIDE_ROUTE) { inclusive = true }
                            }
                        }
                    )
                }
                // Reapertura del tutorial (RF-11): mismo contenido en modo
                // repaso; «Atrás» en la primera página regresa a donde estaba.
                // En repaso no se ofrece «Saltar» (showsSkip); si llegara a
                // emitirse, converge con el mismo regreso que onClose.
                screen(GUIDE_REVIEW_ROUTE) {
                    TutorialRoute(
                        isReview = true,
                        onClose = { navController.popBackStack() },
                        onSkip = { navController.popBackStack() }
                    )
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
                // Hogar del espacio interno: pestañas Inicio · Guía · Mi registro
                // (v2-10, D3) como estado local de esta única entrada — cambiar de
                // pestaña no navega y el estado muere con el pop del grafo (RF-06).
                // El inset eleva la nube por encima de la barra de pestañas (RF-16).
                screen(PANEL_ROUTE, cloudBottomInset = V2TabBarHeight) {
                    val isOffline by silentSession.isOffline.collectAsState()
                    InnerTabsHost(
                        isOffline = isOffline,
                        onOpenGuidance = { navController.navigate(GUIDANCE_ROUTE) },
                        onOpenGuidanceHistory = { navController.navigate(GUIDANCE_HISTORY_ROUTE) },
                        onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                        onOpenTopics = { navController.navigate(TOPICS_ROUTE) },
                        onOpenRoutes = { navController.navigate(ROUTES_ROUTE) },
                        onOpenLines = { navController.navigate(LINES_ROUTE) },
                        onAddJournalEntry = { navController.navigate(JOURNAL_NEW_ROUTE) },
                        onOpenJournalEntry = { entryId ->
                            navController.navigate("$JOURNAL_ROUTE/$entryId")
                        },
                        // Exportación selectiva (RF-45): solo ids opacos viajan
                        // por la ruta; la pantalla vuelve a leer todo lo demás.
                        onExportJournalEntries = { ids ->
                            navController.navigate("$JOURNAL_EXPORT_BASE/${ids.joinToString(",")}")
                        }
                    )
                }
                // Configuración del espacio interno (P21): reabrir el tutorial
                // (RF-11) reutiliza la ruta de repaso ya prevista.
                screen(SETTINGS_ROUTE) {
                    // RF-19 con la opción C de P-31: el cierre explícito destruye
                    // primero el blob del reingreso silencioso (SignOutViewModel)
                    // y después converge en la rutina única de cierre (quickExit):
                    // AuthSessionCleaner cierra la sesión del proveedor —el
                    // siguiente acceso exige código + credenciales, criterio 1— y
                    // SessionGuard hace el pop a la fachada (criterio 2).
                    val signOutViewModel: SignOutViewModel = hiltViewModel()
                    LaunchedEffect(signOutViewModel) {
                        signOutViewModel.doneEvents.collect { sessionManager.quickExit() }
                    }
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onChangeCode = { navController.navigate(SETTINGS_CODE_ROUTE) },
                        // RF-39: cambio de contraseña (re-envuelve la DEK, no
                        // re-cifra registros); pantalla de accounts.
                        onChangePassword = { navController.navigate(SETTINGS_PASSWORD_ROUTE) },
                        onReopenGuide = { navController.navigate(GUIDE_REVIEW_ROUTE) },
                        onOpenPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                        onSignOut = signOutViewModel::onSignOut,
                        onDeleteAccount = { navController.navigate(SETTINGS_ACCOUNT_ROUTE) }
                    )
                }
                // Eliminación total de la cuenta (RF-20): pantalla propia de
                // accounts con doble confirmación. Al terminar converge en la
                // misma rutina única de cierre — el regreso al clima es la única
                // señal y la limpieza de memoria corre con los cleaners.
                screen(SETTINGS_ACCOUNT_ROUTE) {
                    DeleteAccountRoute(
                        onBack = { navController.popBackStack() },
                        onDeleted = sessionManager::quickExit
                    )
                }
                // Cambio de la clave personal (RF-09, P21·b): guardar regresa a
                // configuración; el formulario no queda en la pila.
                screen(SETTINGS_CODE_ROUTE) {
                    ChangeCodeRoute(
                        onCodeChanged = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
                // Cambio de la contraseña de la cuenta (RF-39): guardar regresa
                // a configuración; el formulario no queda en la pila.
                screen(SETTINGS_PASSWORD_ROUTE) {
                    ChangePasswordRoute(
                        onPasswordChanged = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
                // Relectura del acuerdo de datos (RF-17 criterio 2, P-16): el
                // mismo texto del primer uso, en solo lectura.
                screen(PRIVACY_ROUTE) {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }
                // Historial personal (RF-34, P18): el listado es la pestaña
                // «Mi registro» del hogar; aquí solo viven detalle y creación.
                screen(JOURNAL_DETAIL_ROUTE) {
                    JournalDetailRoute(
                        onBack = { navController.popBackStack() },
                        // Edición (RF-35): el mismo editor de creación, con id.
                        onEdit = { entryId ->
                            navController.navigate("$JOURNAL_EDIT_BASE/$entryId")
                        },
                        // Eliminación (RF-36): el pop regresa al listado, que se
                        // refresca al volver a componerse — sin mensaje de éxito.
                        onDeleted = { navController.popBackStack() },
                        // Adjuntos (RF-33): visor interno, dentro del grafo seguro.
                        onOpenAttachment = { mediaId ->
                            navController.navigate("$JOURNAL_MEDIA_BASE/$mediaId")
                        }
                    )
                }
                // Visor interno de un adjunto (RF-33 criterio 2): imagen o PDF,
                // sin salir jamás del grafo seguro (RNF-02).
                screen(JOURNAL_MEDIA_ROUTE) {
                    JournalMediaRoute(onBack = { navController.popBackStack() })
                }
                // Exportación a PDF con contraseña (RF-45): resumen, aviso,
                // contraseña y salida por el selector del sistema. Dentro del
                // grafo seguro: captura bloqueada y nube por construcción.
                screen(JOURNAL_EXPORT_ROUTE) {
                    JournalExportRoute(onBack = { navController.popBackStack() })
                }
                // Edición de un registro (RF-35, P19). El pop tras guardar
                // regresa al detalle, que se refresca al volver a componerse.
                screen(JOURNAL_EDIT_ROUTE) {
                    JournalEditorRoute(
                        onSaved = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
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
                // Resultados guardados del cuestionario (RF-31): listado con
                // detalle en hoja segura y eliminación con doble confirmación.
                screen(GUIDANCE_HISTORY_ROUTE) {
                    GuidanceHistoryRoute(onBack = { navController.popBackStack() })
                }
                // Creación de un registro del historial (RF-32, P19). El pop
                // tras guardar regresa al hub en la pestaña donde estaba la
                // usuaria (Mi registro se refresca al volver a componerse; desde
                // «Guardar una nota» de P09, al Inicio).
                screen(JOURNAL_NEW_ROUTE) {
                    JournalEditorRoute(
                        onSaved = { navController.popBackStack() },
                        onBack = { navController.popBackStack() }
                    )
                }
                // Sección «Entender lo que pasa» de la Guía (RF-21): la lista
                // de tipos es un destino propio desde la portada (v2-11/v2-12).
                screen(TOPICS_ROUTE) {
                    TopicsRoute(
                        onBack = { navController.popBackStack() },
                        onTopicSelected = { topicId ->
                            navController.navigate("$TOPICS_ROUTE/$topicId")
                        }
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
    // Rechazo definitivo del reingreso silencioso (P-31 opción C): se interpone
    // la pantalla de contraseña SOLO si se sigue dentro del grafo seguro (desde
    // la fachada jamás debe abrirse sola — RNF-05; además el cleaner cancela el
    // coordinador antes del pop, así que este caso es cinturón y tirantes). Si
    // el login ya está a la vista (rechazo antes de llegar al panel), no se
    // apila otro: el estado queda en LoginRequired y el siguiente reingreso
    // pide credenciales porque el blob ya fue destruido.
    LaunchedEffect(silentSession, navController) {
        silentSession.loginRequests.collect {
            val destination = navController.currentBackStackEntry?.destination ?: return@collect
            val insideSecureGraph = destination.hierarchy.any { it.route == INNER_ROUTE }
            val atLogin = destination.route == ENTRY_ROUTE || destination.route == ENTRY_SETUP_ROUTE
            if (insideSecureGraph && !atLogin) {
                navController.navigate(ENTRY_ROUTE) {
                    popUpTo(PANEL_ROUTE) { inclusive = true }
                }
            }
        }
    }
    // Al pasar a background dentro del grafo seguro, la pila vuelve a la fachada:
    // recientes, muerte de proceso y botón atrás nunca reabren el espacio interno
    // sin código (RF-06, auditoría M-02 / P-10). Única excepción: la ida-y-vuelta
    // esperada a un selector o cámara del sistema (RF-33) — de un solo uso, con
    // caducidad y siempre bajo los plazos de la sesión.
    SecureBackStackGuard(
        navController = navController,
        secureGraphRoute = INNER_ROUTE,
        facadeRoute = HOME_ROUTE,
        suppressStopPop = sessionManager::isAwaitingSystemReturn
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
private fun HomeRoute(navController: NavHostController, entry: NavBackStackEntry) {
    val viewModel: WeatherViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val searchFocus = remember { FocusRequester() }
    // Señal de un solo uso de la pestaña «Lugares» del pronóstico (D4): al volver
    // a la fachada se enfoca la barra — la misma puerta del código (RF-05/RF-12).
    LaunchedEffect(entry) {
        if (entry.savedStateHandle.remove<Boolean>(FOCUS_SEARCH_KEY) == true) {
            searchFocus.requestFocus()
        }
    }
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
    // Voz de FACHADA del tema V2: paleta compartida, tipografía neutra del
    // sistema — jamás Marcellus aquí (decisión F1, camuflaje RNF-05).
    AuroraV2Theme(V2Voice.Facade) {
        WeatherScreen(
            state = state,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onCitySelected = viewModel::onCitySelected,
            onSearchCancel = viewModel::onSearchCancel,
            onOpenForecast = { navController.navigate(FORECAST_ROUTE) },
            searchFocus = searchFocus
        )
    }
}

private const val HOME_ROUTE = "home"

/** Señal de «Lugares» → fachada con la búsqueda abierta (D4); nombre neutro (RNF-05). */
private const val FOCUS_SEARCH_KEY = "focus_search"
private const val FORECAST_ROUTE = "forecast"
private const val INNER_ROUTE = "inner"
private const val INTRO_ROUTE = "intro"
private const val REGISTER_ROUTE = "register"
private const val SETUP_ROUTE = "setup"
private const val ENTRY_SETUP_ROUTE = "entry_setup"
private const val GUIDE_ROUTE = "guide"
private const val GUIDE_REVIEW_ROUTE = "guide_review"
private const val ENTRY_ROUTE = "entry"
private const val PANEL_ROUTE = "panel"
private const val SETTINGS_ROUTE = "settings"
private const val PRIVACY_ROUTE = "privacy"
private const val SETTINGS_CODE_ROUTE = "settings_code"
private const val SETTINGS_PASSWORD_ROUTE = "settings_password"
private const val SETTINGS_ACCOUNT_ROUTE = "settings_account"
private const val GUIDANCE_ROUTE = "guidance"
private const val GUIDANCE_HISTORY_ROUTE = "guidance_history"
private const val JOURNAL_ROUTE = "journal"
private const val JOURNAL_NEW_ROUTE = "journal_new"
private const val JOURNAL_DETAIL_ROUTE = "$JOURNAL_ROUTE/{$ENTRY_ID_ARG}"
private const val JOURNAL_EDIT_BASE = "journal_edit"
private const val JOURNAL_EDIT_ROUTE = "$JOURNAL_EDIT_BASE/{$ENTRY_ID_ARG}"
private const val JOURNAL_MEDIA_BASE = "journal_media"
private const val JOURNAL_MEDIA_ROUTE = "$JOURNAL_MEDIA_BASE/{$MEDIA_ID_ARG}"
private const val JOURNAL_EXPORT_BASE = "journal_export"
private const val JOURNAL_EXPORT_ROUTE = "$JOURNAL_EXPORT_BASE/{$ENTRY_IDS_ARG}"
private const val TOPICS_ROUTE = "topics"
private const val TOPIC_DETAIL_ROUTE = "$TOPICS_ROUTE/{$TOPIC_ID_ARG}"
private const val ROUTES_ROUTE = "routes"
private const val AGENCY_DETAIL_ROUTE = "$ROUTES_ROUTE/{$ROUTE_ID_ARG}/{$AGENCY_ID_ARG}"
private const val LINES_ROUTE = "lines"

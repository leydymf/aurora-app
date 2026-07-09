# Aurora — Guía de Arquitectura y Diseño de Código

Reglas obligatorias. Toda implementación debe cumplirlas; los agentes de revisión las auditan.

## 1. Arquitectura general

**MVVM + Clean Architecture en 3 capas, organizado por feature:**

```
app/src/main/java/com/auroraclima/app/
├── core/                    # transversal, sin lógica de negocio de features
│   ├── security/            # Keystore, cifrado, FLAG_SECURE, limpieza de estado
│   ├── database/            # Room + SQLCipher (configuración)
│   ├── network/             # cliente HTTP, manejo de errores de red
│   └── ui/                  # tema, componentes Compose reutilizables
├── weather/                 # fachada (RF-01 a RF-04)
├── access/                  # doble fondo: código secreto, salida rápida (RF-05..16)
├── accounts/                # auth, consentimiento (RF-07, RF-15, RF-17..20)
├── content/                 # tipos de violencia, rutas, líneas (RF-21..26)
├── questionnaire/           # RF-27..31
└── records/                 # RF-32..39 (registro de incidentes; nombre neutro)
```

Cada feature tiene internamente:

```
<feature>/
├── ui/        # pantallas Compose + ViewModel
├── domain/    # modelos, interfaces de repositorio, casos de uso — Kotlin puro
└── data/      # implementaciones de repositorio, DAOs, DTOs, fuentes remotas
```

## 2. Regla de dependencias (innegociable)

- `ui` → `domain` ← `data`. La UI nunca importa de `data`; `domain` no importa de Android (salvo anotaciones), ni de Room, ni de Firebase.
- Las features no se importan entre sí. Si dos features necesitan algo común, va a `core/` o se comunica por navegación.
- Todo acceso a datos pasa por una interfaz de repositorio definida en `domain`. Prohibido llamar Firestore, Room o la API de clima directamente desde un ViewModel.

## 3. Patrones obligatorios

- **Estado de UI:** cada pantalla tiene un `data class XxxUiState` inmutable expuesto como `StateFlow` desde el ViewModel. Eventos de usuario como funciones del ViewModel. Nada de `LiveData` ni estado mutable compartido.
- **Errores:** las operaciones de `data` devuelven `Result<T>` (o sealed class propia). Las excepciones nunca cruzan hacia la UI. Los mensajes de error visibles jamás revelan el doble fondo (un fallo de sync se muestra como error genérico o silencioso).
- **Casos de uso:** obligatorios cuando la operación toca seguridad (validar código secreto, cifrar, limpiar estado) o combina más de un repositorio. Para lecturas simples, el ViewModel puede usar el repositorio directo.
- **DI con Hilt:** todo se inyecta; prohibidos los `object` singletons hechos a mano y las instancias creadas dentro de ViewModels.
- **Inmutabilidad:** `val` por defecto, colecciones inmutables, sin `!!`, sin `lateinit` fuera de tests/DI.

## 4. Seguridad por diseño (mapeo a RNF)

| RNF | Mecanismo | Dónde vive |
|---|---|---|
| RNF-01 cifrado en reposo | SQLCipher con llave en Keystore; nunca llaves en código o SharedPreferences planas | `core/db`, `core/seguridad` |
| RNF-02 capturas/recientes | `FLAG_SECURE` aplicado por un componente único que envuelve toda pantalla del doble fondo | `core/seguridad` |
| RNF-03 cierre automático | temporizador de sesión + limpieza en `onStop` prolongado; un solo `SesionManager` | `core/seguridad` |
| RNF-04 minimización | no pedir permisos ni datos que un RF no exija; revisar manifest en cada PR | manifest + revisión |
| RNF-05 camuflaje | strings, íconos, notificaciones y paquete solo de clima; los recursos del doble fondo usan nombres neutros | recursos + revisión |
| RNF-06 separación | reglas de seguridad de Firestore versionadas en el repo (`firestore.rules`) | raíz del repo |
| RNF-07/08 rendimiento/offline | persistencia Firestore activada, imágenes ligeras, sin librerías pesadas nuevas | gradle + revisión |

Reglas adicionales:

- Prohibido `Log.*`, `println` o crash reporting con datos del doble fondo. En release, logging deshabilitado por completo (proguard/lint lo verifica).
- Toda ruta de un feature interno se registra EXCLUSIVAMENTE dentro de `secureGraph { screen(...) }` (`core/security`). En el NavHost raíz solo se registran rutas de la fachada con `composable(...)`; `NoTracesGuardTest` lo vigila.
- Las APIs con ventana propia (Dialog, Popup, ModalBottomSheet, AlertDialog, Toast, pickers) no heredan la bandera de la Activity: solo se usan a través de los envoltorios de `core/security` (`SecureDialog`, y las variantes que se agreguen allí con `securePolicy = SecureOn`). `NoTracesGuardTest` las veda fuera de ese paquete.
- La validación del código secreto es de tiempo constante (comparar hashes, no strings) y ocurre en un caso de uso, no en la UI.
- Ningún texto sensible hardcodeado en `strings.xml` con nombres reveladores: usar claves neutras (`info_titulo_1`, no `violencia_titulo`).

## 5. Calidad y pruebas

- Cada RF implementado incluye como mínimo: test unitario del ViewModel o caso de uso, y test del repositorio con fuente falsa (fake, no mock de framework cuando sea posible).
- `./gradlew ktlintCheck test assembleDebug` debe pasar antes de cada commit.
- Funciones ≤ 30 líneas como guía; una clase = una responsabilidad; nombres en inglés descriptivos (`ValidateSecretCodeUseCase`, no `Util`).
- Composables sin lógica: solo pintan estado y emiten eventos.

## 6. Facilidad de cambio

- Feature nueva = paquete nuevo copiando la plantilla ui/domain/data. No tocar features existentes salvo por su interfaz pública.
- El contenido administrable (tipos, rutas, cuestionario) se modela con esquemas versionados (`schemaVersion` en los documentos de Firestore) para que el futuro panel web no rompa la app.
- Decisiones de arquitectura que cambien esta guía se registran al final de este archivo con fecha y motivo (mini-ADR).

## Registro de decisiones

- 2026-07-07 — v1 de esta guía, derivada de A2-1/A2-2 y los RNF de la especificación.
- 2026-07-07 — Paquetes renombrados de español a inglés neutro (`clima→weather`, `acceso→access`, `cuentas→accounts`, `contenido→content`, `cuestionario→questionnaire`, `incidentes→records`, `core/{seguridad→security, db→database, red→network}`). Motivo: hallazgo de auditoría de seguridad — los nombres de paquete quedan legibles en el APK y en stack traces (`incidentes`/`cuestionario` podían sugerir el propósito real), y CLAUDE.md exige inglés para nombres de código. `records` se eligió sobre `incidents` por ser el más neutro.
- 2026-07-07 — Firebase BOM fijado en 33.16.0 (no 34.x): la serie 34 se compila con Kotlin 2.3 y el proyecto usa Kotlin 2.0.0. Revaluar si se actualiza el toolchain de Kotlin.
- 2026-07-07 — La caché de clima (RF-02/RF-03) vive en una BD Room propia del feature (`weather/data/local/WeatherDatabase`, sin cifrar) en lugar de la futura BD central de `core/database`. Motivo: (a) ciudad y clima no son datos sensibles y una BD de clima en claro es lo esperable en una app meteorológica (RNF-05); (b) la BD central irá con SQLCipher (RNF-01) y mezclar ambas obligaría a cifrar datos de fachada o a que `core` dependa de entidades del feature, invirtiendo la regla de dependencias. `core/database` queda reservado para la configuración Room+SQLCipher del doble fondo.
- 2026-07-08 — Dependencias nuevas: `navigation-compose 2.8.9` y `hilt-navigation-compose 1.2.0`. Motivo: navegación multi-pantalla que necesita toda la semana (fachada ⇄ doble fondo, consentimiento→cuenta→código→tutorial); Jetpack oficial y liviano, cumple los criterios de A2-1. Las rutas usan nombres neutros porque quedan legibles en el APK (RNF-05).
- 2026-07-08 — Patrón "puerta de búsqueda" (RF-05/RF-12): el contrato `SearchQueryGate` vive en `core/security` y `access` lo implementa (`ValidateSecretCodeUseCase`), enlazado por Hilt. Es el mecanismo canónico cuando dos features deben cooperar sin importarse entre sí y la navegación no basta (§2). Regla asociada: cualquier fallo interno de la puerta degrada en silencio a `PassThrough` — la fachada jamás muestra un error causado por el doble fondo (RF-13, §3).
- 2026-07-08 — Multibinding `core/startup/AppInitializer` (Hilt `@Multibinds` + `@IntoSet` por build type) para tareas de arranque. Motivo: permite que la siembra provisional de códigos (TODO RF-08) exista solo en `src/debug` sin que `main` conozca nada de debug.
- 2026-07-08 — Excepción registrada a la regla §3 (sin `object` singletons manuales): `SecureFlagCounter` en `core/security/SecureScreen.kt` es un `object` privado con conteo mutable de pantallas seguras. Motivo: en transiciones de navegación entre dos pantallas seguras ambas conviven en composición y sin conteo el `onDispose` de la saliente borraría FLAG_SECURE de la entrante; es estado del `Window`, no lógica de negocio, y no gana testeabilidad inyectándose. *(→ ver 2026-07-09: el archivo se movió a `SecureNavigation.kt`.)*
- 2026-07-09 — Salida rápida y sesión del doble fondo (RF-16, RNF-03): `SessionManager` (`core/security`, singleton Hilt) es la única fuente del estado «desbloqueado» y el único dueño de la rutina de limpieza; todos los cierres convergen en `closeSession()` — salida rápida (`quickExit()`), 5 min de inactividad, background prolongado (gracia de 30 s tras ON_STOP sin rotación; la pila ya la limpió `SecureBackStackGuard` en el mismo ON_STOP, la gracia solo conserva el desbloqueo ante interrupciones breves), salir del grafo seguro en primer plano — back físico incluido — (auditoría D3 H-4) y los que vengan (RF-28 usará esta misma rutina). La gracia tiene doble mecanismo (auditoría D3 H-3): timer con `delay` más re-verificación al volver con `MonotonicClock` (`SystemClock.elapsedRealtime`, inyectado), porque `delay` se congela en suspensión profunda o proceso cacheado. Punto de extensión: `SensitiveStateCleaner` con multibinding `@IntoSet` (`SessionModule`) para singletons y `registerCleaner()` para objetos de vida corta; los ViewModels internos no necesitan cleaner — los destruye el pop del grafo. El botón nube (52 dp, decisión F2 de A2-6; reutiliza `ic_weather_cloud`, recurso de nombre 100 % meteorológico) lo pinta `SecureScreen` dentro de `secureGraph`: toda pantalla interna lo tiene por construcción, igual que FLAG_SECURE, y el mismo envoltorio observa los toques (pasada Initial, sin consumirlos) para reiniciar la inactividad. `SessionManager` llega a la composición vía `LocalSessionManager` (CompositionLocal provisto desde `MainActivity` con la instancia de Hilt — no es un singleton manual). Transiciones del grafo seguro y `popEnterTransition` de la fachada en `None`: entrada y salida instantáneas («sin animaciones», F2; RF-16 ≤1 s). `SessionGuard` (core/security) conecta sesión ⇄ navegación ⇄ ciclo de vida y comparte el pop `exitSecureGraph` con `SecureBackStackGuard`. Caveat: el teclado (IME) es una ventana propia y no genera eventos de puntero — los campos de texto internos deberán llamar `registerInteraction()` en sus cambios de texto (P-15).
- 2026-07-09 — FLAG_SECURE pasa de envoltorio por pantalla a grafo seguro (RNF-02): `core/security/SecureNavigation.kt` expone `NavGraphBuilder.secureGraph { screen(...) }` y `SecureScreen` es ahora `private` — las pantallas internas NO pueden (ni deben) aplicarlo a mano; registrar la ruta dentro del grafo es lo que la asegura. `SecureScreen.kt` desaparece; la excepción del 2026-07-08 sigue vigente para `SecureFlagCounter` (mismo archivo nuevo) y el conteo puro se extrajo a `SecureFlagLedger` para probarse por unidad. Se agrega `SecureBackStackGuard` (RF-06, hallazgo M-02): al pasar a background dentro del grafo seguro se hace pop hasta la fachada, y al restaurar (muerte de proceso, o saved-state pre-ON_STOP en API 26-27) se repite la comprobación; la rotación no expulsa. Guardias de regresión en `NoTracesGuardTest` (sin notificaciones/shortcuts/widgets/tiles; FLAG_SECURE solo en `core/security`; la fachada nunca lo referencia) y `ManifestMinimizationTest` (permisos = inventario, un solo componente exportado, `allowBackup=false`). *Adenda por auditoría (misma fecha):* el re-chequeo de restauración es síncrono (`DisposableEffect`, no `LaunchedEffect`) para que la pantalla interna no llegue a dibujarse ni un frame (A2); las ventanas propias (Dialog/Popup/BottomSheet/Toast) no heredan la bandera y quedan vedadas fuera de `core/security`, que expone `SecureDialog` con `securePolicy = SecureOn` (A1); el NavHost raíz solo puede registrar rutas de fachada — guardia en `NoTracesGuardTest` (M1). *Segunda pasada (N1/N2):* el pop de restauración solo actúa tras muerte de proceso — un token de vida del proceso en `rememberSaveable` distingue muerte de recreación, así la rotación no expulsa (N1; `ProcessToken` es `object` manual por el mismo criterio que la excepción de `SecureFlagCounter` del 2026-07-08: estado de plataforma sin lógica de negocio); la veda de ventanas propias incluye `DropdownMenu`/`TooltipBox`/`PopupWindow` (N2); los barridos cubren también `src/debug`, y `NavHost`/`composable` quedan confinados a `AuroraApp.kt` y `core/security`.

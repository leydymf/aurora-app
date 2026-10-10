# Aurora — Resultados de pruebas (cierre de `mejoras-v3` + tipos dinámicos + RF-45/RF-46 · previo al merge y al tag)

> Evidencia para el informe. Corrida del **2026-10-05** sobre el árbol completo
> (app + panel), rama `feat/tipos-dinamicos-y-exportacion` (34 commits sobre
> `main`; los 18 primeros son la rama `mejoras-v3`), previa a las corridas
> manuales finales, al merge a `main` y al tag que la usuaria decida crear
> después. Alcance funcional del diff `main..HEAD` (214 archivos,
> +11 225/−1 054): tutorial saltable (RF-10), esquema v2 de contenido con
> icono/acento elegibles desde el panel (RF-21/22/41/42), resultado del
> cuestionario con colores semafóricos (RF-29/30, reversión informada de P-41)
> y **guardado opcional del resultado (RF-31)**, **reingreso silencioso (P-31
> opción C)** con entrada local sin red (RF-12/15/19/20, RNF-08), **tipos del
> registro conectados a la Guía (P-68: Room v5, payload v3)**, **exportación
> selectiva a PDF con contraseña (RF-45)** y el contraste de campos y controles
> (WCAG 1.4.11), más los tres ajustes que dejó la corrida manual por adb del
> mismo día: arranque sin Firebase en el hilo principal (RNF-07, P-80),
> miniatura de recientes sin la barra al buscar (RF-14, P-14) y reintento
> acotado del reingreso silencioso (P-81); y las dos correcciones que pidió la
> auditoría final de seguridad (latch de la miniatura contra el ciclo de vida y
> contención de fallos en el arranque, §9.2). La línea base de comparación es la corrida v0.3.0 · UI V2
> (2026-08-12, rama `ui-v2`, hoy fusionada en `main`).

## 1. Resumen de la corrida automatizada (2026-10-05, corrida final de cierre)

Comandos: `./gradlew cleanTestDebugUnitTest cleanTestReleaseUnitTest test
ktlintCheck lint assembleDebug assembleRelease` → **BUILD SUCCESSFUL** (28 s,
137 tareas — solo las de pruebas se re-ejecutaron por el `clean…` explícito;
compilación, lint y los dos APK ya estaban al día de este mismo `HEAD`); en `admin/`: `npm run test`, `npm run lint`, `npm run build`
→ todo en verde. Los tests unitarios se re-ejecutaron forzando la limpieza
previa (mismo criterio anti-caché de las corridas v0.2.0 y v0.3.0).

> **Cifra vigente al 2026-10-10 (rama `fix/hallazgos-owasp`, v0.3.1):** la tabla de
> abajo es la instantánea del cierre de `mejoras-v3`. La corrida vigente es
> **800 pruebas de JVM por variante en 106 clases y 47 instrumentadas en 12
> clases**, 0 fallos, con el comando completo sobre árbol limpio. Suben de 720 a
> 800 por los dos tramos de la evaluación OWASP: +71 en la rama `seguridad-owasp`
> y +9 en esta. El detalle está en la §10.7 de
> `docs/INFORME_SEGURIDAD_OWASP.md`, y el APK release medido va atado a su
> SHA-256 en la §10.3 del mismo documento.

| Métrica | v0.3.0 · V2 (2026-08-12) | Hoy (2026-10-05) |
|---|---|---|
| Pruebas unitarias JVM | 529 por variante (75 clases) | **720 por variante — debug y release, 1 440 ejecuciones, 0 fallos, 0 errores, 0 omitidas (100 clases)** |
| ktlint | Sin violaciones | **Sin violaciones** |
| Lint | 0 errores · 56 warnings | **0 errores · 58 warnings** (+2 explicados abajo, ninguno nuevo de fondo) |
| `assembleDebug` / `assembleRelease` (R8 + shrink) | OK | **OK** (§2, §3) |
| Panel `admin/` — vitest | 71/71 | **83/83** (7 archivos) |
| Panel `admin/` — eslint + prettier / build | en verde | **en verde** (ver nota) — solo el aviso preexistente de tamaño de chunk de Vite |

*Nota del panel:* la primera pasada de `npm run lint` falló únicamente en
Prettier por el texto de ayuda de `ContentPage.tsx` (párrafo que explica que los
tipos publicados son también las opciones del registro); se reformateó sin
cambiar una letra y la segunda pasada quedó limpia. Es parte de este commit.

La suite corrió tres veces el 2026-10-05, siempre con los mismos resultados en
lint/ktlint/APK: 705 × 2 (97 clases) sobre el cierre de RF-45; 707 × 2 tras las
dos correcciones de la pasada intermedia de agentes (§9.1, `StoredCredentialsTest`);
710 × 2 tras los tres commits que dejó la corrida manual por adb (`d5e1dc2`,
`429f283`, `e3c74e7`); y **720 × 2 en la corrida final**, tras las dos
correcciones que pidió la auditoría final y la segunda vuelta de la primera de
ellas (§9.2: `RecentsThumbnailGuardTest` 6, `AppStartupTest` 3 y un cuarto caso
de `PendingErasureRecoveryTest`). Los **+191 tests / +25 clases** se reparten por feature (clase nueva o
ampliada → `@Test` totales hoy):

| Feature | Clases nuevas | Ampliadas |
|---|---|---|
| RF-10 tutorial saltable | — | `TutorialViewModelTest` 10, `TutorialUiStateTest` 5 |
| Esquema v2 icono/acento (RF-21/22) | `ContentVisualTest` 8 | `FirestoreContentRepositoryTest` 14, `ContentDocumentParsingTest` 9, `AgencyDetailViewModelTest` 5 |
| Semafóricos + RF-31 | `GuidanceSeverityTest` 2, `GuidanceHistoryViewModelTest` 7, `LocalSavedOutcomeRepositoryTest` 5, `FirestoreOutcomeSyncRepositoryTest` 4, `OutcomeSyncReconcilerTest` 2, `OutcomeErasersTest` 2, `DeleteSavedOutcomeUseCaseTest` 3 | `GuidanceViewModelTest` 24 (guardado opt-in y descarte sin rastro) |
| Reingreso silencioso (P-31 C) | `SilentSessionCoordinatorTest` 10, `SilentSignInUseCaseTest` 9, `SilentSessionViewModelTest` 2, `SignOutViewModelTest` 2, `StoredCredentialsTest` 2 (§9, M-1) | `LoginViewModelTest` 11, `SignInUseCaseTest` 16, `CreateAccountUseCaseTest` 11, `ChangePasswordUseCaseTest` 10 |
| Tipos dinámicos (P-68) | `CategoryContentProviderTest` 3, `ResolvedKindTest` 5, `LoadEntryKindsUseCaseTest` 2, `JournalDatabaseMigrationTest` 6 | `JournalEditorViewModelTest` 33, `JournalDetailViewModelTest` 12, `FirestoreJournalSyncRepositoryTest` 15 (payload v3), `JournalSyncReconcilerTest` 9, `LocalJournalRepositoryTest` 6 |
| RF-45 exportación | `ExportPolicyTest` 5, `BuildExportDocumentUseCaseTest` 9, `LoadExportSummaryUseCaseTest` 3, `JournalExportViewModelTest` 13, `LocalJournalExportRepositoryTest` 8, `PdfBoxExportWriterTest` 16 | `JournalListViewModelTest` 15, `ManifestMinimizationTest` 7 |
| Contraste WCAG 1.4.11 | `PaletteContrastTest` 7 | — |
| Corrida manual por adb (RNF-07 · P-14 · P-81) | — | `PendingErasureRecoveryTest` **4** (la cascada de RF-20 solo se resuelve con la marca izada; y si el conjunto ni se puede construir, la marca sigue izada), `NoTracesGuardTest` 8 (+1: `RecentsThumbnailGuard` acotado a ese único archivo), `SilentSessionCoordinatorTest` 10 (+2: esperas 5/15/45 s y rearme por regreso de red, tiempo virtual) |
| Correcciones de la auditoría final (§9.2) | `RecentsThumbnailGuardTest` 6 (la decisión del latch como lógica pura: no restaurar la miniatura con la fachada en segundo plano, y restaurarla siempre al dejarla), `AppStartupTest` 3 (una tarea que lanza no impide las siguientes ni propaga; un conjunto que no se puede construir tampoco) | — |
| Panel (`admin/`) | `contentVisuals.test.ts` 5 | `topicSchema.test.ts` 14, `schemas.test.ts` 18 (icono/acento v2) |

**Guarda del sistema visual en verde sobre TODO el código**:
`VisualSystemGuardTest` sigue barriendo las seis features con la lista de
excepciones legadas VACÍA; las pantallas nuevas (exportación, resultados
guardados, chip «Sin conexión», selector de tipo dinámico) consumen solo los
tokens de `core/ui/v2`. El kit V1 (`core/ui/components/FormElements.kt`) se
borró en el commit de contraste.

**Comparación de warnings de lint contra la línea base** (58 frente a 56):

| Regla | v0.3.0 | Hoy | Nota |
|---|---|---|---|
| `GradleDependency` | 45 | 45 | Versiones más nuevas disponibles; informativo (política A2-1) |
| `AndroidGradlePluginVersion` | 3 | 3 | Igual |
| `Typos` / `RedundantLabel` / `PluralsCandidate` / `OldTargetApi` | 5 | 6 | **+1** `PluralsCandidate` en `journal_export_pdf_document_n` («%d páginas», cabecera de anexo RF-45): falso positivo del mismo tipo que el preexistente |
| `UnusedResources` | 1 | 1 | `color.night` preexistente |
| `ApplySharedPref` | 2 | 3 | **+1** deliberado: `LocalCredentialStore.erase()` destruye el blob de credenciales con `commit()` síncrono (mismo criterio que el cripto-borrado de RF-20 y `LocalDekStore`) |
| **Total** | **56** | **58** | |

**Pruebas instrumentadas** (emulador; no entran en `./gradlew test`): **10
clases / 42 `@Test`** (v0.3.0: 8 / ~29). Nuevas: `LocalCredentialStoreTest`
(11 — ida y vuelta, adulteración, mitad presente, relleno fijo B-1) y
`JournalExportPipelineTest` (2 — Keystore → PDF → FileProvider → barrido);
`EncryptedDatabaseFactoryTest` (3) ganó el centinela en `label` de la migración
Room v5; `WeatherSearchBarFocusTest` se ajustó a la UI V2 (`e3c74e7`). Se
ejecutan con `./gradlew connectedDebugAndroidTest`: **42/42 en verde en el
emulador el 2026-10-05** (§8, paso 0 ✅).

## 2. R8 — Ofuscación del release con los símbolos nuevos (RNF-05)

`assembleRelease` con `isMinifyEnabled = true` + `isShrinkResources = true`.
Verificación de hoy sobre `app-release.apk` con `apkanalyzer dex packages
--defined-only` (8 462 clases en el DEX):

| Nombre buscado en clases propias (`com.auroraclima`) | Coincidencias |
|---|---|
| Sets históricos v0.1.0–v0.3.0 (`SessionManager`, `Secure*`, `Secret*`, `Dek*`, `*Media*`, `*Erasure*`, `V2*`, `InnerTabsHost`, `SecureSheet`, …) | **0** (sin regresión) |
| **Nuevos mejoras-v3**: `Silent*`, `*Credential*`, `Connectivity*`, `*Outcome*`, `GuidanceSeverity`, `GuidanceHistory*`, `*Saved*` | **0** |
| **Nuevos tipos dinámicos / RF-45**: `*Catalog*`, `ResolvedKind`, `LegacyKindCodes`, `*Export*`, `PdfBox*`, `ExportStaging*`, `ShareUriResolver`, `SystemLauncher`, `DayProvider` | **0** |
| **Nuevos tras la corrida manual y las correcciones**: `*Recents*`, `*Thumbnail*`, `*Trigger*`, `SearchChanged`, `*Decision*`, `*Startup*`, `AppInitializer`, `PendingErasure*` | **0** (las únicas coincidencias son tres `Initializer` de AndroidX). Repetido sobre el APK final, a pedido de la auditoría, porque los nombres de un `enum` pueden sobrevivir a R8 |

Clases propias con nombre legible: **las cuatro de siempre más una del mismo
tipo** — `AuroraApplication` y `MainActivity` (manifest / identidad de clima),
`JournalDatabase` / `WeatherDatabase` (+ `_Impl`) y ahora **`GuidanceDatabase`
(+ `_Impl`)**, la BD cifrada de RF-31, por el mismo constraint de reflexión de
Room (término neutro, residual hermano de P-44, registrado en el ADR
2026-08-14). Todo lo demás renombrado a `a`,`b`,`c`…

**PdfBox-Android** (RF-45): ninguna clase de la librería conserva nombre
legible; quedan 23 clases `a`…`l` bajo tres paquetes `documentinterchange`
(nombres de paquete genéricos de la librería, sin término propio). Ningún
`com.tom_roush.*` con nombre de clase original.

**Estado (2026-10-05, APK final de la rama, con las correcciones de §9.2):
VERIFICADO — ningún NOMBRE DE CLASE nuevo legible en el DEX.** La verificación
se hizo con `apkanalyzer dex packages --defined-only`, que enumera clases
declaradas y **no ve los literales de cadena**; por eso el alcance de la frase
es el que dice y no más. La evaluación OWASP del 2026-10-08 barrió además los
literales y encontró 109 plantillas de `toString()` legibles, incluida una con
«Secret» — ver **P-89** y `docs/INFORME_SEGURIDAD_OWASP.md` §6 H-1.
Residual **A-4 → P-78**:
el `keep` de reflexión de Room conserva la RUTA del paquete, de modo que
`com/auroraclima/app/questionnaire/data/local/GuidanceDatabase` deja legible el
segmento `questionnaire` — término que `CamouflageResourcesTest` sí veda en
recursos. El rename `questionnaire`→`form` lo resuelve junto con las plantillas
de `toString` de P-78, antes del piloto.

## 3. Manifest fusionado y tamaño del APK release

`apkanalyzer manifest permissions app-release.apk` (2026-10-05) — salida
**idéntica** a la de v0.2.0 y v0.3.0:

| Permiso | Cambio |
|---|---|
| `android.permission.INTERNET` | Igual |
| `android.permission.ACCESS_NETWORK_STATE` | Igual en el fusionado; **ahora declarado también en el manifest propio** (`ConnectivityMonitor`, P-31 C; nivel normal, plausible en una app de clima; fijado en `ManifestMinimizationTest`) |
| `com.auroraclima.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Igual |
| `com.google.android.providers.gsf.permission.READ_GSERVICES` | Igual |

Sin permisos nuevos (RNF-04): el reingreso silencioso usa `ConnectivityMonitor`
sobre `ACCESS_NETWORK_STATE` ya declarado; la exportación guarda y comparte por
el selector del sistema (`ACTION_CREATE_DOCUMENT` + `FileProvider` con dos
`cache-path`, fijados por `ManifestMinimizationTest`), sin acceso a
almacenamiento.

**Tamaño del APK release** (misma toolchain, release firmado con debug):

| APK | Bytes |
|---|---|
| `v0.2.0-rc1` (2026-07-30) | 23 237 115 |
| `ui-v2` cierre (2026-08-12) | 23 261 848 |
| Base `26aaac1` (mejoras-v3 + tipos dinámicos, antes de RF-45) | 23 303 904 |
| RF-45 (2026-10-03) | 23 915 494 |
| Contraste WCAG (`280ef43`, 2026-10-05) | 23 915 494 |
| `429f283` (guard de recientes + reintento P-81) | 23 915 518 |
| **Hoy (2026-10-05, con las correcciones de §9.2)** | **23 915 518** (sin cambio medible) |
| **Delta total sobre v0.3.0** | **+653 670 B (+2,8 %)** |

De ese delta, +42 KB son todo mejoras-v3 + tipos dinámicos (código y strings) y
+611 KB es RF-45: PdfBox-Android **sin BouncyCastle** (la exclusión evita ≈11 MB
de JAR) y sin CMaps CJK (`ignoreAssetsPatterns("<dir>cmap")`, ~2,4 MB que solo
sirven para leer fuentes compuestas). Lo que queda de la librería en el release:
las clases de escritura que R8 conserva y los assets necesarios para escribir
con las 14 fuentes estándar (AFM ≈ 250 KB, fuente de respaldo ≈ 420 KB, listas de
glifos). Detalle y comparación de alternativas en el ADR 2026-10-03 (RF-45) de
`docs/ARQUITECTURA.md`; vigilancia de la librería en P-75.

## 4. Matriz de casos de prueba (CP) — vigente al 2026-10-05

Leyenda: **PASA** — cubierto por prueba automatizada; **MANUAL** — requiere
emulador (✅ si la usuaria ya la corrió; si no, es **bloqueante** y está
consolidada en §8 con su paso); **NO APLICA** — trabajo futuro. Cambios
respecto de v0.3.0 en **negrita**.

| CP | RF/RNF | Estado | Evidencia automatizada / manual |
|---|---|---|---|
| CP-01 | RF-01 | PASA + MANUAL (§8-A1) | Auto: `CamouflageResourcesTest` (2 — barre también los ~60 strings nuevos de exportación, resultados guardados, chip sin conexión y tutorial), `ManifestMinimizationTest` (7), `NoTracesGuardTest` (8). Visual de camuflaje en §8-A1. |
| CP-02 | RF-02, RF-03, RF-04 | PASA + MANUAL (§8-A1) | Auto: `WeatherViewModelTest` (16), `WeatherRepositoryImplTest` (12), `ForecastMapperTest`, `ForecastViewModelTest` (5). Manual v1 ✅ (2026-07-21); re-estilo V2 en §8-A1. |
| CP-03 | RF-05, RF-06 | PASA + MANUAL (§8-A1/A2) | Auto: `ValidateSecretCodeUseCaseTest` (10) + guardianes. |
| CP-04 | RF-07 | PASA + MANUAL (§8-A2) | Auto: `CreateAccountViewModelTest` (10), `CreateAccountUseCaseTest` (**11 — al crear la cuenta se guardan las credenciales cifradas para el reingreso silencioso**), `FirebaseAuthRepositoryTest` (15). |
| CP-05 | RF-08, RF-09 | PASA + MANUAL ✅/parcial (§8-A3) | Auto: como v0.2.0. P-53 ✅; resta Device Explorer (P-28), **ahora junto a `account_meta.xml`** (§8-A3). |
| CP-06 | RF-10, RF-11 | PASA + MANUAL ✅ + **§8-A2** | Auto: `TutorialViewModelTest` (**10 — «Saltar por ahora»: el salto fija el logro sin práctica y la práctica sigue disponible desde Ajustes**), `TutorialUiStateTest` (5). P-34/P-53 ✅ (tutorial completo); **el salto se verifica en §8-A2**. |
| CP-07 | RF-12, RF-13, RF-14 | PASA + MANUAL (§8-A1) | Auto: `ValidateSecretCodeUseCaseTest` (10), `ConstantTimeTest` (4), **`NoTracesGuardTest` (8 — `RecentsThumbnailGuard` es el único archivo que toca la miniatura de recientes) y `RecentsThumbnailGuardTest` (6 — la decisión del latch, aislada como lógica pura porque la API de Android no expone getter del flag)**. **P-14 ✅ (2026-10-05): la miniatura SÍ mostraba el código a medias; la guard (API 33+, solo mientras se busca) se re-verificó en emulador con el APK `429f283`, y la auditoría final (A-1) la enganchó al ciclo de vida para cerrar la carrera sub-frame con la limpieza de ON_PAUSE.** P-11 bloqueante y **P-84 (residual: bajo API 33 no hay API y queda solo ON_PAUSE — se mira en §8-C2)**. P-11 en §8-A1. **El reingreso silencioso no toca la barra: el código sigue siendo la única puerta (RF-12).** |
| CP-08 | RF-15 | PASA + MANUAL (§8-A4/A5) | Auto: `SignInUseCaseTest` (16), `LoginViewModelTest` (11), **`SilentSignInUseCaseTest` (9 — éxito, rechazo definitivo destruye el blob, deshacer bajo `NonCancellable` tras la salida), `SilentSessionCoordinatorTest` (10 — local-first, confirmación en segundo plano, monitor de red, reintento acotado con espera creciente P-81), `SilentSessionViewModelTest` (2); instrumentada `LocalCredentialStoreTest` (11)**. Manual: P-29 (login manual) y **P-71 (reingreso silencioso)** en §8-A4/A5. |
| CP-09 | RF-16 | PASA + MANUAL (§8-A4, A11) | Auto: `SessionManagerTest` (20), `SecureFlagLedgerTest` (5), **`SilentSignInUseCaseTest` (la salida rápida con el silencioso en vuelo deshace el login)**. Manual: P-27 (§8-A11) y **P-71(7)** (§8-A4). |
| CP-10 | RF-17, RF-18 | PASA + MANUAL ✅ | Auto: `ConsentViewModelTest` (2). P-53 ✅; visual V2 en §8-A2. |
| CP-11 | RF-19 | PASA + MANUAL ✅ + **§8-A5** | Auto: **`SignOutViewModelTest` (2 — cerrar sesión destruye el blob de credenciales y converge en `quickExit`)**, `SignOutSessionCleanupTest`. P-53 ✅; **P-71(4): tras cerrar sesión el reingreso exige código + credenciales** (§8-A5). |
| CP-12 | RF-20 | PASA + MANUAL ✅ + **§8-A14** | Auto: como v0.2.0 + **`OutcomeErasersTest` (2 — cascada sobre `guidance_store.db` y `outcomes`) y `StoredCredentialsEraser` (vía `LocalAccountEraser`)**. P-54 E2E ✅; **P-71(5) `account_meta` inexistente y cascada de RF-31/RF-33** en §8-A14. **Regresión H-1 tras el ADR 2026-10-05: `PendingErasureRecoveryTest` (4) fija que la cascada local NO se construye en arranques normales, SÍ con la marca izada, y que si el conjunto ni se puede construir la marca sigue izada (nada se da por borrado); `AppStartupTest` (3) fija que un fallo de arranque no tumba el proceso ni impide las demás tareas (auditoría final A-2). La prueba manual va en §8-A14.** |
| CP-13 | RF-21, RF-22 | PASA + MANUAL (§8-A6, P1) | Auto: `TopicsViewModelTest` (3), `TopicDetailViewModelTest` (4), `RoutesViewModelTest` (3), `FirestoreContentRepositoryTest` (**14 — esquema v2 `icon`/`accent` con degradación por campo al mapeo automático, RF-25**), `ContentDocumentParsingTest` (9), **`ContentVisualTest` (8 — biblioteca cerrada de iconos/acentos, id desconocido → neutro), `CategoryContentProviderTest` (3 — los topics publicados son el catálogo de tipos del registro, P-68)**. Contenido real ✅ (P-50/P-57); **t05/t06 validados, publicación durante §8-A6**; visual y panel en §8-A6/P1. |
| CP-14 | RF-23 | PASA + MANUAL (§8-A11) | Auto: `AgencyDetailViewModelTest` (5), `LinesViewModelTest` (3). P-26 bloqueante (§8-A11). |
| CP-15 | RF-24, RF-25 | PASA + MANUAL ✅ | Auto: parsing tolerante (`ContentDocumentParsingTest` 9 + 11). P-50 E2E ✅ (2026-07-17, §7). **La tolerancia cubre el esquema v2: campos ausentes o ids fuera de la biblioteca degradan sin tumbar el fetch.** |
| CP-16 | RF-26, RNF-08 | MANUAL ✅ + §8-A4/A6 | P-50 ✅; **entrada local sin red con chip «Sin conexión» (RNF-08, P-31 C)** en §8-A4; indicador de la portada en §8-A6. |
| CP-17 | RF-27, RF-28 | PASA + MANUAL (§8-A9) | Auto: `GuidanceViewModelTest` (24). P-43 bloqueante (§8-A9). |
| CP-18 | RF-29, RF-30 | PASA + MANUAL (§8-A9) | Auto: `CalculateGuidanceLevelUseCaseTest` (19), **`GuidanceSeverityTest` (2 — par semafórico fijo por orden de nivel, reversión P-41), `PaletteContrastTest` (7 — los cuatro pares `accentSeverity` cumplen contraste)**. P-43 (incluido «aviso fijo visible sin scroll» con la tarjeta semafórica dominante) en §8-A9. |
| CP-19 | RF-31 | **PASA + MANUAL (§8-A9)** | **Antes NO APLICA. Auto: `GuidanceViewModelTest` (guardar exige confirmación en hoja segura; «Terminar»/salida rápida/abandono no persisten nada — criterio 1), `LocalSavedOutcomeRepositoryTest` (5 — mínimo consultable fecha + nivel + tipos en `guidance_store.db` cifrada, RNF-04), `FirestoreOutcomeSyncRepositoryTest` (4 — sellado con la DEK de RF-39 a `outcomes`), `OutcomeSyncReconcilerTest` (2 — restauración), `GuidanceHistoryViewModelTest` (7 — listado, detalle, eliminación con doble confirmación), `DeleteSavedOutcomeUseCaseTest` (3 — propagación remota como RF-36), `OutcomeErasersTest` (2 — cascada RF-20). Manual: guardado sin red + reconexión (P-69a), restauración en B y cascada** en §8-A9/B1/A14. |
| CP-20 | RF-32 | PASA + MANUAL (§8-A7) | Auto: `JournalEditorViewModelTest` (**33 — los chips de tipo son los topics publicados en su orden y visual; sin catálogo → «Sin tipo»; tipo retirado conserva el último nombre**), **`LoadEntryKindsUseCaseTest` (2), `ResolvedKindTest` (5), `AddJournalEntryUseCaseTest` (9), `JournalDatabaseMigrationTest` (6 — Room v4→v5 conserva los tipos como `storageCode`), `JournalDaoTest` (6)**. P-46 bloqueante y **P-73(c)** en §8-A7/B1/P1. |
| CP-21 | RF-33 | PASA + MANUAL ✅ (§8-A8, 2026-10-07) | Auto: `LocalJournalAttachmentRepositoryTest` (18) y compañía. P-59 ✅ (§8-A8). |
| CP-22 | RF-34, RF-35, RF-36 | PASA + MANUAL (§8-A7) | Auto: `JournalDetailViewModelTest` (12), `UpdateJournalEntryUseCaseTest` (3), `JournalListViewModelTest` (15). P-46 bloqueante (§8-A7). |
| CP-23 | RF-37, RF-38 | PASA + MANUAL ✅ + §8-A4 | Auto: `FirestoreJournalSyncRepositoryTest` (**15 — payload v3 con el id del topic; lectura tolerante de v2 vía `LegacyKindCodes`**), `JournalSyncReconcilerTest` (9). P-49 ✅; **sync diferida tras la entrada sin red** en §8-A4. |
| CP-24 | RF-39 | PASA + MANUAL (§8-B1..B4) | Auto: como v0.2.0 + payload v3 en la restauración. P-61 bloqueante **(ahora incluye `outcomes` de RF-31 y la restauración v3 de P-73c)** en §8-B. |
| CP-25 | RF-40 | MANUAL ✅ + PASA | Acceso admin ✅ (2026-07-17). P22 V2 en §8-P1. |
| CP-26 | RF-41, RF-42, RF-44 | MANUAL ✅ + PASA + **§8-P1** | E2E ✅ (2026-07-17). Vitest **83/83** (+12: `contentVisuals` 5, `topicSchema` 14, `schemas` 18 — **selector de icono/acento v2 con biblioteca cerrada y `schemaVersion` 2**). **El selector que B6 daba por diferido (D2) ya existe: se verifica en §8-P1, junto con el reflejo del cambio en la Guía Y en los chips del registro (P-68).** |
| CP-27 | RF-43 | MANUAL ✅ + PASA | E2E ✅ + `coherence.test.ts` (25). |
| CP-28 | RNF-01 | PASA + MANUAL parcial (§8-A3/A7/A8) | Auto: cifrado como v0.2.0 + **`LocalCredentialStoreTest` (11, instrumentada — `account_meta` AES-GCM con llave Keystore y relleno fijo), `LocalSavedOutcomeRepositoryTest` (`guidance_store.db` por `EncryptedDatabaseFactory`), `JournalDatabaseMigrationTest`**. Device Explorer en §8-A3/A7/A8/A10. |
| CP-29 | RNF-02 | PASA + MANUAL (§8-A/B) | Auto: `SecureFlagLedgerTest` (5), `NoTracesGuardTest`. **Pantallas nuevas bajo `secureGraph`: exportación (lista en selección + formulario), resultados guardados (listado + detalle + hoja de borrado), hoja de confirmación de guardado** — capturas en §8-A9/A10. |
| CP-30 | RNF-03 | PASA + MANUAL ✅ + §8-A10 | Auto: `SessionManagerTest` (20), `InteractionThrottleTest` (4). **Campos nuevos (dos contraseñas de exportación) registran interacción (regla P-15)**; «5 min en el formulario de exportar» en §8-A10. |
| CP-31 | RNF-04 | PASA + MANUAL parcial | Auto: `NoTracesGuardTest` (8), `ManifestMinimizationTest` (7) + §3 (permisos idénticos hoy). **RF-31 persiste solo fecha + nivel + tipos; el PDF exportado solo lleva lo que la usuaria marcó.** |
| CP-32 | RNF-05 | PASA + MANUAL (§8-A1) | Auto: `CamouflageResourcesTest` + DEX §2 (**hoy: 0 símbolos nuevos legibles; `GuidanceDatabase` neutro como `JournalDatabase`**). **El permiso `ACCESS_NETWORK_STATE`, ahora declarado por la app, es de nivel normal y plausible en una app de clima** (§3). Residual P-78 (plantillas `toString` en el DEX). |
| CP-33 | RNF-06 | PASA ✅ | `firestore.rules` **sin cambios en el diff**: `outcomes` y `account_meta` (local) no exigen regla nueva — `/users/{uid}/**` ya cubre `outcomes`. Playground 10/10 ✅ (2026-07-15/17). |
| CP-34 | RNF-07 | MANUAL (§8-C1) | Gama media: arranque ≤ 3 s, interacción ≤ 2 s. **Añadir la generación de un PDF con adjuntos (RF-45) y la primera entrada sin red al cronómetro. Nuevo (ADR 2026-10-05, P-80): tres arranques en frío tras `am force-stop` sin «failed to complete startup» — el grafo de Hilt ya no construye Firestore/FirebaseAuth en el hilo principal.** |
| CP-35 | RNF-09 | NO APLICA | Evaluación heurística formal de usabilidad (posterior). |
| CP-36 | RNF-10 | MANUAL (§8-C2) | API 26: componentes V2 **+ exportación (PdfBox en minSdk) + selector de documentos**. |
| CP-37 | RNF-11 | PASA | Pasada final de `revisora-arquitectura` sobre el diff completo `main..HEAD` (§9). |
| CP-38 | RF-45 | PASA + MANUAL ✅ (§8-A10, 2026-10-07) | Auto (2026-10-03): `JournalListViewModelTest` (selección), `ExportPolicyTest` (5), `BuildExportDocumentUseCaseTest` (9), `LoadExportSummaryUseCaseTest` (3), `JournalExportViewModelTest` (13), `LocalJournalExportRepositoryTest` (8, incluida la cancelación a mitad de escritura), `PdfBoxExportWriterTest` (16, escritor real: abre solo con la contraseña, PDF anexado con recursos heredados y podado de anotaciones/metadatos, cabecera y pie en páginas rotadas/recortadas, banda de continuación, audios incrustados sin colisión, PDF dañado o con árbol roto), `ManifestMinimizationTest` (dos `cache-path`), `CamouflageResourcesTest`; instrumentada `JournalExportPipelineTest` (2). Selector de documentos y de apps = UI del sistema: **manual ✅ (§8-A10, 2026-10-07)**. |
| **CP-39** | **RF-46** | **PASA + MANUAL ✅ (§8-B3)** | Auto (2026-10-06): `SendPasswordResetUseCaseTest` (10 — mismo desenlace con/sin cuenta y con correo mal formado, pausa tras cinco envíos con contador propio, sin conexión se distingue), `LoginViewModelTest` (ampliado, +13 del archivo), `FirebaseAuthRepositoryTest` (ampliado, +18 del archivo), `NoTracesGuardTest` (guarda ampliada: ninguna capa de la recuperación registra en logs ni telemetría). Manual: **B3 ✅ (2026-10-07)** — mensaje de confirmación idéntico con correo con cuenta / sin cuenta / mal formado, aviso previo, sin conexión distinto, pausa al sexto envío sin bloquear el inicio de sesión normal. **Pendiente (P-19/P-88): personalizar la plantilla de Firebase y verificarla con un correo real antes de cualquier entrega del APK al piloto** — la corrida de hoy validó el comportamiento de la app, no el contenido del correo que compone Firebase. |

### Cobertura resumida (2026-10-05; RF-46 agregado 2026-10-07)

**45 de 46 RF con cobertura automatizada** (v0.3.0: 42 de 44; 2026-10-05: 44 de
45). Entran **RF-31** (antes trabajo futuro), **RF-45** y ahora **RF-46**
(recuperación de contraseña por correo, agregado el 2026-10-06 — CP-39, ver
arriba), los tres RF que creció el alcance de la rama; los anteriores conservan
su cobertura y **ninguno la perdió**. El único RF sin prueba propia sigue
siendo **RF-38** (sincronización diferida): la provee la cola durable de
Firestore y se verificó manualmente en P-49 (2026-07-15), con repetición en
§8-A4 (entrada sin red → reconexión). Se mantiene el criterio conservador de
v0.2.0 de no contarlo, aunque `FirestoreJournalSyncRepositoryTest` ya ejercite
el encolado.

Los **11 RNF** mantienen su cobertura y dos la ganaron o la ampliaron en esta
rama: **RNF-08** (entrada local sin red, por las tres suites del reingreso
silencioso) y **RNF-07**, que pasó de «solo plataforma» a tener una prueba
propia — `PendingErasureRecoveryTest` fija que el arranque no construye la
cascada de borrado sin la marca izada (el eslabón que rompía el arranque antes
del ADR 2026-10-05) y `AppStartupTest` que ningún fallo de una tarea de arranque
tumba el proceso. La medición de tiempos de RNF-07 sigue siendo manual (§8-C1),
igual que RNF-09 (usabilidad, trabajo posterior) y RNF-10 (API 26, §8-C2).

| Alcance | Con cobertura automatizada | Solo manual / futuro |
|---|---|---|
| **RF (46)** | **45** | RF-38 (cola de Firestore, manual en P-49 y §8-A4) |
| **RNF (11)** | 9 (incluye RNF-07 parcial y RNF-08 nuevo) | RNF-09 (heurística posterior), RNF-10 (§8-C2) |

## 5. Pasadas manuales ya ejecutadas por la usuaria (integradas)

| Referencia | Fecha | Resultado |
|---|---|---|
| P-34 (CP-06, RF-10) | 2026-07-11 | ✅ Tutorial con práctica real |
| P-28 parcial (CP-05, RF-08) | 2026-07-11 | ✅ Rechazos de clave en vivo; resta Device Explorer |
| P-49 (CP-23, RF-37) | 2026-07-15 | ✅ Sync cifrada offline→online; blobs ilegibles |
| P-04/CP-37 (CP-33, RNF-06) | 2026-07-15 | ✅ Rules playground 10/10 |
| CP-26/CP-27 (B2) + reglas `/drafts` | 2026-07-17 | ✅ E2E panel→app; `/drafts` solo admin |
| P-50 (CP-15/16/25 + regresión, B3) | 2026-07-17 | ✅ Ciclo completo panel→app, corrupto tolerado, modo avión |
| P-53 (CP-05/06/10/11 — Configuración C1) | 2026-07-17 | ✅ Pasada completa en verde |
| P-54 (CP-12, RF-20) | 2026-07-20 | ✅ Eliminación E2E con Firebase real |
| CP-02 (RF-04, pronóstico) | 2026-07-21 | ✅ Pronóstico de 5 días en emulador |
| Siembra P-57 (contenido verificado) | 2026-07-22 | ✅ 4 topics / 5 lines / 3 routes / 24 agencies en producción |
| **RF-45 revisión en emulador (parcial)** | 2026-10-03 | ✅ PDF con adjuntos abierto en Acrobat/Chrome/Drive: páginas anexadas, audio incrustado, jerarquía visual; motivó los commits `4c9ad3e`/`816a7d6`. **CP-38 completo sigue en §8-A10** |
| **B8 contraste (parcial)** | 2026-10-05 | ✅ Exportación y clave personal vistas con el contorno nuevo; **resto de pantallas en §8 (transversal)** |
| **Corrida por adb, §8 pasos 0–A8** | 2026-10-05 | ✅ Paso 0 (42/42), A1–A5 y A7 completos, A6/A8 parciales (tabla de avance en §8); **P-14 y P-81 re-verificados con el APK `429f283`**; dejó P-80..P-82 y el ADR 2026-10-05 |
| **Corrida manual de la usuaria, §8: P1/A1/A3/A13/A6/B5/A9/B1/B2/B3/B4/A14** | 2026-10-07 | ✅ Las doce en verde, sin fallos; cierra los residuales visuales de A1/A6, cierra A9/A13/A14/B1-B5/P1 completos e incluye RF-46/CP-39; plantilla del correo de restablecimiento personalizada (P-19). **Restan del §8: A2 (residual), A8 (residual), A10 (RF-45, bloqueante), A11, A12 y bloque C** |

> Estas pasadas validaron comportamiento y siguen contando; su componente
> visual V2 y los flujos nuevos se recorren en la lista única de §8.

## 6. Fase B2 — Panel administrativo (histórico, 2026-07-17)

Alcance: RF-40 a RF-44, commits `3827b83`, `c413523`, `555a602`, `98f1f88`,
`3e6f863` y `73a29da`. El panel produce EXACTAMENTE el esquema que la app
consume (espejo de los DTO de `content/data` y `questionnaire/data`) y la app
solo ve lo publicado: los borradores viven en `/drafts`, exclusiva del claim
admin; regla desplegada y verificada en el Playground el 2026-07-17
(`/users/**` intacto, RNF-06). Corrida automatizada de la fase: vitest 66/66
(hoy 83/83 con el instrumento v2 y el esquema v2 de contenido), lint y build en
verde. Corrida manual E2E de la usuaria (2026-07-17): publicación reflejada,
borradores invisibles, bloqueos de integridad referencial y de coherencia del
cuestionario, versión automática y compuerta `[POR CONFIRMAR]` — todo en verde.

## 7. Fase B3 — Cierre del ciclo de contenido en la app (histórico, 2026-07-17)

Alcance: RF-24, RF-25 y RF-44 (lado app), commits `ef1f925`, `ec9355d`,
`14b62d0` y `054b1ce`. Cambio único de producción: el parsing snapshot→DTO pasó
a casts seguros sobre el mapa crudo — un documento corrupto se omite (content) o
se descarta rigiendo el válido de mayor versión (questionnaire), sin tumbar el
fetch (RF-25). Corrida manual E2E ✅ (P-50, 2026-07-17). Veredictos: tres
APROBADO. *(v0.2.0: helpers extraídos a `core/network/FirestoreDocumentParsing.kt`;
2026-08-14: la misma tolerancia absorbe `icon`/`accent` del esquema v2.)*

## 8. Corridas manuales — LISTA ÚNICA (bloqueante del merge y del tag)

Todo lo manual que resta está aquí y solo aquí. Reemplaza las §8 de v0.2.0 y
v0.3.0 e integra **P-71** (reingreso silencioso), **P-73(c)** (tipos conectados a
la Guía y al registro), **CP-38** (exportación), **CP-19** (RF-31), las dos
pruebas que dejó el ADR 2026-10-05 (arranque en frío ×3 en C1 y regresión H-1
en A14), la verificación visual V2 que seguía pendiente y el **bloque S**, que es
lo que dejaron las correcciones de la evaluación OWASP (v0.3.1): **S1 es
bloqueante del tag v0.3.1**. Los pasos numerados
completos de cada P-xx están en su entrada de `docs/PENDIENTES.md`; aquí va el
resumen ejecutable, el resultado esperado y el orden. El orden está pensado
para **una sola instalación en A** (bloques A y P encadenados sin reinstalar),
**una en B** (bloque B) y el bloque C aparte.

**Montaje:** emulador **A** = `Medium_Phone_API_35` **relanzado con 4 núcleos y
4 GB** (`emulator -avd Medium_Phone_API_35 -cores 4 -memory 4096`, P-82; con la
configuración por defecto de 1 núcleo / 2 GB la app no completa el arranque),
instalación limpia del **debug** de la rama vigente —para los bloques A/B/P/C,
`429f283` (Room v5 / payload v3); **para el bloque S, el debug de v0.3.1**, que es
el único que lleva las correcciones de la evaluación OWASP— con
`./gradlew installDebug` y comprobando `lastUpdateTime` antes de empezar;
proyecto real `aurora-31c37`; consola de Firebase y panel `admin/` (`npm run
dev`) abiertos. Emulador **B** (segundo dispositivo) solo en el bloque B.
**Regla de orden:** el APK v5 debe estar instalado ANTES de publicar t05/t06
(ADR 2026-10-03; hoy ya están publicados en producción con esquema v2, así que
A6 solo verifica), y ninguna contraseña se cambia antes de que B haya
restaurado (bloque B).

**Transversales en TODA pantalla interna** (se anotan donde fallen, no se
repiten como paso): captura bloqueada (RNF-02); nube violeta de salida
alcanzable y funcional (RF-16); fidelidad al mockup `docs/mockups-v2/v2-NN.png`
con Marcellus solo en título/saludo, un solo botón lleno por pantalla e iconos
de la librería (donde mockup e implementación difieren por camuflaje o decisión
D1–D8/P-17/P-25/P-41/P-45/P-52, manda la implementación); **contraste B8**:
todo campo con relleno blanco y contorno gris `#7E788E` que pasa a tinta oscura
y se engrosa con foco, error en rosa, chips de tipo sin elegir con contorno
gris; la barra de la fachada NO cambia sobre el cielo y muestra el contorno solo
al buscar, la del tutorial siempre.

### Avance al 2026-10-05 (corrida por adb desde Claude Code; detalle en `docs/PENDIENTES.md` P-80..P-82)

La corrida por adb cubrió el paso 0 y A1–A8 sobre la cuenta de prueba viva en A.
**Lo que resta para la usuaria, en orden:** (1) terminar **A2** (repaso del
tutorial desde Ajustes, RF-11), **A6** (fidelidad visual de la Guía) y **A8**
(registro con PDF + audio, cámara, visor, Device Explorer, «×», tope de 10);
(2) **A9–A14** completos, con la regresión H-1 nueva dentro de A14; (3) bloque
**B** (B1–B4, B5 condicional); (4) bloque **P**; (5) bloque **C**, con el
arranque en frío ×3 nuevo dentro de C1. Los pasos ya en verde no se repiten
salvo que un cambio de código los toque.

| Paso | Estado | Nota |
|---|---|---|
| 0 | ✅ 42/42 | Tras corregir `WeatherSearchBarFocusTest` (buscaba un texto que la UI V2 convirtió en icono; commit `e3c74e7`) |
| A1 | ✅ (P-14 🔁 ✅); **🔁 visual pendiente** | RF-13 idéntico píxel a píxel bajo la barra; **la miniatura de recientes sí mostraba el código a medias** → mitigación aplicada (`RecentsThumbnailGuard`, API 33+, commit `429f283`). **Repetido con el APK nuevo (18:43): ✅** la miniatura es una tarjeta neutra mientras se busca y vuelve a ser la fachada normal al salir de la búsqueda. Resta P-11 (Gboard). **Se retiró la pestaña «Lugares» de la barra (ADR 2026-10-06)**: lo funcional (RF-02/12/13/14, P-14) no cambió y sigue en verde, pero falta repetir SOLO la parte visual — comprobar en el emulador que la fachada se ve creíble con dos pestañas (espaciado e indicador activo) |
| A2 | ✅ parcial | Consentimiento, cuenta nueva, reglas de la clave en vivo, «Saltar por ahora» → Inicio. **Resta** el repaso del tutorial desde Ajustes (RF-11) |
| A3 | ✅ | Reingreso cae en Inicio (P-67); prefs solo Base64 (`search_index`, `account_meta`, `backup_meta`); consola `profile g=1` |
| A4 | ✅ (paso 2 🔁 ✅) | Entrada sin red con chip, Guía y 6 tipos desde caché, validaciones y guardado sin red, nube + reentrada. **Al volver la red el chip no desapareció ni subió el registro** (P-81, resuelto con reintento acotado, commit `429f283`). **Repetido con el APK nuevo (18:45): ✅** el chip desapareció solo 12 s después de devolver la red (primer reintento a los 5 s) y el registro subió |
| A5 | ✅ | Cerrar sesión destruye el blob; login manual; P-29 completo (mensaje único, pausa 5:00 que se retoma tras forzar detención, acceso normal al expirar); blob renace |
| A6 | ✅ parcial | t05/t06 ya publicados en producción con esquema v2; 6 tipos en la Guía; **resta** la fidelidad visual (capturas negras por FLAG_SECURE: mirar el emulador) |
| A7 | ✅ | P-46 completo salvo FLAG_SECURE del selector (no verificable por adb): fecha con selector (en inglés por la locale del emulador), orden, detalle, edición (mismo doc), edición y borrado sin red con propagación, doble confirmación, nube a mitad (borrador no persiste), >5 min tecleando sin expirar |
| A8 | ✅ parcial | Photo Picker sin permisos, ida y vuelta directa, dos fotos adjuntas y guardadas; con el selector de documentos abierto >5 min, al volver se aterriza en la fachada (esperado). **Resta:** registro con PDF + audio, cámara, visor, Device Explorer de `media_store`, «×», tope de 10 |
| A9–A14, B, P, C | ⏳ | Pendientes: el emulador (1 núcleo / 2 GB) se saturó repetidamente — ver P-82 y el montaje de arriba |

Hallazgo de código corregido en esta corrida: Aurora dejó de completar el arranque
(«failed to complete startup») porque el grafo de Hilt construía Firestore y
FirebaseAuth en el hilo principal durante `Application.onCreate` — ADR 2026-10-05
y P-80 (resto del arrastre, no bloqueante). Texto de la portada del cuestionario
alineado con RF-31. Las dos pruebas manuales que nacen de ese ADR están ya
integradas en **C1** (arranque en frío ×3) y **A14** (regresión H-1).

### Avance al 2026-10-07 (corrida manual de la usuaria)

La usuaria corrió a mano, con sus propios ojos (no por adb), los pasos que la
corrida del 2026-10-05 había dejado como «visual pendiente» o como bloques
enteros sin tocar, en este orden: **P1, A1, A3, A13, A6, B5, A9, B1, B2, B3,
B4 y A14**. Resultado: **todas pasaron, sin fallos**. La plantilla del correo
de restablecimiento ya está personalizada en la consola de Firebase (P-19).

| Paso | Estado | Nota |
|---|---|---|
| A1 | ✅ **completo (2026-10-07)** | Cierra el «🔁 visual pendiente» del 2026-10-05: la usuaria confirmó en el emulador que la fachada se ve creíble con las dos pestañas (Hoy · 5 días), con espaciado e indicador activo correctos tras el retiro de «Lugares» (ADR 2026-10-06). Resta solo P-11 (Gboard), que sigue como pendiente aparte |
| A3 | ✅ **reconfirmado (2026-10-07)** | Ya estaba en verde por la corrida por adb; la usuaria lo repitió a mano sin hallazgos nuevos |
| A6 | ✅ **completo (2026-10-07)** | Cierra el «resta la fidelidad visual» del 2026-10-05: la usuaria confirmó a ojo (capturas bloqueadas por FLAG_SECURE) que la Guía con t05/t06 es fiel a los mockups v2-11..v2-16 |
| A9 | ✅ **(2026-10-07)** | Cuestionario semafórico + guardado opcional (RF-27..31, CP-17/18/19) completo: los cuatro niveles, el aviso RF-30 fijo, «Terminar»/«Guardar»/«Cancelar» y la eliminación con doble confirmación, todo en verde |
| A13 | ✅ **(2026-10-07)** | Fuente grande (fontScale 2.0, P-64) verificada: las tres tarjetas de Acciones rápidas miden lo mismo; el corte de etiquetas en 360 dp quedó anotado como esperado, sin bloquear |
| A14 | ✅ **(2026-10-07)** | Cierre — eliminación total con todas las cascadas (RF-20, regresión H-1 del ADR 2026-10-05) verificada de punta a punta |
| B1 | ✅ **(2026-10-07)** | Restauración íntegra v3 en el segundo dispositivo, íntegra (registros, adjuntos y resultado guardado) |
| B2 | ✅ **(2026-10-07)** | Edición y borrado cruzados entre A y B, incluido el caso de conflicto (gana la eliminación), en verde |
| B3 | ✅ **(2026-10-07)** | Incluye la corrida de **RF-46/CP-39** (recuperación de contraseña): mensaje idéntico con/sin cuenta y con correo mal formado, aviso previo, sin conexión distinto, pausa al sexto envío sin bloquear el login; después, respaldo ilegible y reingreso manual en A, todo en verde |
| B4 | ✅ **(2026-10-07)** | Cambio de contraseña desde la app, con los tres casos de error, en verde |
| B5 | ✅ **(2026-10-07)** | Migración v4→v5 con datos previos, condicional, aplicada y en verde |
| P1 | ✅ **(2026-10-07)** | Panel V2 + esquema v2 + P-68, incluido el retiro/republicación de t06 y el borrador vs. publicado, en verde |
| A2 | ✅ **completo (2026-10-07)** | Cierra el residual: desde Ajustes → «Ver el tutorial de nuevo», la práctica rechaza una clave incorrecta y fija el logro con la clave real; la salida rápida regresa a la fachada al instante |
| A8 | ✅ **completo (2026-10-07)** | Cierra el residual: adjuntar PDF y audio por «Archivo», cámara del sistema sin copia en galería/DCIM y con la gracia de 5 min, visor interno con captura bloqueada, tipo vedado rechazado, quitar con «×», tope de 10, abandono sin rastro, persistencia tras cerrar sesión y blobs ilegibles en Device Explorer — todo en verde |
| A10 | ✅ **completo (2026-10-07)** | **Cierra el único bloqueante real del tag:** exportación a PDF (RF-45/CP-38) — selección, validaciones de contraseña, «Guardar en el teléfono» con apertura y rechazo de contraseña errónea, «Compartir» con adjuntos a un destino real, cancelación a mitad y salida rápida durante la contraseña, todo en verde |

**Decisión de la usuaria (2026-10-07):** **A11** (Llamar), **A12** (Ajustes y
flujos de extensión) y el bloque **C** (C1 arranque en frío/gama media, C2 API
26) quedan **deliberadamente deferidos a justo antes del piloto**, no al tag:
A11 es una ruta que este ciclo no tocó (duda de diseño ya registrada en P-27,
no un riesgo de regresión); A12 ya quedó probado a fondo en su parte funcional
por B4 (cambio de contraseña) y lo que resta es solo visual; el propio bloque C
se documentaba como «recomendadas antes del tag, **no encadenadas**» y conviene
correrlo en un teléfono real de gama media/baja en vez de otro emulador. Con
A2/A8/A10 cerrados, **el §8 ya no bloquea el merge ni el tag** — A11/A12/C
quedan anotados como condición previa al piloto, no al TRL 4.

**Re-verificación automatizada previa al merge (2026-10-07):** `./gradlew
cleanTestDebugUnitTest cleanTestReleaseUnitTest test ktlintCheck lint
assembleDebug assembleRelease` → **BUILD SUCCESSFUL** (3 min 47 s, 137 tareas).
**791 pruebas unitarias JVM por variante (debug y release), 0 fallos, 0
errores** — incluye las de RF-46 y las de la sincronización entre dispositivos
que se añadieron después de la corrida del 2026-10-05, por eso el número crece
frente a los 720 documentados entonces. `ktlintCheck` y `lint` sin violaciones
nuevas. En `admin/`: `npm run test` **91/91** (+8 sobre los 83 documentados),
`npm run lint` y `npm run build` en verde. El release queda firmado con la
llave de depuración (decisión TRL 4 vigente, ver `docs/PENDIENTES.md`), 23 951
586 B.

### Paso 0 — Instrumentadas (una vez, emulador A arriba, antes de instalar)

| # | Qué correr | Resultado esperado |
|---|---|---|
| 0 | `./gradlew connectedDebugAndroidTest` | **47/47** en 12 clases (eran 42 en 10; entran `ImageImporterRejectionTest`, `QuickAccessRowHeightTest` y los 2 casos de `SecureTextEntryTest`). Cubre Keystore, SQLCipher (incluido el centinela `label` de la migración v5 — P-73c), cifrado de payload/media, foco de la barra, **`LocalCredentialStoreTest` (P-71 paso 8)** y **`JournalExportPipelineTest` (CP-38)**. Después: `./gradlew installDebug` limpio en A |

### Bloque A — Instalación A (una sola cuenta, encadenado sin reinstalar)

| # | Qué correr | Pasos (resumen) | Resultado esperado |
|---|---|---|---|
| A1 | **Fachada y camuflaje** — CP-01/02/03/07/32 · B1 · P-14 · P-11 (1.ª parte) | Mockups v2-01..03: clima con dos pestañas Hoy · 5 días, búsqueda con recientes, barra de temperatura. Lista visual de camuflaje: ícono/nombre/splash, miniatura de recientes normal, capturas permitidas. **RF-13**: ciudad inexistente vs. código incorrecto → mismo «sin resultados» (texto, aspecto, tiempos). **P-14**: teclear el código a medias → Home → mirar la miniatura, **repitiendo Home varias veces seguidas con 2–3 caracteres escritos** (la restauración está enganchada a ON_RESUME para cerrar la carrera sub-frame — auditoría final A-1). Teclear `luna2026` (activación) | Tipografía neutra, solo iconos de clima; nada delata el doble fondo. **Secuencia del latch, en este orden:** (1) teclear 2–3 caracteres → Home → recientes muestra una tarjeta neutra, nunca el código; (2) repetir 3–4 veces seguidas → siempre neutra; (3) **volver a la app** (la barra ya está vacía) y pulsar Home **sin teclear** → recientes vuelve a ser la fachada de clima normal — este paso es el que comprueba que la miniatura no queda oculta de forma permanente, que sí sería un distinguidor (RF-01/RNF-05); (4) repetir todo con un nombre de ciudad en lugar del código → comportamiento idéntico (RF-13). Mientras la app está en segundo plano tras buscar, la tarjeta neutra es el precio aceptado del latch (ADR 2026-10-05); la pestaña «Lugares» se retiró de la barra (ADR 2026-10-06, enmienda a D4 — ver §A1 🔁 visual); la activación abre el consentimiento |
| A2 | **Activación, cuenta y tutorial saltable** — CP-04/06/10 · B2 · CP-06 extendido | Consentimiento (3 tarjetas, botón inactivo hasta el checkbox) → crear cuenta «paso 1 de 3» con correo NUEVO → clave con reglas que cambian de icono (probar corta / `Bucaramanga` / `bogota` / confirmación distinta) → tutorial: **tocar «Saltar por ahora»** en el primer momento → Inicio. Luego Ajustes → «Ver el tutorial de nuevo» → práctica real con la clave → nube | Fiel a v2-05..v2-08; el salto lleva a Inicio sin práctica y sin dejar el tutorial inaccesible; en el repaso la práctica rechaza una clave incorrecta y fija el logro con la real; la nube regresa a la fachada al instante |
| A3 | **Inicio, pestañas y Device Explorer #1** — B2 · P-28 final · P-71(6) · CP-05/28/31 | Inicio con saludo Marcellus, tarjeta del cuestionario sin conteo (D8), accesos rápidos, engranaje. **Tarjetas de «Acciones rápidas»: comprobar que Llamar · Guardar una nota · Informarme tienen el MISMO ancho y la MISMA altura**, primero con la letra normal y después repitiendo la mirada con fontScale 2.0 en A13. Cambiar de pestaña varias veces → Home → reabrir. **Abrir la Guía una vez con red** (deja caché para A4). Device Explorer: `shared_prefs/search_index.xml`, `store_meta.xml`, **`account_meta.xml`**, `backup_meta.xml` | Fachada al reabrir y reingreso por código que **cae en Inicio** (P-67); atrás no sale del grafo a medias; la nube visible en las tres pestañas. **Las tres tarjetas de acciones rápidas quedan parejas** (misma altura, ícono y texto centrados, texto en máximo dos líneas) en cualquier ancho. Como cada tarjeta centra su propio bloque, el ícono de la que ocupa dos líneas queda unos 9 dp más arriba que los otros dos: mirar si molesta. Todos los XML solo con Base64 bajo claves neutras (`a0`/`a1`, `s0`/`s1`…), ningún rastro de clave, correo ni contraseña |
| A4 | **Reingreso silencioso sin red** — P-71 (1)(2)(7) · CP-08/09/16/23 · RNF-08 | **Modo avión TOTAL** → código → espacio interno abre con chip «Sin conexión» → ver Mi registro y Guía (caché) → crear un registro → nube → reentrar (chip persiste, lo local sigue). **Volver la red sin salir** → chip desaparece; consola `users/{uid}/entries` muestra el doc nuevo. **(7)** con red: salir con la nube justo tras teclear el código (silencioso en curso) → reentrar | Entrada sin pedir contraseña ni red; chip solo mientras no hay confirmación; **el chip se va solo entre ~5 s y ~65 s tras devolver la red (primer reintento a los 5 s, luego 15 s y 45 s — P-81)**; sync diferida al reconectar (RF-38, P-69a de paso); la salida rápida lleva a la fachada al instante y la reentrada pide solo el código. **Anotar si el parpadeo «Sin conexión → comprobando → Sin conexión» molesta** (puede verse hasta tres veces en el primer minuto del paso 1, porque los tres reintentos también corren en modo avión; residual P-70 d) |
| A5 | **Cerrar sesión y login manual** — P-71 (4) · P-29 · CP-08/11 · B2 (P08) | Ajustes → «Cerrar sesión» → reentrar: código → **login manual** «Hola de nuevo.» (el blob se destruyó). P-29: contraseña mala / correo inexistente / correo malformado; 5 fallos seguidos; forzar detención con la pausa activa; esperar que expire. Login correcto | Reingreso exige código + credenciales; mensaje ÚNICO «Los datos no coinciden…» en los tres casos; «Pausa temporal» desde 5:00 con botón deshabilitado y cuenta regresiva que se retoma tras reinicio; tras el login correcto el **blob renace** (el siguiente reingreso vuelve a ser solo código) |
| A6 | **Guía + tipos nuevos t05/t06** — CP-13/14/16 · B3 · P-73(c) 1.ª parte | Con el APK v5 YA instalado: en `tools/seed`, `npm run publicar-tipos -- --publicar` (solo t05 «Violencia digital» y t06 «Acoso sexual»). En la app: portada de tres secciones + «Puedes leerlo sin conexión» (probar en modo avión); tipos con icono/acento (t01 azul-figura, t02 lila-lóbulos, t03 rosa-círculo, t04 arena-tarjeta, **t05/t06 con el visual que traen del esquema v2**); detalle; rutas por sector; entidad; líneas | Fiel a v2-11..v2-16; **6 tipos** en la Guía en el orden publicado; los colores respetan el significado fijo; contenido real de producción; sin red se lee desde la caché |
| A7 | **Registros con tipos dinámicos** — P-46 completo (CP-20/22) · P-73(c) · B4 · B8 · Device Explorer #2 | Mi registro vacío → «Agregar»: los **chips de tipo son los 6 topics publicados, mismo orden y visual que la Guía**; guardar vacío; tipo sin texto; fecha con el selector; segundo registro con fecha anterior; detalle; teclear >5 min; nube a mitad; edición prellenada (mismo doc en consola, `payload`/`iv` nuevos, **`schemaVersion` 3**); edición en modo avión; eliminación con doble confirmación («Conservar» primero), también en modo avión. **Dejar al final 3 registros** (uno de t05 o t06) para A8/A10. Device Explorer: `databases/journal_store.db` y `-wal` | Errores inline sin borrar lo tecleado; orden cronológico inverso; FLAG_SECURE en el selector; la sesión NO expira tecleando; el borrador no persiste tras la nube; el `delete` se propaga al reconectar; BD ilegible; los chips sin elegir con contorno gris y el elegido con su acento |
| A8 | **Adjuntos** — P-59 (CP-21) · Device Explorer #3 | Imagen de galería (sin diálogo de permisos); cámara del sistema (DCIM vacío, regreso directo al formulario; dejarla abierta >5 min); PDF y audio por «Archivo»; visor interno; tipo vedado; «×», tope de 10, abandono con adjuntos; cerrar sesión y reabrir. Device Explorer: `files/media_store/`, `cache/media_pending/`. **Dejar: registro 1 con 2 fotos, registro 2 con PDF + audio, registro 3 sin adjuntos** | Sin permisos pedidos; sin copia en galería/DCIM; ida-y-vuelta sin pasar por la fachada (y CON fachada si expira la gracia); captura bloqueada en el visor; adjuntos persisten; solo blobs de 32 hex ilegibles; `media_pending` vacío |
| A9 | **Cuestionario semafórico + guardado opcional** — P-43 (CP-17/18) · **CP-19 (RF-31)** · P-69(a) · B4 | **Portada: confirmar la viñeta «Tú decides / …solo se guarda si tú lo eliges»** (texto alineado con RF-31 en `d5e1dc2`; el mockup P15 aún dice lo contrario — P-41). Responder 2 y retroceder; «Abandonar»; nube a mitad; `adb shell am kill`; completar en 360×800 **cuatro veces para forzar los 4 niveles** (todo «Nunca» / dos mixtos / todo «Con frecuencia»); «Ver rutas». **RF-31**: «Terminar» sin guardar → Inicio sin rastro; repetir y «Guardar este resultado» → hoja de confirmación (captura bloqueada) → **«Cancelar» no persiste**; de nuevo → «Guardar» → Inicio «Resultados guardados» (fecha · nivel · señales) → detalle → eliminar (doble confirmación, «Conservar» primario). **Mirar los nombres de los tipos en los tres lugares**: la frase del resultado «Por tus respuestas, aparecen señales de…», la tarjeta del listado y «Señales reconocidas:» del detalle. **Sin red**: guardar otro → reconectar → consola `users/{uid}/outcomes`. Captura en portada/pregunta/resultado/listado | Respuesta marcada al volver; portada vacía tras abandonar/nube/kill; **tarjeta del nivel semafórica dominante con un color distinto por orden 1→4 (verde sereno / ámbar / naranja / rojo profundo) y aviso RF-30 fijo visible SIN scroll en los cuatro**; nada persiste sin confirmar (criterio 1); el guardado deja solo fecha + nivel + tipos y aparece sellado (`payload`/`iv`) en `outcomes` al reconectar; la eliminación desaparece del listado y de la consola; **los nombres de los tipos salen en negrilla en el resultado, en la tarjeta del listado y en el detalle, con el resto de la frase en peso normal y el texto idéntico al de antes** (solo cambia el estilo: RF-31 sigue guardando fecha, nivel y tipos); capturas bloqueadas |
| A10 | **Exportación** — CP-38 / P-72 (RF-45) · B8 · Device Explorer #4 | «Exportar» → marcar 2 (los que tienen adjuntos) → resumen (fechas · tipos con el nombre de la Guía · «4 adjuntos»); contraseña corta (<10) y luego dos distintas; válida → «Guardar en el teléfono» → Descargas → abrir desde Archivos (pedir contraseña; probar una errónea); repetir con «Incluir adjuntos» y «Compartir» → selector → destino real (Gmail/Drive/WhatsApp) → confirmar que llega íntegro; repetir y cancelar; nube a mitad de la contraseña; esperar 5 min en el formulario; captura en la lista en selección y en el formulario. Device Explorer: `cache/doc_out/` y ausencia de `.pdfbox.cache` | Nada marcado al entrar; botón inactivo hasta marcar; interruptor apagado de entrada; errores de contraseña en línea; el PDF abre SOLO con la contraseña, trae fecha, tipo (nombre de la Guía) y relato; sin adjuntos se citan «· no incluido»; con adjuntos cada registro en su página con banda «REGISTRO n DE N», las 2 imágenes enmarcadas, el PDF anexado con cabecera «Registro 1 · Documento adjunto 1 · página x de y» y el audio incrustado (`audio_1.wav` en Acrobat; Chrome/Drive no lo muestran); nombre `documento_AAAA-MM-DD.pdf`; al volver del selector se aterriza en exportar (no en la fachada); `doc_out/` vacío tras compartir, tras la nube y tras los 5 min; capturas bloqueadas |
| A11 | **Llamar** — P-26 + P-27 (CP-09/14) | Desde una entidad: «Llamar» → marcador → atrás; mirar recientes del marcador | Se aterriza en la FACHADA (pop en ON_STOP); recientes no muestra la pantalla de líneas; anotar si el número queda visible en el marcador (si sí: ampliar `content_agency_call_note`) y el costo UX del reingreso |
| A12 | **Ajustes y flujos de extensión** — B5 · B8 · CP-05/09 | Tres secciones con «Eliminar todo» en ciruela y pie «Prototipo académico»; cambiar clave (actual errónea / nueva corta / éxito y verificar en la barra: la vieja responde como búsqueda fallida, la nueva abre); privacidad; **NO cambiar la contraseña aún** (va en B4) | Coherentes con el sistema V2; la clave nueva es la única puerta (RF-09/12/13); campos con el contorno B8 |
| A13 | **Fuente grande** — P-64 / B7 | Ajustes del sistema → fontScale 2.0 → tres pestañas, **tarjetas de «Acciones rápidas» en Inicio**, cuestionario, lista de registros en selección, formulario de exportar | Ningún texto cortado; **las tres tarjetas de acciones rápidas siguen midiendo lo mismo** (altura de la más alta, contenido centrado, etiqueta en máximo dos líneas). **Aviso: en 360 dp con fuente 2.0 las etiquetas NO caben y se cortarán con puntos suspensivos** — la altura pareja sí se cumple, que es lo que arregla este cambio; si el corte resulta inaceptable, la salida es apilar los tres accesos en columna por encima de cierta escala de letra, y se abre un pendiente. La nube NO solapa la barra de pestañas (si la barra crece: medirla con `onSizeChanged`, P-64) |

### Bloque B — Segundo dispositivo, restauración y contraseñas (emulador B limpio)

| # | Qué correr | Pasos (resumen) | Resultado esperado |
|---|---|---|---|
| B1 | **Restauración íntegra v3** — P-61 (3)(4)(4b) · P-73(c) restauración · RF-31 · CP-24 | Emulador B limpio con el MISMO APK: activación → «¿Ya tienes una cuenta?» → login (misma cuenta; tarda: KDF + descarga) → código NUEVO. **Antes de abrir la Guía ni Mi registro: modo avión** → Mi registro | Los 3 registros ÍNTEGROS (contenido, orden, adjuntos — chips y visor) y el **resultado guardado** de A9 en Inicio; **sin catálogo y sin red los tipos se ven «Sin tipo»** y recuperan el nombre al volver la red o abrir la Guía (P-73c). Consola: `media`/`mediaparts` con blobs ilegibles |
| B2 | **Edición y borrado cruzados** — P-61 (4)(4b) · RF-36 remoto · **ADR 2026-10-06** | Con red en B: editar un registro; eliminar en B el registro con adjunto; **crear en B un registro nuevo**; **guardar en B un resultado del cuestionario y eliminar otro**. En A: entrar al espacio protegido y abrir **Mi registro** y **Resultados guardados**. Repetir una vez con A en modo avión y después devolverle la red. **Caso del conflicto:** en A, sin red, editar un registro que ya estaba subido; eliminar ESE mismo registro en B con red; devolver la red a A y reabrir Mi registro | Consola: mismo doc actualizado, con `doc_id` de 32 hex en los registros nuevos y el id numérico en los anteriores. **En A, sin tocar nada más: el registro eliminado en B desaparece con sus adjuntos, la edición hecha en B se ve, y el registro y el resultado creados en B aparecen.** Nada de esto bloquea la pantalla ni muestra mensajes técnicos: la lista pinta primero lo local y se actualiza sola en un segundo momento. **Sin red todo sigue igual que hoy** (nada desaparece ni cambia hasta que vuelve la conexión). **En el conflicto gana la eliminación:** al volver la red, el registro que A había editado desaparece, porque en B se eliminó. Comprobar también que un registro creado en A sin conexión NO desaparece al reconciliar (nunca llegó a subir) |
| B3 | **Contraseña restablecida → respaldo ilegible y reingreso manual en A** — P-61 (5)(6)(7) · **P-71 (3)** · **RF-46 (CP-39)** | **Primero desde la app (RF-46):** en la pantalla de inicio de sesión, «¿Olvidaste tu contraseña?» → leer el aviso → probar con un correo que NO tiene cuenta, después con el de la cuenta real y después con uno mal formado, comparando que el mensaje final sea idéntico en los tres; probar también sin conexión; pedirlo SEIS veces seguidas: el mensaje en pantalla será idéntico las seis, pero a la bandeja deben llegar solo CINCO correos (la pausa no se muestra a propósito, decir «estás en pausa» delataría que alguien pidió el restablecimiento desde ahí hace poco). Después, comprobar que se puede iniciar sesión con normalidad. **Requisito previo: la plantilla del correo debe estar personalizada en la consola (P-19), que ahora es bloqueante del piloto** — revisar el correo recibido palabra por palabra, y también la página que abre el enlace. Luego, con la contraseña ya restablecida sin usar A ni B. Emulador C (o B recién desinstalada): login con la NUEVA → código → Mi registro. **Volver a A**: código en la barra | C: vacío + aviso «No fue posible recuperar tu información anterior…» UNA vez; consola `entries` purgado y `g` incrementada. **RF-46: el mensaje de confirmación es el MISMO con un correo que tiene cuenta, con uno que no y con uno mal formado** (si difieren, la pantalla serviría para averiguar quién tiene cuenta); sin conexión dice que el correo no salió, que es lo único que se distingue; al sexto envío el mensaje no cambia pero ese correo ya no sale, y el inicio de sesión sigue funcionando sin pausa. **En A, tras volver a entrar: sus registros y adjuntos siguen TODOS ahí** — la purga del respaldo no puede llevarse nada local (es lo que promete el aviso previo; la reconciliación lo respeta exigiendo la generación vigente para borrar). **A: aparece el login manual (el blob se destruyó al fallar el silencioso)**; con la nueva contraseña entra, el blob renace y **sus registros locales se re-suben solos** (reaparecen en consola) |
| B4 | **Cambio de contraseña desde la app** — P-61 (8) · CP-05 | En A: Ajustes → «Cambiar tu contraseña» (actual correcta; también actual incorrecta y sin red) → cerrar sesión → login en B con la nueva | Error genérico con la actual incorrecta; «nada se ha modificado» sin red; B entra y los registros siguen intactos; consola `meta/profile` cambió `r` SIN cambiar `g` y `entries` NO se re-escribió |
| B5 | *(condicional)* **Migración v4→v5 con datos previos** — P-73(c) migración · P-61 v1→v2 | Solo si conservas una instalación con el APK anterior (`main`) y registros: actualizar sin desinstalar → login | Los registros conservan su tipo (`storageCode`); en modo avión SIN haber abierto nunca la Guía se ven «Sin tipo» y recuperan el nombre al volver la red; en consola los docs pasan a `schemaVersion` 3 sin perder ninguno |

### Bloque P — Panel web y reflejo en la app (A y B vivos)

| # | Qué correr | Pasos (resumen) | Resultado esperado |
|---|---|---|---|
| P1 | **Panel V2 + esquema v2 + P-68** — B6 · CP-25/26/27 · P-73(c) retiro | En `admin/`: login con marca Aurora y rechazo genérico; contenido con iconos por tipo y chip de estado; **editor de tipo con el selector de icono/acento (biblioteca cerrada)**: cambiar icono y acento de t05 → publicar → en la app, Guía y chips del registro; rutas/líneas con aviso de integridad; cuestionario con publicar bloqueado y motivo. **Retirar (despublicar) t06** → en la app el registro de t06 → republicar. **Borrador vs. publicado (2026-10-06):** abrir t05 en el editor SIN tocar nada y comprobar qué texto muestra; luego guardar un borrador con un cambio pequeño, mirar el aviso antes de publicar y **descartarlo sin publicar** (o publicarlo y volver a correr `publicar-tipos -- --publicar`, que repone el texto validado) | Fiel a v2-24..v2-28; el cambio de icono/acento se refleja en la **Guía Y en los chips y detalle del registro** (un solo origen, P-68); el registro del tipo retirado conserva el **último nombre publicado con visual neutro**, y lo recupera al republicar; validaciones y flujos funcionales como en B2/B3. **t05 debe abrirse con el texto validado que muestra la app, NO con el «Prueba» de julio** — y ahora sin ningún aviso, porque **P-87 ya se ejecutó (2026-10-06)**: los cinco borradores obsoletos de producción se borraron y `publicar-tipos` dejó los de t05 y t06 idénticos a lo publicado, así que la colección `drafts` tiene solo esos dos y ninguno está obsoleto. **Esto cambia qué comprueba el paso:** el aviso de «borrador obsoleto» ya no puede dispararse de forma natural y su comportamiento queda cubierto por `admin/src/lib/drafts.test.ts` (8 pruebas, incluidas las de borrador más viejo que lo publicado); lo que se mira a mano es que el editor abra con el texto validado, limpio y sin aviso. Al guardar un borrador propio sí debe aparecer el aviso de que lo que se publicaría es distinto de lo que la app muestra hoy |

### Cierre — Eliminación total (al terminar A, B y P)

| # | Qué correr | Pasos (resumen) | Resultado esperado |
|---|---|---|---|
| A14 | **RF-20 con todas las cascadas** — P-54 regresión · P-71 (5) · P-59 (8) · RF-31 · CP-12 · **regresión H-1 (ADR 2026-10-05)** · Device Explorer #5 | **Primero la regresión H-1:** con sesión abierta y datos apagados (no modo avión total, P-82), «Eliminar todo y salir» con la doble confirmación → la parte remota falla y la marca queda izada → cerrar la app → datos encendidos → reabrir y reingresar. **Luego la eliminación real:** Ajustes → «Eliminar tu cuenta y tus datos» → dos confirmaciones → contraseña (probar primero sin red y con contraseña incorrecta) → éxito. Device Explorer completo. Reabrir la app | **H-1:** al reingresar no queda ningún registro, resultado ni adjunto local y la marca baja en el siguiente arranque (sin «failed to complete startup»). Sin red / incorrecta: «Nada se ha eliminado todavía» y consola intacta; con éxito: consola sin `entries`/`media`/`mediaparts`/`outcomes`/`meta` y sin la cuenta en Auth; **`account_meta.xml` inexistente**, `media_store`/`media_view`/`media_pending`/`doc_out` inexistentes, `journal_store.db` y **`guidance_store.db`** ausentes; la app vuelve a ser solo clima (en debug el código de activación se resiembra; en release exigiría reinstalar — P-55) |

### Bloque S — Seguridad y regresión visual de v0.3.1 (rama `fix/hallazgos-owasp`)

Lo que dejaron las correcciones de la evaluación OWASP. **S1 es bloqueante del tag
v0.3.1**: es la única verificación de H-7 que ninguna prueba automática puede
hacer, porque comprueba el menú que dibuja el sistema operativo, no lo que Compose
le pasa (eso ya lo fija `SecureTextEntryTest` en las instrumentadas). Instalar
primero: `./gradlew installDebug` y comprobar `lastUpdateTime` antes de mirar el
emulador — `assembleDebug` no instala.

| # | Qué correr | Pasos | Resultado esperado |
|---|---|---|---|
| S1 | **P-94 (H-7) — el menú del relato** · **bloqueante del tag** | Espacio interno → Mi registro → «Agregar» → escribir 3-4 palabras en «Qué pasó». Provocar el menú de selección de **tres** formas: mantener pulsado sobre el texto, doble toque sobre una palabra, y arrastrar los tiradores de selección; repetir con «Seleccionar todo» aplicado. Hacerlo con los gestos del emulador, **no** con Cmd/Ctrl+C del teclado del equipo, que no pasa por el menú (residual declarado en P-95) | En las cuatro formas el menú ofrece **«Pegar» y «Seleccionar todo»** y **NO** ofrece «Copiar», «Cortar» ni «Compartir». Si apareciera «Copiar» en alguna de las cuatro, la corrección de H-7 no está cerrada y el tag no sale |
| S2 | **P-94 — «pegar» sigue disponible** (usabilidad: la corrección no debía costarla) | Copiar cualquier texto en otra app del emulador y pegarlo en: «Qué pasó», el correo de inicio de sesión, el de crear cuenta y el de «¿Olvidaste tu contraseña?» (RF-46) | Los cuatro campos pegan. El de RF-46 importa aparte: vive dentro de una hoja, que instala su propia barra de selección |
| S3 | **P-94 — las dos barras** (RF-12/RF-14) | Teclear en la barra de la fachada y en la de práctica del tutorial; mantener pulsado el texto en cada una. Comprobar también que escribir y borrar sigue fluido | Sin «Copiar» ni «Cortar», con «Pegar». El foco no se pierde al teclear (lo cubre `WeatherSearchBarFocusTest`, esto solo confirma el APK instalado) |
| S4 | **P-11 — el teclado no aprende** (comprobación de la usuaria, repetible) | Escribir una **palabra inventada** en el relato y otra en la barra de la fachada; salir, abrir otra app con un campo de texto y empezar a teclear las primeras letras de cada una | Gboard no las sugiere. Esta es la comprobación que desmiente o confirma la corrección de P-11: antes las barras no tenían protección mecánica ninguna, pese a lo que decía el comentario del código |
| S5 | **P-90 — confirmación corta de la caída** | Teclear en la barra y pulsar Inicio 3-4 veces; lo mismo en el relato | 0 caídas. Las 10+10 ya están anotadas; esto solo confirma el APK instalado |
| S6 | **Regresión visual por material3 1.2.1 → 1.3.1** (no es seguridad; la trae la subida de BOM de H-2) | Mirar en pantalla: selector de fecha del registro (A7), casilla del consentimiento (A2), casilla y interruptor «Incluir adjuntos» de la exportación (A10), los indicadores circulares de carga, y los campos del kit en login, clave y exportación | Comparar con `docs/mockups-v2/` y con el contraste B8 (relleno blanco, contorno gris `#7E788E` que se engrosa con foco). Anotar diferencias de tamaño, color o separación: esa versión cambia métricas y colores por defecto de esos componentes |
| S7 | **Re-smoke de la salida rápida y del tutorial tras el renombre de H-8** (RF-16, RNF-03, RF-10, RF-19) | Entrar con la clave → pulsar la nube desde Inicio, desde el detalle de un registro y desde el cuestionario, cronometrando → comprobar que «atrás» no regresa al espacio interno → Ajustes → «Ver el tutorial de nuevo» → práctica de ingreso con la clave real → nube → Ajustes → «Cerrar sesión» → reingreso | La nube devuelve al clima en menos de 1 s en los tres sitios, con la barra vacía; «atrás» no regresa; el tutorial completa su práctica; el reingreso exige clave y credenciales. **Por qué está aquí:** A2 y la transversal de RF-16 quedaron en ✅ el 2026-10-07, y el renombre `quickExit`→`closeNow` del 2026-10-10 tocó esa ruta después. El renombre está probado archivo por archivo como puro, así que esto es confirmación, no sospecha — pero una lista única no debe dejar un ✅ cuyo código cambió luego |
| S8 | **La red sigue funcionando con la configuración nueva** (H-5, P-93) | Buscar una ciudad en la fachada y ver el pronóstico; entrar y comprobar que la nube sincroniza un registro nuevo | Clima real y sincronización en verde. Son 30 s, y conviene porque el archivo no lleva `<debug-overrides>`: si algo quedara mal declarado, se vería como un fallo de red y no como un fallo de configuración |

### Bloque C — Plataforma (recomendadas antes del tag, no encadenadas)

| # | Qué correr | Pasos | Resultado esperado |
|---|---|---|---|
| C1 | **CP-34 (RNF-07) — gama media + arranque en frío (ADR 2026-10-05, P-80)** | Emulador perfil gama media (2 GB RAM, ej. Pixel 2 API 30) o dispositivo real; instalar el RELEASE; **`adb shell am force-stop com.auroraclima.app` y arrancar en frío 3 veces midiendo con `adb shell am start -W com.auroraclima.app/.MainActivity` (anotar `TotalTime` de cada una)**; cronometrar arranque en frío, fachada→código→pestañas, **primera entrada sin red** y **generación de un PDF con los 4 adjuntos** | Ninguno de los 3 arranques muestra «failed to complete startup» y la fachada aparece en ≤ 3 s (`TotalTime` ≤ 3 000 ms; el AVD saturado de P-82 no sirve de referencia); respuesta ≤ 2 s; la exportación muestra progreso y termina sin ANR; sin jank en las listas |
| C2 | **CP-36 (RNF-10) — API 26** · **RF-14 bajo API 33** | Emulador API 26 (minSdk); instalar debug y release; recorrer fachada, activación, pestañas, guía, cuestionario (guardar resultado), registros, **exportar y compartir**. **Además:** teclear el código a medias → Home → mirar la miniatura de recientes | Sin crashes ni degradación visual de los componentes V2; FLAG_SECURE operativo; PdfBox escribe y el selector de documentos funciona en minSdk. **La miniatura PUEDE mostrar el texto a medias: `setRecentsScreenshotEnabled` existe solo desde API 33, así que bajo minSdk solo queda la limpieza en ON_PAUSE — limitación documentada (nota de RF-14 y ADR 2026-10-05), NO una regresión.** Anotar lo observado |

## 9. Pasadas de agentes sobre el diff completo `main..HEAD` (2026-10-05)

Dos pasadas el mismo día: la **intermedia** (§9.1) sobre los 31 commits que
cerraban RF-45, antes de la corrida manual por adb; y la **final** (§9.2) sobre
los 34 commits de la rama, después de los tres ajustes que dejó esa corrida
(`d5e1dc2`, `429f283`, `e3c74e7`). Cada uno de esos tres commits pasó además
por los tres agentes en su propia sesión (veredictos y observaciones atendidas
en sus mensajes de commit).

### 9.1 Pasada intermedia (`f69f1ff..5c93cb3`, 31 commits)

Ejecutada sobre el diff íntegro de entonces (207 archivos, +10 644/−800; app +
`admin/` + `tools/seed`), cada agente con su encargo. Los tres: **APROBADO CON
OBSERVACIONES**, cero bloqueantes. Las dos correcciones de código que pidieron
se aplicaron en esa misma sesión (commit `5c93cb3`) y la suite se re-ejecutó
después en verde.

- **revisora-arquitectura: APROBADO CON OBSERVACIONES** (0 bloqueantes, 7
  mejoras). Verificado en verde: regla de dependencias §2 — ninguna feature
  importa de otra, `core/` no importa de features, los `domain` nuevos son JVM
  puro (`DayProvider` con `java.time`, `ExportDocumentWriter` con
  `OutputStream`); el acoplamiento records↔content de P-68 pasa por
  `core/catalog/CategoryCatalog` (interfaz) implementado en
  `content/data/CategoryContentProvider` — mismo patrón que `DirectoryProvider`;
  todo acceso nuevo detrás de interfaces de domain con `Result`
  (`JournalExportRepository`, `ExportDocumentWriter`, `CredentialStore`,
  `SilentSessionOrchestrator`, `SavedOutcomeRepository`, `OutcomeSyncRepository`);
  UiState inmutables con errores `@StringRes`; Hilt completo sin `!!`/`lateinit`/
  `Log`; única dependencia nueva (PdfBox-Android) con ADR y residual P-75; ADRs
  presentes para las seis decisiones de la rama; guarda visual con lista legada
  vacía y `FormElements.kt` borrado sin referencias; cada ViewModel/caso de
  uso/repositorio nuevo con test. **Resueltas hoy:** M-1 (constantes muertas
  `BAND_LABEL_BASELINE`/`BAND_HEADLINE_BASELINE` de `PdfPageLayout`, ahora usadas
  en `band()`) y M-2 (`BundledFontMapper` registrado como excepción explícita a
  §3 en el ADR 2026-10-03). **Registradas como P-79:** M-3 (`PdfPageLayout` 437
  líneas, extraer medidor de texto y archivos incrustados), M-4 (`LoginViewModel.
  init` combina tres contratos → `ResolveEntryModeUseCase`), M-5 (`AuroraApp()`
  342 líneas), M-6 (`GuidanceScreen.kt` 791 líneas), M-7 (tres adaptadores de
  3–5 líneas sin test propio). Confirmó vigentes y suficientes P-63, P-69d, P-74,
  P-75, P-76, P-77.
- **auditora-seguridad: APROBADO CON OBSERVACIONES** (0 bloqueantes, 0 altas,
  1 media, 2 bajas). Las 7 reglas verificadas sobre lo nuevo: (1) FLAG_SECURE —
  `journal_export/{ids}` y `guidance_history` dentro de `secureGraph`, hojas
  nuevas sobre `SecureSheet`, selectores del sistema por `SystemLauncher`
  (ida-y-vuelta centralizada), `NoTracesGuardTest` cubre sin cambios; (2)
  RF-12/13 — la compuerta de la barra no cambió (solo el borde WCAG), la decisión
  blob/sesión/login ocurre YA dentro del grafo seguro; (3) salida rápida —
  `SilentSessionCoordinator` es `SensitiveStateCleaner`, `SilentSignInUseCase`
  deshace bajo `NonCancellable`, `doc_out` barrido al volver / al cerrar sesión /
  al arrancar, contraseñas de exportación vaciadas en `onShareReturned`, tras
  guardar y en `onCleared`; (4) cero `Log`/`println`/telemetría en `src/main`,
  excepciones con texto neutro, `BundledFontMapper` evita `/system/fonts`,
  `.pdfbox.cache` y `System.err`; (5) cifrado — `account_meta` AES-256-GCM con
  llave Keystore y relleno fijo 512 B, `guidance_store.db` solo por
  `EncryptedDatabaseFactory`, `outcomes` sellados con la DEK, `label` del payload
  v3 DENTRO del cifrado, PDF AES-256 R6 con `javax.crypto` sin metadatos de
  autor; (6) manifest fusionado sin permisos ni componentes nuevos (único
  provider `FileProvider` no exportado con exactamente dos `cache-path`); (8)
  camuflaje — strings nuevos neutros, se RETIRARON `journal_kind_*` de
  `strings.xml`, `resources.arsc` sin términos prohibidos, nombres de BD/
  colecciones/archivos neutros, ids de icono/acento nombran dibujo y matiz, assets
  de PdfBox sin `cmap`. **Corregida hoy (M-1, media):** `StoredCredentials` era
  `data class` con `toString()` por defecto cuya plantilla `(email=…, password=…)`
  sobrevive a R8 — sin fuga activa (nada la stringifica), defensa en profundidad:
  ahora sobreescribe `toString` sin campos (+`StoredCredentialsTest`). **Residuales
  nuevos:** **P-78** (B-1: plantillas `toString` de `data class` legibles en el DEX
  release — `questionnaire=`, `ConsentUiState(` preexistentes — decidir rename o
  `toString` propio antes del piloto) e inciso en **P-65** (B-2: los `Log` internos
  de PdfBox solo se eliminan en release). Confirmó P-70, P-69, P-74, P-75, P-76,
  P-65/66 vigentes sin duplicarlos.
- **verificadora-rf: APROBADO CON OBSERVACIONES.** Mapa criterio → evidencia
  completo para RF-10/21/22/25/29/30/31/12/15/19/20/32/35/37/39/45 y
  RNF-01/02/03/04/05/08: **100 % con implementación localizada y test
  automatizado**, sin regresión en RF-13/RF-16/RNF-03 (`core/security` solo gana
  `SystemLauncher`; `SessionManager` intacto). Corrida puntual de confirmación:
  19 clases, **192 tests en verde**. Destacados: RF-31 criterio 1 fijado por
  `GuidanceViewModelTest` («terminar o abandonar sin confirmar no toca el
  repositorio», «salida rápida con la hoja abierta descarta»); RF-15 — el login
  silencioso NO cuenta para el bloqueo por intentos (`SilentSignInUseCase` llama
  al repositorio directo; el contador vive solo en `SignInUseCase`) y el blob
  nace solo con éxito; RF-32 — «sin tipo elegido no se guarda aunque el catálogo
  no esté»; RF-45 criterio 5 — barrido triple del temporal probado en tres
  suites. **Observaciones atendidas en este documento:** la §8 anterior no
  contenía P-71 ni P-73c y faltaban tres huecos manuales — RF-10 saltable
  (ahora A2), los cuatro niveles semafóricos (A9) y RF-31 completo con «Cancelar»
  en la hoja, cascada RF-20 sobre `guidance_store.db`/`outcomes` y restauración
  en B (A9/A14/B1); `ACCESS_NETWORK_STATE` anotado en §3 y en CP-32; el inventario
  de RNF-04 ganó la nota del blob `account_meta` (`docs/REQUERIMIENTOS.md`).
  Cobertura: **44 de 45 RF** (RF-38 sigue como excepción formal documentada,
  aunque `FirestoreJournalSyncRepositoryTest` ya ejercita el encolado — se
  mantiene el criterio conservador de v0.2.0).

Veredicto conjunto de la pasada intermedia: LISTO PARA MERGE condicionado a la
§8 (superado por la pasada final de §9.2).

### 9.2 Pasada final (`f69f1ff..HEAD` = `429f283`, 34 commits)

Ejecutada el 2026-10-05 al cierre de la jornada, ya con los tres commits que
dejó la corrida manual por adb (`d5e1dc2`, `429f283`, `e3c74e7`) y sobre el
árbol con esta misma documentación actualizada. Encargo de cada agente: el diff
íntegro de la rama (214 archivos, +11 225/−1 054), con lectura línea a línea de
esos tres commits y confirmación de que el mapa de la pasada intermedia sigue
vigente. **Cero bloqueantes y cero cambios de código pedidos.**

- **revisora-arquitectura: APROBADO CON OBSERVACIONES** (0 bloqueantes, 4
  mejoras nuevas de baja prioridad). Barrido mecánico sobre todo
  `app/src/main/java`: **0 importaciones entre las seis features** (30 pares
  comprobados), 0 importaciones de `core/` hacia features (`core/catalog` y
  `core/directory` solo importan stdlib, como corresponde a contratos de
  inversión), 0 `*/ui` importando de `*/data`, 0 `*/domain` con `android.*`/
  Room/Firebase/Compose, 0 ViewModels con Firebase/Room/HTTP. En las líneas
  añadidas del diff completo: **0 `!!`, 0 `GlobalScope`/`runBlocking`, 0
  `Log`/`println`**; el único `lateinit` es el `@Inject` de `dagger.Lazy` en
  `AuroraApplication` (DI, permitido). Los dos patrones del ADR 2026-10-05 se
  cumplen al pie: `dagger.Lazy<Set<AppInitializer>>` resuelto dentro del
  `launch` (regla a) y `Provider<Set<LocalAccountDataEraser>>` resuelto solo con
  la marca izada (regla b). `RecentsThumbnailGuard` vive en `core/security` con
  el mismo criterio que FLAG_SECURE y su único consumidor es la raíz de
  composición. Revisó la lógica nueva del coordinador: `withTimeoutOrNull`
  distingue correctamente «volvió la red» (rearma las esperas) de «venció el
  plazo» (reintento caliente), y la limpieza de sesión la cancela. Tests con
  fakes, sin mocks de framework (0 `mockk`/`Mockito` en todo el diff).
  `ktlintCheck` exit 0. **Mejoras nuevas registradas como P-83:** (a)
  `SilentSessionCoordinator.start()` llegó a 35 líneas; (b) la guard de
  recientes no admite test de comportamiento (Android no expone getter) y
  conviene anotar la limitación en el ADR; (c) `DebugSecretCodeSeeder` recibe el
  hasher por valor y no por `Provider` (impacto nulo, inconsistencia con la
  letra de la regla b); (d) `SystemLauncher` sin test propio y ausente de
  P-79(e). **Confirmó vigentes sin duplicar:** P-79 (a–e), P-80, P-63, P-69d,
  P-74, P-75, P-76, P-77.
- **auditora-seguridad: APROBADO CON OBSERVACIONES** (0 críticos, 0 altos, 4
  medias: dos nuevas corregidas en el acto, una ya registrada y una que amplía un
  residual). Re-ejecutó los barridos mecánicos sobre el diff íntegro, todos en
  verde: 0 `Log`/`println`/`printStackTrace`/`Timber`/`Crashlytics`/
  `FirebaseAnalytics` en `src/main`; 0 llaves, salts o contraseñas literales y 0
  `buildConfigField`; FLAG_SECURE con un único punto de aplicación; 309 strings
  sin un solo término vedado; el hash del código en tiempo constante y la
  respuesta visible esperando siempre a la búsqueda real (RF-13). Verificó el APK
  release de forma independiente y coincide con §2 y §3. Respondió las dos
  preguntas centrales de la sesión: **la cascada local de RF-20 sigue garantizada**
  tras el cambio de arranque — `dagger.Lazy` solo difiere la construcción, no la
  vuelve condicional, y el `Provider` devuelve el conjunto completo de los nueve
  erasers con la misma semántica de fallo; y **la guard de recientes no es un
  distinguidor de RF-13**, porque se activa con «hay texto en la barra» y oculta
  la miniatura en cualquier búsqueda, sin consultar el contenido ni el resultado
  de la puerta. **Hallazgos nuevos, los dos corregidos en esta sesión:** A-1, la
  limpieza de la barra en ON_PAUSE bajaba la protección antes de dibujar el frame
  sin el código, así que un snapshot en esa ventana sub-frame podía volver a
  fotografiarlo; y A-2, el bucle de inicializadores no contenía excepciones, de
  modo que un servicio ausente podía tumbar el proceso — y una app de clima que
  no arranca justo después de una eliminación interrumpida sería una anomalía
  observable. **Las otras dos quedaron registradas:** A-3 → **P-84** (bajo API 33
  no existe la API y solo queda ON_PAUSE: decide la política del piloto) y A-4 →
  ampliación de **P-78** (el `keep` de Room conserva la ruta del paquete, así que
  el segmento `questionnaire` sobrevive a R8 en el DEX release). Confirmó la
  pasada intermedia vigente y P-70 sin duplicar.
- **Segunda ronda sobre las dos correcciones (misma jornada).** Los tres agentes
  volvieron a pasar sobre ellas, y las dos primeras versiones no sobrevivieron
  iguales: **A-2 quedó APROBADO de entrada**, pero **A-1 fue RECHAZADO por
  seguridad y por arquitectura con el mismo diagnóstico** — la corrección era
  insuficiente porque `hidden` seguía siendo clave del `DisposableEffect`, y al
  cambiar una clave Compose ejecuta el `onDispose` del efecto viejo, que
  restauraba la miniatura justo en el instante del ON_PAUSE: el latch quedaba
  anulado y el estado neto era idéntico al del código sin corregir. La segunda
  versión saca `hidden` de las claves, lee el valor vivo con
  `rememberUpdatedState` y extrae la decisión a `recentsThumbnailDecision`,
  lógica pura con un disparador explícito por momento (`SearchChanged`,
  `ReturnedToForeground`, `LeftFacade`) y `null` para «no tocar el flag», con
  `RecentsThumbnailGuardTest` (6) fijando el caso crítico. Sobre ella, **los tres
  dieron APROBADO**: seguridad recorrió los caminos de salida uno por uno
  (segundo plano, navegación al grafo seguro, pronóstico, rotación, salida
  rápida) y confirmó que ninguno deja la fachada sin miniatura de forma
  permanente ni expone el código; arquitectura cerró su bloqueante y adoptó el
  enum de tres disparadores como forma definitiva, porque la alternativa de dos
  parámetros no podía expresar que al abandonar la fachada hay que restaurar
  aunque no haya primer plano. Sus últimas observaciones se aplicaron antes del
  commit: el sitio de llamada pasa los valores reales en lugar de literales que
  no describen la realidad, el `when` quedó explícito en vez de `takeIf`, y el
  ADR del guard dice ahora que abandonar la fachada restaura siempre porque de
  ahí en adelante manda FLAG_SECURE.
- **verificadora-rf: APROBADO CON OBSERVACIONES** (0 defectos de código; 5 notas
  de redacción en la §8, todas incorporadas ya a este documento). Re-ejecutó 6
  clases puntuales en verde (`SilentSessionCoordinatorTest` 10,
  `PendingErasureRecoveryTest` 3, `NoTracesGuardTest` 8, `WeatherViewModelTest`
  16, `CamouflageResourcesTest` 2, `GuidanceViewModelTest` 24) y comprobó que
  los conteos declarados entonces en §1 y §4 cuadraban (529 + 181 = 710; 75 + 23
  = 98; las correcciones posteriores los dejaron en 720 y 100).
  Mapa criterio → evidencia de los tres commits: **RF-14 c2 CUMPLIDO en API
  33+** (la guard se activa con `isSearchActive`, que es «hay texto en la
  barra», de modo que oculta la miniatura en CUALQUIER búsqueda y no solo con el
  código — no es un distinguidor de RF-13; bajo API 33 solo queda ON_PAUSE, y
  esa limitación pasó a C2); **RF-15/RNF-08 CUMPLIDO** con las esperas
  5/15/45 s, el rearme por regreso de red y el reintento inmediato, y el
  silencioso sigue sin contar para el bloqueo por intentos; **RF-16/RNF-03 sin
  regresión** (`core/security` solo gana `RecentsThumbnailGuard` y
  `SystemLauncher`; `SessionManager` intacto); **RF-20 regresión H-1 CUMPLIDO**
  (la cascada no se construye sin la marca y sí con ella, incluido el caso de un
  eraser que falla y deja la marca izada); **RF-31 CUMPLIDO** con el texto nuevo
  de la portada y sin claves huérfanas; **RNF-07 PARCIAL por diseño** — el
  arranque ya no construye Firebase en `Application.onCreate`, pero P-80 sigue
  vivo en `MainActivity` y la medición ≤ 3 s es manual (§8-C1). Verificó además
  la trazabilidad completa de la §8: los ocho pasos de P-71, P-73(c) en
  A6/A7/B1/B5/P1 y la cobertura 44 de 45 RF con RF-38 como única excepción
  formal. **Sus cinco observaciones ya están aplicadas:** pasos de la regresión
  H-1 en A14, medición `am start -W` en C1, miniatura bajo API 26 en C2, plazo
  del chip y parpadeo P-70(d) en A4, viñeta «Tú decides» en A9.

Veredicto conjunto de la pasada final: **el diff `main..HEAD` queda LISTO PARA
MERGE a `main`, condicionado únicamente a las corridas manuales de la §8**, que
ejecuta la usuaria antes de unir ramas y crear el tag. Los pendientes que la
rama deja vivos son todos no bloqueantes: P-78, P-79, P-80, P-82 y P-83.

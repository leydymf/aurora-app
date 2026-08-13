# Aurora — Resultados de pruebas (cierre v0.3.0-trl4 · UI V2)

> Evidencia para el informe. Corrida del **2026-08-12** sobre el árbol completo
> (app + panel), rama `ui-v2` (43 commits sobre `v0.2.0-rc1`), previa al merge a
> `main` y al tag `v0.3.0-trl4`. Alcance: **rediseño visual completo V2**
> (PLAN_UI_V2.md — sistema de tokens en `core/ui/v2`, 22 pantallas móviles + 5
> web re-estiladas, pestañas de fachada e internas, portada de Guía, Inicio con
> accesos rápidos) **sin cambios funcionales**: ni un RF nuevo ni un contrato de
> domain/data tocado. La línea base de comparación es la corrida v0.2.0
> (2026-07-30, tag `v0.2.0-rc1`).

## 1. Resumen de la corrida automatizada (v0.3.0 · UI V2)

Comandos: `./gradlew test ktlintCheck lint assembleDebug assembleRelease` →
**BUILD SUCCESSFUL**; en `admin/`: `npm run test`, `npm run lint`, `npm run build`
→ todo en verde. Los tests unitarios se re-ejecutaron forzando
`cleanTestDebugUnitTest`/`cleanTestReleaseUnitTest` (mismo criterio anti-caché
de la verificadora en v0.2.0).

| Métrica | v0.2.0 (2026-07-30) | v0.3.0 · V2 (2026-08-12) |
|---|---|---|
| Pruebas unitarias JVM | 522 por variante (74 clases) | **529 por variante — debug y release, 1058 ejecuciones, 0 fallos, 0 errores, 0 omitidas (75 clases)** |
| ktlint | Sin violaciones | **Sin violaciones** |
| Lint | 0 errores · 56 warnings | **0 errores · 56 warnings** (idéntico a la línea base, detalle abajo) |
| `assembleDebug` / `assembleRelease` (R8 + shrink) | OK | **OK** (§2) |
| Panel `admin/` — vitest | 71/71 | **71/71** |
| Panel `admin/` — eslint + prettier / build | en verde | **en verde** (solo el aviso preexistente de tamaño de chunk de Vite) |

Los **+7 tests / +1 clase** son las adiciones de la V2:
`VisualSystemGuardTest` (3 — la guarda del sistema visual) y ampliaciones de
presentación en `ForecastMapperTest`, `TutorialViewModelTest` y
`AgencyDetailViewModelTest`. Ningún test existente cambió de lógica —
consistente con el principio «solo capa ui» del plan.

**Guarda del sistema visual en verde sobre TODO el código**:
`VisualSystemGuardTest` barre las seis features (weather, access, accounts,
content, questionnaire, records) y su lista de excepciones legadas
(`legacyMaterialIconsFiles`) está **VACÍA** — ninguna pantalla define colores
`Color(0x…)`, tipografía propia (`FontFamily(`/`R.font.`) ni importa iconos de
Material por fuera de la librería cerrada `V2Icons`. Todo el árbol consume los
tokens de `core/ui/v2`.

**Comparación de warnings de lint contra la línea base** (cero warnings nuevos):

| Regla | v0.2.0 | v0.3.0 | Nota |
|---|---|---|---|
| `GradleDependency` | 45 | 45 | Versiones más nuevas disponibles; informativo (política A2-1) |
| `AndroidGradlePluginVersion` | 3 | 3 | Igual |
| `Typos` / `RedundantLabel` / `PluralsCandidate` / `OldTargetApi` | 5 | 5 | Iguales, falsos positivos conocidos |
| `UnusedResources` | 1 | 1 | `color.night` preexistente. **6 huérfanos que la migración V2 dejó** (drawables V1 `ic_book`/`ic_compass`/`ic_folder`/`ic_rule_done` reemplazados por `V2Icons`, y `access_panel_journal_title`/`_body` del panel-menú viejo) se **eliminaron** en esta sesión (commit `chore(ui)` del cierre) |
| `ApplySharedPref` | 2 | 2 | Deliberados de RF-20 (cripto-borrado con `commit()` síncrono) |
| **Total** | **56** | **56** | |

**Pruebas instrumentadas** (emulador; no entran en `./gradlew test`): 8 clases /
~29 `@Test`, sin cambios en la V2. Se re-ejecutan con
`./gradlew connectedDebugAndroidTest` en la pasada bloqueante de emulador (§8).

## 2. R8 — Ofuscación del release con los símbolos nuevos de la V2 (RNF-05)

`assembleRelease` con `isMinifyEnabled = true` + `isShrinkResources = true`
(sin cambios de configuración). Verificación sobre `app-release.apk` con
`apkanalyzer dex packages --defined-only` (2026-08-12):

| Nombre buscado en clases propias (`com.auroraclima`) | Coincidencias |
|---|---|
| Sets históricos v0.1.0/v0.2.0 (`SessionManager`, `Secure*`, `Secret*`, `Dek*`, `*Media*`, `*Erasure*`, …) | **0** (sin regresión) |
| **Nuevos UI V2 — sistema visual**: `V2Palette`, `V2Icons`, `V2Shapes`, `V2Theme`, `*TypeVoice*`, `CloudReturnVisual`, `ContentVisual*`, `Surfaces`/`Headers`/`Rows`/`Fields`/`Controls` (core/ui/v2) | **0** |
| **Nuevos UI V2 — estructura**: `InnerTabsHost`, `AuroraApp`, `SecureSheet`, `*TabBar*` | **0** |

Total de líneas propias legibles en el DEX: **las mismas cuatro clases de
siempre y ninguna más** — `AuroraApplication` y `MainActivity` (obligadas por el
manifest / identidad de clima) y `JournalDatabase` / `WeatherDatabase`
(+ `_Impl`) por el constraint de reflexión de Room (términos neutros, residual
hermano de P-44). Todo lo demás renombrado a `a`,`b`,`c`…

**Estado (v0.3.0): VERIFICADO — ningún símbolo de la V2 legible en el DEX.**

## 3. Manifest fusionado y tamaño del APK release

`apkanalyzer manifest permissions` sobre el release de `ui-v2` **y** sobre el
release recompilado del tag `v0.2.0-rc1` (worktree limpio), 2026-08-12 — salidas
**idénticas byte a byte**:

| Permiso | Cambio |
|---|---|
| `android.permission.INTERNET` | Igual |
| `android.permission.ACCESS_NETWORK_STATE` | Igual |
| `com.auroraclima.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Igual |
| `com.google.android.providers.gsf.permission.READ_GSERVICES` | Igual |

Sin permisos nuevos, sin componentes nuevos: la V2 no tocó el manifest.

**Tamaño del APK release** (comparación real, ambos compilados el 2026-08-12
con la misma toolchain):

| APK | Bytes |
|---|---|
| `v0.2.0-rc1` | 23 237 115 |
| `ui-v2` (cierre) | 23 261 848 |
| **Delta** | **+24 733 B (+0,1 %)** |

La V2 no engorda el APK de forma significativa: los ~42 drawables vectoriales
nuevos (iconografía de contorno) y los tokens son XML/código minúsculos, el
resource shrinker retira los recursos V1 que quedaron sin uso y las tipografías
(Marcellus/IBM Plex) se sirven como fuentes del sistema/descargables ya
contadas — no hay assets pesados nuevos.

## 4. Matriz de casos de prueba (CP) — vigente en v0.3.0

La matriz funcional de v0.2.0 sigue vigente SIN cambios de fondo: la V2 no
agregó ni modificó RF, y las 529×2 pruebas re-ejecutadas hoy la respaldan.
Leyenda: **PASA** — cubierto por prueba automatizada; **MANUAL** — requiere
emulador (✅ si la usuaria ya la corrió; si no, es **bloqueante del tag
`v0.3.0-trl4`**, consolidado en §8); **NO APLICA** — trabajo futuro.

| CP | RF/RNF | Estado | Evidencia automatizada / manual |
|---|---|---|---|
| CP-01 | RF-01 | PASA + MANUAL (§8-B) | Auto: `CamouflageResourcesTest` (2), `ManifestMinimizationTest` (6), `NoTracesGuardTest` (7). La fachada V2 exige repetir la verificación visual de camuflaje (§8-B1). |
| CP-02 | RF-02, RF-03, RF-04 | PASA + MANUAL (§8-B) | Auto: `WeatherViewModelTest` (16), `WeatherRepositoryImplTest` (12), `ForecastMapperTest` (ampliada V2), `ForecastViewModelTest` (5). Manual v1 ✅ (2026-07-21); el re-estilo V2 + pestañas de fachada se verifican en §8-B1. |
| CP-03 | RF-05, RF-06 | PASA + MANUAL (§8-B) | Auto: `ValidateSecretCodeUseCaseTest` (10) + guardianes. Visual V2 en §8-B1/B2. |
| CP-04 | RF-07 | PASA + MANUAL (P-29) | Auto: `CreateAccountViewModelTest` (10), `CreateAccountUseCaseTest` (8), `FirebaseAuthRepositoryTest` (15). Manual P-29 **bloqueante** (§8-A3). |
| CP-05 | RF-08, RF-09 | PASA + MANUAL ✅/parcial | Auto: como en v0.2.0 (§4 histórico del tag). P-53 ✅; **resta** Device Explorer (§8-A2). |
| CP-06 | RF-10, RF-11 | PASA + MANUAL ✅ | Auto: `TutorialViewModelTest` (ampliada V2), `TutorialUiStateTest` (3). P-34/P-53 ✅; visual V2 en §8-B2. |
| CP-07 | RF-12, RF-13, RF-14 | PASA + MANUAL (P-11, P-14) | Auto: `ValidateSecretCodeUseCaseTest` (10), `ConstantTimeTest` (4). P-11/P-14 **bloqueantes** (§8-A8) + indistinguibilidad visual V2 (§8-B1). |
| CP-08 | RF-15 | PASA + MANUAL (P-29) | Auto: `SignInUseCaseTest` (13), `LoginViewModelTest` (9). Manual P-29 **bloqueante** (§8-A3). |
| CP-09 | RF-16 | PASA + MANUAL (P-27) | Auto: `SessionManagerTest` (20), `SecureFlagLedgerTest` (5). P-27 **bloqueante** (§8-A9); nube V2 y pestañas en §8-B2. |
| CP-10 | RF-17, RF-18 | PASA + MANUAL ✅ | Auto: `ConsentViewModelTest` (2). P-53 ✅; visual V2 (P04) en §8-B2. |
| CP-11 | RF-19 | PASA + MANUAL ✅ | Auto: como v0.2.0. P-53 ✅; visual V2 (P21) en §8-B5. |
| CP-12 | RF-20 | PASA + MANUAL ✅ | Auto: como v0.2.0 (9+7+…). P-54 E2E ✅; doble confirmación re-estilada en §8-B5. |
| CP-13 | RF-21, RF-22 | PASA + MANUAL (§8-B) | Auto: `TopicsViewModelTest` (3), `TopicDetailViewModelTest` (4), `RoutesViewModelTest` (3), `FirestoreContentRepositoryTest` (13). Contenido real ✅ (P-50/P-57); portada de Guía + iconos/matices por tipo (D2) se verifican en §8-B3. |
| CP-14 | RF-23 | PASA + MANUAL (P-26) | Auto: `AgencyDetailViewModelTest` (ampliada V2), `LinesViewModelTest` (3). P-26 **bloqueante** (§8-A9); visual en §8-B3. |
| CP-15 | RF-24, RF-25 | PASA + MANUAL ✅ | Auto: parsing tolerante (7+11). P-50 E2E ✅ (2026-07-17, §7). |
| CP-16 | RF-26, RNF-08 | MANUAL ✅ + §8-B3 | P-50 ✅; el indicador «Puedes leerlo sin conexión» de la portada V2 se verifica en §8-B3. |
| CP-17 | RF-27, RF-28 | PASA + MANUAL (P-43) | Auto: `GuidanceViewModelTest` (18, instrumento v2). P-43 **bloqueante** (§8-A4); visual V2 en §8-B4. |
| CP-18 | RF-29, RF-30 | PASA + MANUAL (P-43) | Auto: `CalculateGuidanceLevelUseCaseTest` (19). P-43 **bloqueante** (§8-A4) — incluye el criterio «aviso sin desplazar» sobre la pantalla V2 (§8-B4). |
| CP-19 | RF-31 | NO APLICA | «Terminar» siempre descarta (P-41); guardado opcional = trabajo futuro. |
| CP-20 | RF-32 | PASA + MANUAL (P-46) | Auto: `JournalEditorViewModelTest` (23) y compañía. P-46 **bloqueante** (§8-A5); chips de tipo V2 en §8-B4. |
| CP-21 | RF-33 | PASA + MANUAL (P-59) | Auto: como v0.2.0 (17+6+…). P-59 **bloqueante** (§8-A6). |
| CP-22 | RF-34, RF-35, RF-36 | PASA + MANUAL (P-46) | Auto: como v0.2.0. P-46 **bloqueante** (§8-A5); `SecureSheet` («Conservar» como acción principal) en §8-B4. |
| CP-23 | RF-37, RF-38 | PASA + MANUAL ✅ | Auto: `FirestoreJournalSyncRepositoryTest` (13). P-49 ✅; chip «Todo guardado» (derivado local, sin lógica nueva) en §8-B4. |
| CP-24 | RF-39 | PASA + MANUAL (P-61) | Auto: como v0.2.0 (13+8+…). P-61 **bloqueante** (§8-A7). |
| CP-25 | RF-40 | MANUAL ✅ + PASA | Acceso admin ✅ (2026-07-17). P22 V2 (mensaje genérico de rechazo) en §8-B6. |
| CP-26 | RF-41, RF-42, RF-44 | MANUAL ✅ + PASA | E2E ✅ (2026-07-17). Vitest **71/71**. Re-estilo P23/P24 en §8-B6. |
| CP-27 | RF-43 | MANUAL ✅ + PASA | E2E ✅ + `coherence.test.ts` (25). Re-estilo P25 en §8-B6. |
| CP-28 | RNF-01 | PASA + MANUAL parcial | Auto: cifrado como v0.2.0. Device Explorer integrado en §8-A2/A5/A6. |
| CP-29 | RNF-02 | PASA + MANUAL (§8-A/B) | Auto: `SecureFlagLedgerTest` (5). Captura bloqueada se re-verifica sobre las pantallas V2 (§8-B2..B5) y recientes (§8-A8). |
| CP-30 | RNF-03 | PASA + MANUAL ✅ | Auto: `SessionManagerTest` (20), `InteractionThrottleTest` (4). «Teclear >5 min» dentro de §8-A5. |
| CP-31 | RNF-04 | PASA + MANUAL parcial | Auto: `NoTracesGuardTest` (7), `ManifestMinimizationTest` (6) + §3. `/data/data` junto a CP-28 (§8-A). |
| CP-32 | RNF-05 | PASA + MANUAL (§8-B) | Auto: `CamouflageResourcesTest` + DEX §2. La lista visual de camuflaje se recorre sobre la fachada V2 (§8-B1). |
| CP-33 | RNF-06 | PASA ✅ | Reglas sin cambios en la V2; Playground 10/10 ✅ (2026-07-15/17). |
| CP-34 | RNF-07 | MANUAL (§8-C) | Dispositivo/emulador de gama media: arranque ≤ 3 s, interacción ≤ 2 s — **incluido en la pasada única** (§8-C1). |
| CP-35 | RNF-09 | NO APLICA | Evaluación heurística formal de usabilidad (posterior). |
| CP-36 | RNF-10 | MANUAL (§8-C) | API 26 — **incluido en la pasada única** (§8-C2): la V2 introdujo componentes Compose nuevos que deben verse correctos en minSdk. |
| CP-37 | RNF-11 | PASA | Pasada final de `revisora-arquitectura` sobre el diff completo V2 (§9). |

### Cobertura resumida (v0.3.0)

Idéntica a v0.2.0 en lo funcional (**42 de 44 RF** con cobertura automatizada;
RF-31 y RF-38 formal diferidos): la V2 no movió la aguja funcional, que era el
objetivo. Lo que agrega es la **verificación visual** consolidada en §8-B.

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

> Nota V2: estas pasadas validaron **comportamiento** y siguen contando (la
> lógica no cambió). Su componente **visual** quedó desactualizado por el
> rediseño y se recorre una única vez en §8-B.

## 6. Fase B2 — Panel administrativo (histórico, 2026-07-17)

Alcance: RF-40 a RF-44, commits `3827b83`, `c413523`, `555a602`, `98f1f88`,
`3e6f863` y `73a29da`. El panel produce EXACTAMENTE el esquema que la app
consume (espejo de los DTO de `content/data` y `questionnaire/data`) y la app
solo ve lo publicado: los borradores viven en `/drafts`, exclusiva del claim
admin; regla desplegada y verificada en el Playground el 2026-07-17
(`/users/**` intacto, RNF-06). Corrida automatizada de la fase: vitest 66/66
(hoy 71/71 con el instrumento v2), lint y build en verde. Corrida manual E2E de
la usuaria (2026-07-17, proyecto real + emulador): publicación reflejada,
borradores invisibles, bloqueos de integridad referencial y de coherencia del
cuestionario, versión automática y compuerta `[POR CONFIRMAR]` — todo en verde.

## 7. Fase B3 — Cierre del ciclo de contenido en la app (histórico, 2026-07-17)

Alcance: RF-24, RF-25 y RF-44 (lado app), commits `ef1f925`, `ec9355d`,
`14b62d0` y `054b1ce`. Cambio único de producción: el parsing snapshot→DTO pasó
a casts seguros sobre el mapa crudo (`toXxxDto`) — un documento corrupto se
omite (content) o se descarta rigiendo el válido de mayor versión
(questionnaire), sin tumbar el fetch (RF-25). Corrida manual E2E ✅ (P-50,
2026-07-17). Veredictos de agentes: tres APROBADO. *(Nota v0.2.0: los helpers
de parsing se extrajeron a `core/network/FirestoreDocumentParsing.kt`.)*

## 8. Corridas manuales BLOQUEANTES del tag `v0.3.0-trl4` — LISTA ÚNICA Y DEFINITIVA

Todo lo manual que resta está aquí y solo aquí: las funcionales diferidas de
v0.2.0 (bloque A), las visuales de la V2 contra mockups (bloque B) y las de
plataforma (bloque C). Emulador `Medium_Phone_API_35`, instalación limpia,
proyecto real `aurora-31c37`, salvo donde se indique otro. Los pasos numerados
completos de cada P-xx están en su entrada de `docs/PENDIENTES.md`; aquí el
resumen ejecutable con el resultado esperado. Orden pensado para minimizar
reinstalaciones: A1→A9 encadenan sobre la misma instalación (con las
excepciones indicadas), B se recorre durante A aprovechando cada pantalla
visitada, C al final en sus emuladores propios.

### Bloque A — Funcionales diferidas de v0.2.0 (ahora sobre la UI V2)

| # | Qué correr | Pasos (resumen) | Resultado esperado |
|---|---|---|---|
| A1 | `./gradlew connectedDebugAndroidTest` | Emulador arriba, correr las 8 suites instrumentadas (~29 tests) | 0 fallos. Cubre Keystore, SQLCipher, cifrado de payload/media, foco de la barra |
| A2 | **P-28 final** (CP-05/28/31) | Con la cuenta creada: Device Explorer → `shared_prefs/search_index.xml` y `store_meta.xml` | Solo Base64 bajo claves neutras; ningún rastro de la clave personal |
| A3 | **P-29** (CP-04/08) | Login con Firebase real: contraseña mala / correo inexistente / correo malformado; 5 fallos seguidos; forzar detención con la pausa activa | Mensaje ÚNICO «Los datos no coinciden…» en los tres casos; «Pausa temporal» desde 5:00 con botón deshabilitado; la cuenta regresiva se retoma tras reinicio; expirada, acceso normal |
| A4 | **P-43** (CP-17/18) | Cuestionario v2: responder 2 y retroceder; «Abandonar»; nube a mitad; `adb shell am kill`; completar en 360×800 (todo «Con frecuencia»); «Ver rutas»; captura en portada/pregunta/resultado | Respuesta marcada al volver; portada vacía tras abandonar/nube/kill (sin rastro); **aviso RF-30 visible SIN scroll bajo la tarjeta del nivel** incluso con el nivel más alto; atrás desde rutas regresa al resultado; captura bloqueada |
| A5 | **P-46** (CP-20/22 completo) | Registros: guardar vacío; tipo sin texto; fecha con el selector; segundo registro con fecha anterior; detalle; teclear >5 min; nube a mitad; edición prellenada (verificar mismo doc en consola); edición en modo avión; eliminación con doble confirmación («Conservar» primero), también en modo avión | Errores inline sin borrar lo tecleado; orden cronológico inverso; FLAG_SECURE en el selector; la sesión NO expira tecleando; el borrador no persiste tras la nube; el doc remoto se sobreescribe (mismo id, `payload`/`iv` nuevos); el `delete` se propaga al reconectar |
| A6 | **P-59** (CP-21) | Evidencias: imagen de galería (sin diálogo de permisos); cámara del sistema (verificar DCIM vacío y regreso directo al formulario; dejarla abierta >5 min); PDF en visor interno; tipo vedado; cerrar sesión y reabrir; Device Explorer `files/media_store/`; eliminar registro con adjuntos; eliminar cuenta | Sin permisos pedidos; sin copia en galería/DCIM; ida-y-vuelta sin pasar por la fachada (y CON fachada si expira la gracia); captura bloqueada en el visor; adjuntos persisten; solo blobs de 32 hex ilegibles; cascadas RF-36/RF-20 completas |
| A7 | **P-61** (CP-24, RF-39) | DOS instalaciones + Firebase real: A crea 3 registros (+1 adjunto) y sube; B restaura tras login + código nuevo; edición cruzada; borrado remoto en cascada; contraseña restablecida por correo → emulador limpio; volver a A; cambio de contraseña | B restaura ÍNTEGRO (contenido, orden y adjunto); mismo docId; tras el reset, B queda vacío + aviso de una sola vez y `g` incrementada; A re-sube solo su historial; el cambio de contraseña NO re-escribe `entries` (solo `r` del sobre) |
| A8 | **P-14 + P-11** (CP-07) | Teclear el código a medias y pulsar Home (mirar la miniatura de recientes); teclear la clave completa varias veces y revisar las sugerencias de Gboard (barra de fachada Y barra de práctica del tutorial) | La miniatura NO muestra el código a medio teclear; Gboard NO sugiere la clave después |
| A9 | **P-26 + P-27** (CP-09/14) | Desde una entidad: «Llamar» → marcador → atrás; mirar recientes del marcador | Se aterriza en la FACHADA (pop en ON_STOP); recientes no muestra la pantalla de líneas; anotar si el número queda visible en el marcador (si sí: ampliar `content_agency_call_note`) y documentar el costo UX del reingreso |

### Bloque B — Verificación visual de la UI V2 contra mockups (pantalla por pantalla)

Fuente de fidelidad: `docs/mockups-v2/v2-NN.png` (tú eres el criterio — Fase 4
del plan). Regla transversal en TODA pantalla interna: nube violeta de salida
alcanzable con el pulgar, captura bloqueada, Marcellus solo en el título/saludo,
un solo botón lleno (lavanda) por pantalla, iconos de contorno de la librería.
Donde el mockup y la implementación difieren por camuflaje (P-17/P-25/P-41/
P-45/P-52) o por decisión (D1–D8, decisiones S3), **manda la implementación**
ya auditada — no es hallazgo.

| # | Pantallas | Mockup | Qué comparar | Resultado esperado |
|---|---|---|---|---|
| B1 | **Fachada P01–P03 + camuflaje** | v2-01, v2-02, v2-03 | Clima principal con pestañas Hoy · 5 días · Lugares; búsqueda con recientes; 5 días con barra de temperatura. Camuflaje: tipografía NEUTRA (sin Marcellus ni marca squircle), solo iconos de clima. Repetir la lista visual de camuflaje: ícono/nombre/splash, miniatura de recientes de la FACHADA visible y normal, capturas permitidas en la fachada. **RF-13 visual**: teclear una ciudad inexistente y un código incorrecto — el estado «sin resultados» debe ser INDISTINGUIBLE (mismo texto, mismo aspecto, mismos tiempos) | Fiel a mockup con léxico de camuflaje; nada delata el doble fondo; «Lugares» abre la misma P02 (D4) |
| B2 | **Acceso P04–P09 + pestañas** | v2-05..v2-10 | Consentimiento (3 tarjetas con icono, botón inactivo hasta el checkbox); crear cuenta «paso 1 de 3»; clave con reglas que cambian de icono al cumplirse; tutorial de tres momentos con práctica; login «Hola de nuevo.»; Inicio con saludo Marcellus, tarjeta del cuestionario SIN conteo (D8), accesos rápidos Llamar · Guardar evidencia · Informarme, engranaje de Ajustes (D3), pestañas Inicio · Guía · Mi registro. **Regresión de pestañas**: cambiar de pestaña varias veces → Home → reabrir: fachada y reingreso pide el código; atrás desde cualquier pestaña NO sale del grafo seguro a medias | Fiel a mockups; pestañas íntegras dentro del espacio seguro; la nube visible en las tres pestañas (sin quedar tapada por la barra) |
| B3 | **Guía: portada + P10–P14** | v2-11..v2-16 | Portada de tres secciones + «Puedes leerlo sin conexión» (probar en modo avión); tipos con icono/matiz FIJOS (mapeo S3: t01 azul-figura, t02 lila-lóbulos, t03 rosa-círculo, t04 arena-tarjeta; un id no mapeado caería al neutro); detalle con señales de vistos; rutas por sector (verde salud · azul protección · arena justicia); entidad con aviso rosa de llamada; líneas con número grande | Fiel a mockups; los colores respetan el significado fijo (regla 1); contenido real de producción (4 tipos, no los 5 del mockup — dato, no UI) |
| B4 | **Reconocer y registrar P15–P20** | v2-17..v2-22 | Intro con 3 garantías y «Ahora no»; pregunta única con progreso por segmentos y «Salir» visible; resultado (además del criterio funcional A4); Mi registro con chip «Todo guardado» y entradas con tipo/fecha/adjuntos; anotar con tipo en chips de color y adjuntos; detalle + **hoja inferior de doble confirmación con «Conservar» como acción principal** (SecureSheet — captura bloqueada también ahí) | Fiel a mockups con las adaptaciones registradas (P-41/P-45: 12 preguntas, «Terminar» descarta, léxico «adjuntos») |
| B5 | **Ajustes P21 + flujos sin mockup** | v2-23 | Tres secciones (llave de entrada / privacidad / cuenta) con «Eliminar todo» al final en ciruela; pie «Prototipo académico»; «Cambiar contraseña» presente (D6). Flujos por extensión del sistema (D6): cambiar código, cambiar contraseña, eliminar cuenta (dos confirmaciones re-estiladas), privacidad, visor de adjuntos, tutorial en repaso | Coherentes con el sistema (tokens/componentes V2), sin elementos inventados |
| B6 | **Panel web P22–P25 + P23·b** | v2-24..v2-28 | En `admin/` (`npm run dev` o build desplegado): login con marca Aurora y rechazo como aviso visible genérico; contenido con iconos por tipo y chip de estado; rutas/líneas con aviso de integridad al eliminar; cuestionario con publicar bloqueado y motivo; editor con checklist y vista previa de la usuaria (SIN selector de icono/color — diferido por D2) | Fiel a mockups a densidad de escritorio; validaciones y flujos funcionales idénticos a B2/B3 (ya verificados E2E) |
| B7 | **Fuente grande (P-64)** | — | Ajustes del sistema → tamaño de fuente al máximo (fontScale 2.0) → recorrer las tres pestañas internas y el cuestionario | Ningún texto cortado; la nube de salida NO queda solapada con la barra de pestañas (si la barra crece y el hueco desaparece: medirla con `onSizeChanged`, ver P-64) |

### Bloque C — Plataforma (recomendadas antes del tag, no encadenadas)

| # | Qué correr | Pasos | Resultado esperado |
|---|---|---|---|
| C1 | **CP-34 (RNF-07) — gama media** | Emulador perfil gama media (2 GB RAM, ej. Pixel 2 API 30) o dispositivo real equivalente; instalar el RELEASE; cronometrar arranque en frío y navegación fachada→código→pestañas | Arranque ≤ 3 s; respuesta a interacción ≤ 2 s; sin jank notorio en las listas V2 |
| C2 | **CP-36 (RNF-10) — API 26** | Emulador API 26 (minSdk); instalar debug y release; recorrer fachada, activación, pestañas, guía, cuestionario, registros | Sin crashes ni degradación visual de los componentes V2 (formas, fuentes, gradientes); FLAG_SECURE operativo |

## 9. Pasada final de agentes (v0.3.0 · diff completo `v0.2.0-rc1..ui-v2`)

Ejecutada el 2026-08-12 sobre el diff íntegro (148 archivos, +6498/−3023, app +
admin/), cada agente con su encargo específico:

- **revisora-arquitectura: APROBADO.** Clasificó los ~170 archivos del diff:
  UI app (47 .kt en `*/ui/`, `core/ui/v2` y nivel raíz de composición), 40
  drawables + strings neutros, 4 tests (solo adiciones), panel (17, solo
  estilos/plantillas/assets locales) y docs. **Cero cambios** en `domain/`,
  `data/`, `di/`, manifest, `gradle/`, `firestore.rules` y `admin/package.json`
  — cero dependencias nuevas. Los dos toques a `core/security` son apariencia
  con mecanismo intacto (nube inyectada con el mismo `quickExit`, inset de
  anclaje, `SecureSheet` con la misma `SecureFlagPolicy.SecureOn`).
  `InnerTabsHost` en el paquete raíz es nivel de composición (mismo estatus que
  `AuroraApp`), sin imports entre features. Guarda visual con lista legada
  VACÍA + grep independiente limpio. Única mejora (M-1, documental): registrar
  `SecureSheet`/`cloudBottomInset` con fecha — **resuelta** con el ADR
  2026-08-12 de esta sesión.
- **auditora-seguridad: APROBADO PARA MERGE.** (1) Fachada V2: `FacadeTypeVoice`
  100 % `FontFamily.Default`, sin Marcellus/marca/squircle en `weather/`; los
  guardianes cubren TAMBIÉN los 46 drawables nuevos (barren contenido y nombre
  de archivo). (2) FLAG_SECURE: `secureGraph` único punto de bandera;
  `InnerTabsHost` se instancia SOLO dentro de `screen(PANEL_ROUTE)` — las tres
  pestañas comparten una entrada del grafo seguro; `NoTracesGuardTest` veda
  ventanas propias fuera de `core/security` y verifica que el NavHost raíz solo
  registre rutas de fachada. (3) Backstack: `SecureBackStackGuard`/`SessionGuard`
  sin cambios; la pestaña seleccionada es `rememberSaveable` ligado a la entrada
  `panel` — el pop la destruye y el reingreso siempre cae en Inicio, sin rastro.
  (4) RF-13: una sola rama `showNoResults` → un único `NoResultsContent`;
  `SearchQueryGate` sin cambios. (5) Cero logs/telemetría/dependencias nuevos.
  (6) Drawables con nombres de objeto neutros. Residuales nuevos (no
  bloqueantes) registrados como **P-65** (vocabulario semántico V2 legible en
  DEX debug; release limpio), **P-66** (extender el barrido de camuflaje a
  `src/debug` y renombrar el seeder) y **P-67** (fijar con test el contrato
  «reingreso → pestaña Inicio») en `PENDIENTES.md`.
- **verificadora-rf: APROBADO PARA MERGE.** Los seis criterios con componente
  visual, CUMPLIDOS en código y tests: RF-13 (un solo composable, gate en
  paralelo esperando SIEMPRE a la búsqueda real, 3 tests de regresión); RF-16
  criterio 1 (nube compuesta ENCIMA del contenido en las 22 rutas `screen()`;
  el inset solo desplaza el anclaje); RF-30 (el aviso vive en `LevelCard`,
  zona fija ANTES del área con `verticalScroll` — invisible-sin-scroll es
  imposible por construcción); RF-36/RF-20 (doble confirmación intacta:
  `DeleteConfirmSheet` sobre `SecureSheet` con los mismos strings;
  `DeleteAccountScreen`/ViewModel con diff VACÍO en toda la rama); tests de
  ViewModel con 149 inserciones / 0 borrados (solo presentación); corrida
  puntual de 9 suites de respaldo: **94/94 en verde**.

Veredicto conjunto: **el diff `v0.2.0-rc1..ui-v2` queda LISTO PARA MERGE a
`main`, condicionado únicamente a las corridas manuales de la §8 para el tag
`v0.3.0-trl4`.**

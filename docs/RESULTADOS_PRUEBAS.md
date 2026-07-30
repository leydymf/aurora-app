# Aurora — Resultados de pruebas (cierre v0.2.0-trl4)

> Evidencia para el informe. Corrida del **2026-07-30** sobre el árbol completo
> (app + panel), previa al tag `v0.2.0-trl4`. Alcance: todos los RF implementados
> desde `v0.1.0-trl4` (RF-04, 09, 11, 19, 20, 24, 25, 33, 35, 36, 37 pleno, 39,
> 40–44 y el cuestionario v2) más la regresión de los anteriores.
> La línea base anterior (`v0.1.0-trl4`, 2026-07-15) se conserva como referencia
> de comparación en §1 y en las secciones históricas §6/§7.

## 1. Resumen de la corrida automatizada (v0.2.0)

Comandos: `./gradlew test ktlintCheck lint assembleDebug assembleRelease` →
**BUILD SUCCESSFUL**; en `admin/`: `npm run test`, `npm run lint`, `npm run build`
→ todo en verde.

| Métrica | v0.1.0 (2026-07-15) | v0.2.0 (2026-07-30) |
|---|---|---|
| Pruebas unitarias JVM | 260 por variante (43 clases) | **522 por variante — debug y release, 1044 ejecuciones, 0 fallos, 0 errores, 0 omitidas (74 clases)** |
| ktlint | Sin violaciones | **Sin violaciones** |
| Lint | 0 errores · 51 warnings | **0 errores · 56 warnings** (comparación abajo) |
| `assembleDebug` / `assembleRelease` (R8 + shrink) | OK | **OK** (§2) |
| Panel `admin/` — vitest | 66/66 (B2) | **71/71** |
| Panel `admin/` — eslint + prettier / build | en verde | **en verde** (solo el aviso preexistente de tamaño de chunk de Vite) |

**Comparación de warnings de lint contra la línea base** (todos informativos;
cero warnings nuevos sin justificar):

| Regla | v0.1.0 | v0.2.0 | Nota |
|---|---|---|---|
| `GradleDependency` | 42 | 45 | Versiones más nuevas disponibles; informativo (política A2-1: no subir dependencias sin justificación) |
| `AndroidGradlePluginVersion` | 3 | 3 | Igual |
| `Typos` («momento») / `RedundantLabel` / `PluralsCandidate` / `OldTargetApi` | 5 | 5 | Iguales, falsos positivos conocidos |
| `UnusedResources` | 1 | 1 | `color.night` preexistente; un segundo hallazgo (`journal_media_section`, resto de RF-33) se **eliminó** en esta sesión |
| `ApplySharedPref` | 0 | 2 | **Nuevos y deliberados** (RF-20): los `erase()` de `DatabaseKeyManager` y `LocalDekStore` usan `commit()` síncrono — el cripto-borrado debe aterrizar en disco antes de continuar; `apply()` podría perderse si el proceso muere |
| `ModifierParameter` | 0 | 0 | Apareció en RF-39 (`JournalListScreen`) y se **corrigió** en esta sesión |
| **Total** | **51** | **56** | |

**Pruebas instrumentadas** (emulador; no entran en `./gradlew test`): 8 clases /
~29 `@Test`. Verificadas en emulador API 35 en sus sesiones de origen:
`KeystoreSecretCodeHasherTest` (5), `KeystorePayloadCipherTest` (5/5) y la
cabecera SQLCipher (`EncryptedDatabaseFactoryTest`, P-03/P-49). Las nuevas de
RF-33/RF-39 (`KeystoreMediaCipherTest`, `LocalDekStoreTest`,
`ImageImporterRejectionTest`) y `WeatherSearchBarFocusTest` se re-ejecutan con
`./gradlew connectedDebugAndroidTest` en la pasada bloqueante de emulador (§8).

## 2. P-05 repetido — Ofuscación R8 del release (RNF-05), incluye RF-33/RF-39

`assembleRelease` con `isMinifyEnabled = true` + `isShrinkResources = true`
(sin cambios de configuración desde v0.1.0). Verificación sobre
`app-release.apk` con `apkanalyzer dex packages --defined-only` (2026-07-30):

| Nombre buscado en clases propias (`com.auroraclima`) | Coincidencias |
|---|---|
| Set v0.1.0: `SessionManager`, `SecretCode*`, `SearchQueryGate`, `QuickExit*`, `SensitiveState*`, `Questionnaire*`, `Guidance*`, `Secure*`, `Consent*`, `SignIn*`, paquetes `access`/`core.security` | **0** |
| **Nuevos RF-39 (sobres de llaves)**: `Dek*` (`DekStore`, `LocalDekStore`, `DekPayloadCipher`, `DekMediaCipher`), `*Envelope*` (`KeyEnvelopeRemoteSource`), `PasswordKeyWrapper`, `Pbkdf*`, `*Derivation`, `SyncKeyManager`, `JournalSyncReconciler` | **0** |
| **Nuevos RF-33 (evidencias)**: `*Media*` (`JournalMedia*`, `MediaCipher`, `MediaKeyEraser`), `Attachment*` (`AttachmentStore`, `AttachmentDao`), `ImageImporter`, `CaptureBuffer` | **0** |
| **Nuevos RF-20**: `*Erasure*`/`*Eraser*` (`AccountDataErasure`, `PendingErasure*`, `JournalStoreEraser`…) | **0** |

Total de líneas propias en el DEX: 85, todas pertenecientes a las **mismas
únicas clases legibles de v0.1.0** (ninguna nueva): `AuroraApplication` y
`MainActivity` (obligadas por el manifest / identidad de clima) y
`JournalDatabase` / `WeatherDatabase` (+ `_Impl`) por el constraint de reflexión
de Room («journal» es término genérico; residual hermano de P-44). Todo lo demás
renombrado a `a`,`b`,`c`…

**Estado P-05 (v0.2.0): VERIFICADO — sin regresión y sin símbolos nuevos legibles.**

## 3. P-06 repetido — Manifest fusionado del release (RNF-04)

`apkanalyzer manifest permissions app-release.apk` (2026-07-30):

| Permiso | Cambio vs. v0.1.0 |
|---|---|
| `android.permission.INTERNET` | Igual (clima + Firebase) |
| `android.permission.ACCESS_NETWORK_STATE` | Igual (Firestore offline) |
| `com.auroraclima.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Igual (firma, AndroidX) |
| `com.google.android.providers.gsf.permission.READ_GSERVICES` | Igual (GMS) |

**Confirmado explícitamente: NINGÚN permiso nuevo pese a RF-33.** El Photo
Picker del sistema, la cámara del sistema (`ACTION_IMAGE_CAPTURE` vía intent) y
el SAF (`ACTION_OPEN_DOCUMENT`) no requieren `CAMERA`, `READ_MEDIA_IMAGES`,
`READ_EXTERNAL_STORAGE` ni ningún otro permiso — esa fue precisamente la
política de las tres vías (D0 §5.1). Sin permisos peligrosos; sin `CALL_PHONE`.

Único elemento nuevo en el manifest fusionado: el `androidx.core.content.FileProvider`
(**no exportado**, `grantUriPermissions` puntual) que entrega a la cámara del
sistema el content-URI del JPEG temporal de captura — componente genérico de
AndroidX presente en miles de apps; no delata nada.

Componentes **exportados**: idénticos a v0.1.0 — `MainActivity` (propio,
launcher) + `GenericIdpActivity`, `RecaptchaActivity`, `RevocationBoundService`,
`ProfileInstallReceiver` (inyectados por Firebase Auth/GMS/profileinstaller,
genéricos de infraestructura). Residual aceptado, sin cambios.

**Estado P-06 (v0.2.0): VERIFICADO — manifest sin cambios de superficie.**

## 4. Matriz de casos de prueba (CP) — corrida v0.2.0

Leyenda: **PASA** — cubierto por prueba automatizada (se cita la clase y su nº
de `@Test`); **MANUAL** — requiere emulador (✅ si la usuaria ya la corrió; si
no, es **bloqueante del tag**, pasos en §8); **NO APLICA** — fuera del alcance
TRL 4 (trabajo futuro).

| CP | RF/RNF | Estado | Evidencia automatizada / manual |
|---|---|---|---|
| CP-01 | RF-01 | PASA + MANUAL ✅ | Auto: `CamouflageResourcesTest` (2), `ManifestMinimizationTest` (6), `NoTracesGuardTest` (7). Manual: verificado en cada pasada de emulador desde v0.1.0 (ícono, nombre, splash 100% clima). |
| CP-02 | RF-02, RF-03, RF-04 | PASA + MANUAL ✅ | Auto: `WeatherViewModelTest` (16), `WeatherRepositoryImplTest` (12), `ForecastMapperTest` (4), `ForecastViewModelTest` (5), `WeatherCardMapperTest` (2), `WeatherConditionTest` (2). **RF-04 (pronóstico 5 días) implementado 2026-07-21**; CP-02 manual ✅ (usuaria, 2026-07-21). |
| CP-03 | RF-05, RF-06 | PASA + MANUAL ✅ | Auto: `ValidateSecretCodeUseCaseTest` (10), `CamouflageResourcesTest`, `NoTracesGuardTest`. Manual: fachada explorada sin indicios en las pasadas previas. |
| CP-04 | RF-07 | PASA + MANUAL (P-29) | Auto: `CreateAccountViewModelTest` (10), `CreateAccountUseCaseTest` (8 — incluye publicación del sobre RF-39), `FirebaseAuthRepositoryTest` (15). Manual P-29 **bloqueante** (§8). |
| CP-05 | RF-08, RF-09 | PASA + MANUAL ✅/parcial | Auto RF-08: `CreateSecretCodeUseCaseTest` (9), `CodeSetupViewModelTest` (6), `LocalSecretCodeRepositoryTest` (5), `CityNameSetTest` (5), `KeystoreSecretCodeHasherTest` (instr. 5). **RF-09 (cambio de código, C1)**: `ChangeSecretCodeUseCaseTest` (5), `ChangeCodeViewModelTest` (4); manual ✅ P-53 (2026-07-17). P-28 parcial ✅ (2026-07-11); **resta** Device Explorer (`shared_prefs/` solo Base64) — bloqueante (§8). |
| CP-06 | RF-10, RF-11 | PASA + MANUAL ✅ | Auto: `TutorialViewModelTest` (7), `TutorialUiStateTest` (3). Manual P-34 ✅ (2026-07-11); **RF-11 (reabrir tutorial, C1)** manual ✅ P-53 (2026-07-17). |
| CP-07 | RF-12, RF-13, RF-14 | PASA + MANUAL (P-11, P-14) | Auto: `ValidateSecretCodeUseCaseTest` (10), `ConstantTimeTest` (4). Manuales P-11 (Gboard no aprende la clave) y P-14 (miniatura de recientes) **bloqueantes** (§8). |
| CP-08 | RF-15 | PASA + MANUAL (P-29) | Auto: `SignInUseCaseTest` (13 — incluye restauración RF-39), `LoginViewModelTest` (9), `FirebaseAuthRepositoryTest` (15). Manual P-29 **bloqueante** (§8). |
| CP-09 | RF-16 | PASA + MANUAL (P-27) | Auto: `SessionManagerTest` (20 — incluye la ida-y-vuelta de RF-33), `SecureFlagLedgerTest` (5), `SignOutSessionCleanupTest` (2), `AuthSessionCleanerTest` (2). Manual P-27 (llamar→marcador→atrás) **bloqueante** (§8). |
| CP-10 | RF-17, RF-18 | PASA + MANUAL ✅ | Auto: `ConsentViewModelTest` (2). Relectura desde privacidad (P-16, C1) manual ✅ P-53 (2026-07-17). |
| CP-11 | RF-19 | PASA + MANUAL ✅ | **Implementado (C1)**: `SignOutSessionCleanupTest` (2), `AuthSessionCleanerTest` (2), convergencia en `SessionManager.quickExit()` (`SessionManagerTest`). Manual ✅ P-53 (2026-07-17). |
| CP-12 | RF-20 | PASA + MANUAL ✅ | **Implementado (C2)**: `DeleteAccountUseCaseTest` (9), `DeleteAccountViewModelTest` (7), `DatabaseKeyManagerEraseTest`, `PrefsPendingErasureFlagTest`, `PendingErasureRecoveryTest` (3), `JournalRemoteEraserTest` (2), `JournalMediaEraserTest`, `AccessCredentialsEraserTest`, `LocalDekStoreTest` (instr.). Manual E2E ✅ P-54 (2026-07-20, Firebase real). |
| CP-13 | RF-21, RF-22 | PASA + MANUAL ✅ | Auto: `TopicsViewModelTest` (3), `TopicDetailViewModelTest` (4), `RoutesViewModelTest` (3), `FirestoreContentRepositoryTest` (13). Contenido real sembrado y navegado (P-57 siembra 2026-07-22 + regresión P-50 ✅). |
| CP-14 | RF-23 | PASA + MANUAL (P-26) | Auto: `AgencyDetailViewModelTest` (4), `LinesViewModelTest` (3). Manual P-26 (número en recientes del marcador) **bloqueante** (§8). |
| CP-15 | RF-24, RF-25 | PASA + MANUAL ✅ | Auto: `ContentDocumentParsingTest` (7), `QuestionnaireDocumentParsingTest` (11), casos de tolerancia en ambos repositorios. Manual E2E ✅ P-50 (2026-07-17, §7). |
| CP-16 | RF-26, RNF-08 | MANUAL ✅ | Verificado dentro de P-50 ✅ (2026-07-17): contenido y cuestionario completos en modo avión, sin error. |
| CP-17 | RF-27, RF-28 | PASA + MANUAL (P-43) | Auto: `GuidanceViewModelTest` (18 — **instrumento v2**, 12 preguntas en dos bloques). Manual P-43 **bloqueante** (§8). |
| CP-18 | RF-29, RF-30 | PASA + MANUAL (P-43) | Auto: `CalculateGuidanceLevelUseCaseTest` (19 — motor v2: dos vías §5.4, garantía §5.5, banderas críticas, bordes 9/10·24/25·40/41·66), `GuidanceViewModelTest`. Manual P-43 **bloqueante** (§8). |
| CP-19 | RF-31 | NO APLICA | «Terminar» siempre descarta (P-41); guardado opcional = trabajo futuro. Descarte cubierto por CP-17. |
| CP-20 | RF-32 | PASA + MANUAL (P-46) | Auto: `JournalEditorViewModelTest` (23), `AddJournalEntryUseCaseTest` (9), `JournalDaoTest` (6), `LocalJournalRepositoryTest` (6). Manual P-46 **bloqueante** (§8). |
| CP-21 | RF-33 | PASA + MANUAL (P-59) | **Implementado (E1)**: `ImageImporterTest` (5 — limpieza EXIF/GPS), `AttachmentPolicyTest` (6 — lista blanca y tope), `LocalJournalAttachmentRepositoryTest` (17), `JournalMediaViewModelTest` (6), `JournalMediaEraserTest`, `JournalDatabaseMigrationTest` (3), `SessionManagerTest` (ida-y-vuelta), `KeystoreMediaCipherTest` (instr. 6), `ImageImporterRejectionTest` (instr. 2), `ManifestMinimizationTest` (sin permisos nuevos, §3). Manual P-59 **bloqueante** (§8). |
| CP-22 | RF-34, RF-35, RF-36 | PASA + MANUAL (P-46) | **RF-35/36 implementados (C3)**: `JournalListViewModelTest` (7), `JournalDetailViewModelTest` (11), `UpdateJournalEntryUseCaseTest` (3), `DeleteJournalEntryUseCaseTest` (4 — cascada de evidencias local y remota), `LocalJournalRepositoryTest` (6), `JournalDaoTest` (6). Manual P-46 (CP-22 completo, pasos 9-14) **bloqueante** (§8). |
| CP-23 | RF-37, RF-38 | PASA + MANUAL ✅ | Auto: `FirestoreJournalSyncRepositoryTest` (13 — payloads v2 de RF-39), `KeystorePayloadCipherTest` (instr. 5/5). Manual P-49 ✅ (2026-07-15). RF-38: cola durable probada en P-49; su formalización sigue como trabajo futuro. |
| CP-24 | RF-39 | PASA + MANUAL (P-61) | **Implementado (D2, llave envuelta + evidencias troceadas)**: `DefaultSyncKeyManagerTest` (13), `PasswordKeyWrapperTest` (8), `Pbkdf2PasswordKeyDerivationTest` (4), `DekPayloadCipherTest` (6), `DekMediaCipherTest` (7), `JournalSyncReconcilerTest` (9), `FirestoreJournalMediaSyncRepositoryTest` (10), `JournalDatabaseMigrationTest` (3), `ChangePasswordUseCaseTest` (7), `ChangePasswordViewModelTest` (4), `LocalDekStoreTest` (instr. 6). Manual P-61 (dos instalaciones + Firebase real) **bloqueante** (§8). |
| CP-25 | RF-40 | MANUAL ✅ + PASA | Acceso admin y caso negativo verificados (2026-07-17, B2/B3 §6-§7). |
| CP-26 | RF-41, RF-42, RF-44 | MANUAL ✅ + PASA | E2E panel→app ✅ (2026-07-17, §6). Auto panel: vitest **71/71** (`schemas.test.ts` 15, `topicSchema.test.ts` 10, `phone.test.ts` 8, `ids.test.ts` 5…). |
| CP-27 | RF-43 | MANUAL ✅ + PASA | E2E ✅ (2026-07-17, §6) + `coherence.test.ts` (25) y `formSchema.test.ts` (8) actualizados al **instrumento v2** (fixture = `forms.json` v2 real). |
| CP-28 | RNF-01 | PASA + MANUAL parcial | Auto: cabecera SQLCipher (`EncryptedDatabaseFactoryTest` instr. 3), `KeystorePayloadCipherTest` (5/5), `KeystoreMediaCipherTest` (6), `DekPayloadCipherTest`/`DekMediaCipherTest`. Manual: Device Explorer (BD, `shared_prefs/`, `media_store/`) **bloqueante** — integrado en P-28/P-46/P-59 (§8). |
| CP-29 | RNF-02 | PASA + MANUAL (P-43/P-14/P-59) | Auto: `SecureFlagLedgerTest` (5). Manual: captura bloqueada en cuestionario (P-43), registros/visor de adjuntos (P-59), recientes (P-14) — **bloqueantes** (§8). |
| CP-30 | RNF-03 | PASA + MANUAL ✅ | Auto: `SessionManagerTest` (20), `InteractionThrottleTest` (4). Inactividad verificada en pasadas previas; el caso «teclear >5 min» va dentro de P-46 (§8). |
| CP-31 | RNF-04 | PASA + MANUAL parcial | Auto: `NoTracesGuardTest` (7), `ManifestMinimizationTest` (6) + §3. Manual: auditoría de `/data/data` con Device Explorer (junto a CP-28, §8). |
| CP-32 | RNF-05 | PASA + MANUAL ✅ | Auto: `CamouflageResourcesTest` (2) + verificación DEX §2. Lista de camuflaje recorrida en las pasadas previas; sin cambios visibles nuevos (RF-33/39 usan léxico «adjuntos»). |
| CP-33 | RNF-06 | PASA ✅ | `firestore/rules.test.mjs` + Rules playground 10/10 ✅ (2026-07-15) + `/drafts` solo admin ✅ (2026-07-17). Las reglas no cambiaron con RF-39 (media/mediaparts viven bajo `/users/{uid}/`, cubiertas por la regla de dueña). |
| CP-34 | RNF-07 | MANUAL | Dispositivo de gama media: arranque ≤ 3 s, interacción ≤ 2 s. No bloqueante del tag (sin cambio de presupuesto de arranque en v0.2.0); se recomienda junto a §8. |
| CP-35 | RNF-09 | NO APLICA (Fase 4) | Evaluación heurística formal de usabilidad. |
| CP-36 | RNF-10 | MANUAL | API 26 + dispositivo reciente. `minSdk = 26` sin cambios; no bloqueante del tag. |
| CP-37 | RNF-11 | PASA | Pasada final v0.2.0 de `revisora-arquitectura` sobre todo el repo (§9). |

### Cobertura resumida (v0.2.0)

- **RF implementados y con cobertura automatizada**: RF-01..30, 32..37, 39,
  40..44 — **42 de 44** (todo el alcance TRL 4 ampliado).
- **RF diferidos (trabajo futuro)**: RF-31 (guardar el resultado del
  cuestionario) y RF-38 como RF formal (el mecanismo de cola durable existe y
  está probado; faltan sus criterios propios).
- **CP totalmente cerrados (auto + manual ✅)**: CP-01, 02, 03, 05 (salvo Device
  Explorer), 06, 10, 11, 12, 13, 15, 16, 23, 25, 26, 27, 33, 37.
- **CP bloqueados solo por corrida manual (§8)**: CP-04/08 (P-29), CP-07
  (P-11/P-14), CP-09 (P-27), CP-14 (P-26), CP-17/18 (P-43), CP-20/22 (P-46),
  CP-21 (P-59), CP-24 (P-61), CP-28/31 (Device Explorer).

## 5. Pasadas manuales ya ejecutadas por la usuaria (integradas)

| Referencia | Fecha | Resultado |
|---|---|---|
| P-34 (CP-06, RF-10) | 2026-07-11 | ✅ Tutorial con práctica real |
| P-28 parcial (CP-05, RF-08) | 2026-07-11 | ✅ Rechazos de clave en vivo; resta Device Explorer |
| P-49 (CP-23, RF-37) | 2026-07-15 | ✅ Sync cifrada offline→online; blobs ilegibles |
| P-04/CP-37 (CP-33, RNF-06) | 2026-07-15 | ✅ Rules playground 10/10 |
| CP-26/CP-27 (B2) + reglas `/drafts` | 2026-07-17 | ✅ E2E panel→app; `/drafts` solo admin |
| P-50 (CP-15/16/25 + regresión, B3) | 2026-07-17 | ✅ Ciclo completo panel→app, corrupto tolerado, modo avión |
| P-53 (CP-05/06/10/11 — Configuración C1: RF-09/11/17c2/19) | 2026-07-17 | ✅ Pasada completa en verde |
| P-54 (CP-12, RF-20) | 2026-07-20 | ✅ Eliminación E2E con Firebase real; Device Explorer sin rastros |
| CP-02 (RF-04, pronóstico) | 2026-07-21 | ✅ Pronóstico de 5 días en emulador |
| Siembra P-57 (contenido verificado) | 2026-07-22 | ✅ 4 topics / 5 lines / 3 routes / 24 agencies en producción |

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
(questionnaire), sin tumbar el fetch (RF-25); un decimal en campo numérico se
trata como corrupto (M-2). Suites nuevas `ContentDocumentParsingTest` y
`QuestionnaireDocumentParsingTest` + casos de repositorio. Corrida manual E2E ✅
(P-50, 2026-07-17): reflejo sin reinstalar, corrupto simulado en consola
tolerado sin error visible, modo avión, regresión RF-21/22/23/27 y caso
negativo de CP-25 — todos los pasos en verde; documentos de prueba restaurados.
Veredictos de agentes: tres APROBADO. *(Nota v0.2.0: los helpers de parsing se
extrajeron a `core/network/FirestoreDocumentParsing.kt` al cerrar P-51; los
tests siguen en verde.)*

## 8. Corridas manuales BLOQUEANTES del tag `v0.2.0-trl4`

Pendientes de la usuaria, en emulador (`Medium_Phone_API_35`, instalación
limpia, proyecto real `aurora-31c37`). Los pasos completos y numerados están en
`docs/PENDIENTES.md` (entrada de cada P); aquí el resumen ejecutable. Orden
sugerido para minimizar reinstalaciones:

1. **`connectedDebugAndroidTest`** — correr las 8 suites instrumentadas
   (~29 tests) en el emulador antes de empezar: deben quedar 0 fallos.
2. **P-28 final (CP-05/28/31)** — Device Explorer sobre `shared_prefs/`
   (`search_index.xml`, `store_meta.xml`): solo Base64 bajo claves neutras.
3. **P-29 (CP-04/08)** — login con Firebase real: mensaje ÚNICO para
   contraseña/correo inválidos, pausa de 5 fallos que sobrevive al reinicio.
4. **P-43 (CP-17/18)** — cuestionario v2: abandono sin rastro, nube a mitad,
   `am kill`, aviso RF-30 visible sin scroll en 360×800, captura bloqueada.
5. **P-46 (CP-20/22)** — registros: validaciones, FLAG_SECURE en el selector
   de fecha, borrador no persiste, edición prellenada (mismo doc remoto),
   eliminación con doble confirmación y propagación (también en modo avión),
   teclear >5 min sin expirar.
6. **P-59 (CP-21)** — evidencias: galería sin diálogo de permisos, cámara sin
   rastro en DCIM con regreso directo al formulario, PDF en visor interno con
   captura bloqueada, persistencia tras cerrar sesión, `media_store/` solo
   blobs ilegibles, cascadas RF-36/RF-20, saneamiento del editor.
7. **P-61 (CP-24)** — RF-39 con DOS instalaciones: A crea y sube (schemaVersion
   2, meta/profile con el sobre), B restaura íntegro (registros + adjunto),
   edición cruzada, borrado remoto en cascada, contraseña restablecida →
   respaldo ilegible con aviso y re-subida desde A, cambio de contraseña sin
   re-cifrado.
8. **P-14 + P-11 (CP-07)** — miniatura de recientes sin el código a medio
   teclear; Gboard no sugiere la clave (ambas barras: fachada y práctica).
9. **P-26 + P-27 (CP-09/14)** — número pre-marcado en recientes del marcador;
   llamar→atrás aterriza en la fachada y zanjar el desacuerdo de la gracia.

No bloqueantes pero recomendados en la misma pasada: CP-34 (tiempos en gama
media) y CP-36 (API 26).

## 9. Pasada final de agentes (v0.2.0)

Ejecutada el 2026-07-30 sobre TODO el repo (app + admin/), no solo el diff:

- **auditora-seguridad: APROBADO PARA TAG.** Las 7 reglas innegociables se
  cumplen (FLAG_SECURE por construcción incluido el visor de adjuntos y las
  rutas de RF-39; acceso indistinguible con puerta en paralelo; salida rápida
  convergente; **0 hits** de logs/telemetría; cinco llaves de alias neutro solo
  en Keystore, nada hardcodeado; identidad 100% clima con los tres guardianes
  en verde; consentimiento bloqueante). Secretos fuera del repo (seeder y
  panel los rechazan dentro); reglas Firestore con dos mundos sin puentes.
  **Ningún riesgo residual nuevo**: todo mapea a P-22/33/35/37/48/56/60/62.
- **revisora-arquitectura: APROBADO.** Regla de dependencias limpia (cero
  imports entre features; content y questionnaire comparten helpers VÍA core);
  cero excepciones hacia UI; 20/20 ViewModels con `StateFlow` inmutable; P-51 y
  P-42a/b saldados sin abrir grietas. No bloqueantes registrados en P-63.
- **verificadora-rf: APROBADO PARA TAG.** Re-ejecutó la suite forzando
  `cleanTestDebugUnitTest`: **522/74/0 en debug y release** (detectó y descartó
  un filtro residual del daemon que falseaba la primera corrida), ktlint limpio,
  panel 71/71. Muestreó las 40 clases citadas en §4 para los RF nuevos: **todas
  existen y los conteos coinciden**. Confirmó que los bloqueantes de §8
  coinciden EXACTAMENTE con los manuales abiertos de PENDIENTES. Única
  discrepancia (corregida): la cifra histórica de `KeystoreSecretCodeHasherTest`.

Veredicto conjunto: **el árbol queda LISTO PARA TAG `v0.2.0-trl4`, condicionado
únicamente a las corridas manuales de §8.**

# Aurora — Resultados de pruebas (cierre Fase 3, TRL 4)

> Evidencia para el informe. Corresponde al tag `v0.1.0-trl4` (Día 7).
> Fecha de la corrida: 2026-07-15. Alcance: todos los RF de prioridad **Alta**
> alcanzables sin el panel web (ver `PLAN_FASE3.md`). Los RF **Media** y el panel
> quedan como trabajo futuro (ver `docs/PENDIENTES.md`).
>
> **Actualización 2026-07-17:** la Fase B2 (panel administrativo, RF-40–44) fue
> implementada y verificada end-to-end contra el proyecto real — ver **§6**. La
> corrida formal de sus CP y la regresión de contenido quedan para la Fase B3.

## 1. Resumen de la corrida automatizada

Comando: `./gradlew test ktlintCheck lint assembleDebug` → **BUILD SUCCESSFUL**.

| Métrica | Resultado |
|---|---|
| Pruebas unitarias (JVM, variantes debug+release) | **260 pruebas, 0 fallos, 0 errores, 0 omitidas** (43 clases de test) |
| ktlint | Sin violaciones |
| Lint | **0 errores · 51 warnings** — todos preexistentes e informativos (42 `GradleDependency` = versiones más nuevas disponibles, 3 `AndroidGradlePluginVersion`, 2 `Typos`, 1 `UnusedResources`, 1 `RedundantLabel`, 1 `PluralsCandidate`, 1 `OldTargetApi`). **Cero warnings nuevos.** |
| `assembleDebug` | OK |
| `assembleRelease` (R8 + shrink, ver §2) | OK |

Pruebas instrumentadas (emulador, no entran en `./gradlew test`): las de cifrado
`KeystorePayloadCipherTest` (5/5) y la cabecera SQLCipher se verificaron en emulador
API 35 (ver P-03/P-49 en PENDIENTES); `KeystoreSecretCodeHasherTest` 8/8.

## 2. P-05 — Ofuscación R8 del release (RNF-05)

`app/build.gradle.kts` (release): `isMinifyEnabled = true` + `isShrinkResources = true`,
reglas en `app/proguard-rules.pro` (SQLCipher JNI + kotlinx.serialization; Room/Hilt/
Firebase/Retrofit vía sus `consumer-rules` del AAR). `assembleRelease` → BUILD SUCCESSFUL
(tareas `minifyReleaseWithR8`, `shrinkReleaseRes` ejecutadas).

**Verificación en el DEX** (`apkanalyzer dex packages --defined-only app-release.apk`):

| Nombre buscado | Coincidencias en clases propias |
|---|---|
| `SessionManager`, `SecretCode*`, `SearchQueryGate`, `QuickExit*`, `SensitiveState*` | **0** |
| `Questionnaire*`, `GuidanceLevel`, `CalculateGuidance*` | **0** |
| paquetes `access`, `questionnaire`, `core.security` | **0** (renombrados a `a`,`b`,`c`…) |
| `Secure*` (SecureNavigation, SecureFlagLedger, SecureBackStackGuard, SecureDayPicker…) | **0** en `com.auroraclima` |

Únicas clases propias que R8 deja legibles (todas neutras, no delatan el propósito):
`AuroraApplication` y `MainActivity` (obligadas por el manifest / identidad de clima)
y `JournalDatabase` / `WeatherDatabase` (+ sus `_Impl`) por el constraint de reflexión
de Room. «Journal» es palabra genérica; residual documentado (hermano de P-44).
Los hits sueltos de `Secure`/`SignIn`/`Consent` que aparecen en el DEX pertenecen a
librerías de terceros (GMS/Firebase), no a `com.auroraclima`.

**Estado P-05: CERRADO.**

## 3. P-06 — Permisos del manifest fusionado (release final) (RNF-04)

`apkanalyzer manifest permissions app-release.apk`:

| Permiso | Origen | Justificación | ¿Peligroso? |
|---|---|---|---|
| `android.permission.INTERNET` | Declarado por Aurora | Clima Open-Meteo (RF-02/RF-03) + Firebase Auth/Firestore (RF-07/15/26/37). Esperable en una app de clima | No (normal) |
| `android.permission.ACCESS_NETWORK_STATE` | Fusionado (Firebase/Firestore) | La persistencia offline de Firestore consulta la conectividad para encolar/subir (RF-26, RF-37/38) | No (normal) |
| `com.auroraclima.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Generado por AndroidX Core | Permiso de firma auto-generado que protege receivers dinámicos internos frente a otras apps; solo lo posee esta app, sin capacidad ni superficie de privacidad | No (signature) |
| `com.google.android.providers.gsf.permission.READ_GSERVICES` | Fusionado (GMS) | Lectura de la config del Google Services Framework, estándar en cualquier app con Firebase | No (normal) |

**Ningún permiso peligroso** (sin runtime prompt): no hay cámara, ubicación, contactos,
almacenamiento, teléfono ni SMS. En particular **no se solicita `CALL_PHONE`**: la llamada
de RF-23 usa `ACTION_DIAL` (marcador precargado, la usuaria decide llamar), lo que evita
el permiso y refuerza el camuflaje.

Componentes exportados: solo `MainActivity` (launcher) es propio. Los demás
(`GenericIdpActivity`, `RecaptchaActivity`, `RevocationBoundService`,
`ProfileInstallReceiver`) los inyectan los SDK de Firebase Auth / GMS y son genéricos de
infraestructura de autenticación; no delatan el doble fondo. Residual aceptado.

**Estado P-06: CERRADO.**

## 4. Matriz de casos de prueba (CP)

Leyenda de estado:
- **PASA** — cubierto por prueba automatizada (se cita la clase).
- **MANUAL** — requiere verificación en emulador (se dan pasos y resultado esperado).
- **NO APLICA** — el RF no está en el alcance de Fase 3 (RF Media / panel web); trabajo futuro.

Muchos CP son **mixtos**: la lógica está cubierta por pruebas automatizadas y la parte
visual/UX o con Firebase real se verifica en emulador. Las pasadas manuales ya
ejecutadas por la usuaria se marcan ✅.

| CP | RF/RNF | Estado | Evidencia automatizada / Pasos manuales |
|---|---|---|---|
| CP-01 | RF-01 | PASA + MANUAL | Auto: `CamouflageResourcesTest`, `ManifestMinimizationTest`, `NoTracesGuardTest` (ningún término/permiso/recurso delator). Manual: instalar y confirmar visualmente que ícono, nombre («Aurora»), splash y pantalla inicial son 100% de clima. Esperado: nada sugiere el propósito real. |
| CP-02 | RF-02, RF-03, RF-04 | PASA (RF-02/03) · RF-04 NO APLICA | Auto: `WeatherViewModelTest` (16), `WeatherRepositoryImplTest` (7), `WeatherCardMapperTest`, `WeatherConditionTest`, `GeocodingApi`/`ForecastApi`. RF-02/03 (búsqueda de ciudad + clima actual + último dato en caché Room). **RF-04 (pronóstico 5 días): NO implementado — trabajo futuro.** |
| CP-03 | RF-05, RF-06 | PASA + MANUAL | Auto: `ValidateSecretCodeUseCaseTest` (10), `CamouflageResourcesTest`, `NoTracesGuardTest`. Manual: explorar toda la fachada sin código y confirmar que no hay ningún indicio del doble fondo. Esperado: no aparece nada. |
| CP-04 | RF-07 | PASA + MANUAL (P-29) | Auto: `CreateAccountViewModelTest` (10), `CreateAccountUseCaseTest` (5), `FirebaseAuthRepositoryTest` (10). Manual con Firebase real: crear cuenta válida; reintentar mismo correo → error; contraseña débil → error. Esperado: los tres casos según criterios de RF-07. |
| CP-05 | RF-08, RF-09 | PASA (RF-08) + MANUAL (P-28) · RF-09 NO APLICA | Auto RF-08: `CreateSecretCodeUseCaseTest` (9), `CodeSetupViewModelTest` (6), `LocalSecretCodeRepositoryTest` (5), `KeystoreSecretCodeHasherTest` (instrum. 8/8). Manual P-28 (parcial ✅ 2026-07-11): rechazos en vivo (corta, `Bucaramanga`, `bogota` sin tilde, confirmación distinta); resta inspeccionar `shared_prefs/` con Device Explorer (solo Base64 bajo claves neutras). **RF-09 (cambio de código): NO implementado — futuro.** |
| CP-06 | RF-10, RF-11 | PASA (RF-10) + MANUAL ✅ (P-34) · RF-11 NO APLICA | Auto RF-10: `TutorialViewModelTest` (7). Manual P-34 ✅ (2026-07-11): tutorial con práctica real, rechazo de clave incorrecta, sin repetir el tutorial al reingresar. **RF-11 (reabrir tutorial desde configuración): NO implementado — no hay pantalla de configuración aún; futuro.** |
| CP-07 | RF-12, RF-13, RF-14 | PASA + MANUAL (P-11, P-14) | Auto: `ValidateSecretCodeUseCaseTest` (10), `ConstantTimeTest` (4, respuesta en tiempo constante = indistinguible). Manual: P-11 (Gboard no debe aprender/sugerir la clave), P-14 (miniatura de recientes no muestra el código a medio teclear). Pasos en PENDIENTES. |
| CP-08 | RF-15 | PASA + MANUAL (P-29) | Auto: `SignInUseCaseTest` (9), `LoginViewModelTest` (9), `FirebaseAuthRepositoryTest` (10). Manual P-29 con Firebase real: código→«Hola de nuevo» (no panel); credenciales correctas→panel; contraseña incorrecta/correo inexistente/mal formado→MISMO mensaje; 5 fallos→pausa 5:00 con botón deshabilitado; la pausa se retoma tras reinicio; expirada→acceso normal. |
| CP-09 | RF-16 | PASA + MANUAL (P-27) | Auto: `SessionManagerTest` (12) (limpieza de estado sensible, `closeSession`), `SecureFlagLedgerTest`. Manual: nube→fachada en <1 s, sin regreso por navegación ni rastro de sesión; llamar→marcador→atrás aterriza en fachada (P-27). |
| CP-10 | RF-17, RF-18 | PASA + MANUAL (P-16) | Auto: `ConsentViewModelTest` (2) (bloqueante, sin preselección, rechazo sin recolección). Manual: relectura del consentimiento desde privacidad → pendiente de la pantalla de configuración (P-16, futuro). Parte de creación de cuenta cubierta. |
| CP-11 | RF-19 | NO APLICA | **RF-19 (cierre de sesión de usuaria): NO implementado — futuro.** (La limpieza interna de sesión en salida rápida existe, pero el logout explícito de RF-19 no.) |
| CP-12 | RF-20 | NO APLICA | **RF-20 (eliminación de cuenta): NO implementado — futuro.** |
| CP-13 | RF-21, RF-22 | PASA + MANUAL | Auto: `TopicsViewModelTest` (3), `TopicDetailViewModelTest` (4), `RoutesViewModelTest` (3), `FirestoreContentRepositoryTest` (10). Manual: con contenido sembrado, navegar tipos de violencia y rutas de atención completos. Esperado: contenido completo y navegable. |
| CP-14 | RF-23 | PASA + MANUAL (P-26) | Auto: `AgencyDetailViewModelTest` (4), `LinesViewModelTest` (3). Manual P-26: tocar llamar abre el marcador con el número precargado, sin registro en la app; verificar si el número persiste en recientes. |
| CP-15 | RF-24, RF-25 | PASA + MANUAL ✅ (2026-07-17, B3) | Auto (Fase B3): `ContentDocumentParsingTest` (7), `QuestionnaireDocumentParsingTest` (8) y casos nuevos de repositorio — malformado omitido sin romper el resto, esquema desconocido que no sustituye a la versión vigente, colección vacía sin error, actualización reflejada en la siguiente consulta. Manual ✅ (usuaria, 2026-07-17): checklist completo de §7 en verde — reflejo sin reinstalar, corrupto simulado en consola sin error visible, caché en modo avión. |
| CP-16 | RF-26, RNF-08 | MANUAL | Firestore con persistencia offline. Manual: activar modo avión y abrir el contenido informativo. Esperado: contenido completo disponible, carga ≤ 2 s. |
| CP-17 | RF-27, RF-28 | PASA + MANUAL (P-43) | Auto: `GuidanceViewModelTest` (16). Manual P-43: responder 2 preguntas→retroceder (sigue marcado)→«Abandonar»→reingresar: portada vacía; nube a mitad→fachada→reingreso: portada vacía; `am kill`→sin rastro. |
| CP-18 | RF-29, RF-30 | PASA + MANUAL (P-43) | Auto: `CalculateGuidanceLevelUseCaseTest` (14) (nivel correcto por respuestas y umbrales), `GuidanceViewModelTest`. Manual P-43: en 360×800 el aviso de RF-30 visible SIN scroll bajo la tarjeta del nivel, también en el nivel más alto; «Ver rutas» abre rutas y vuelve. |
| CP-19 | RF-31 | NO APLICA | **RF-31 (persistir el resultado con confirmación): NO implementado — «Terminar» siempre descarta (P-41); guardar opcional es futuro.** El descarte-sin-registro sí es el comportamiento actual (cubierto por CP-17). |
| CP-20 | RF-32 | PASA + MANUAL (P-46) | Auto: `JournalEditorViewModelTest` (7), `AddJournalEntryUseCaseTest` (3), `JournalDaoTest` (4), `LocalJournalRepositoryTest` (5). Manual P-46: validación de obligatorios sin borrar lo diligenciado, selector de fecha con FLAG_SECURE, guardado y borrador que no persiste tras salida rápida. |
| CP-21 | RF-33 | NO APLICA | **RF-33 (evidencias adjuntas): NO implementado — futuro.** |
| CP-22 | RF-34, RF-35, RF-36 | PASA (RF-34) + MANUAL (P-46) · RF-35/36 NO APLICA | Auto RF-34: `JournalListViewModelTest` (3), `JournalDetailViewModelTest` (4) (orden cronológico inverso, detalle). Manual P-46: listado con fecha+tipo, fila abre detalle completo. **RF-35/36 (editar/eliminar): NO implementado — futuro.** |
| CP-23 | RF-37, RF-38 | PASA + MANUAL ✅ (P-49) · RF-38 parcial | Auto RF-37: `FirestoreJournalSyncRepositoryTest` (3), `KeystorePayloadCipherTest` (instrum. 5/5). Manual P-49 ✅ (2026-07-15): offline→reconectar sube solo; en Firestore `payload`/`iv` ilegibles (Base64), sin `day`/`kind`/`detail` en claro. **RF-38 (cola offline): el mecanismo existe vía la cola durable de Firestore y quedó probado en P-49; su formalización como RF propio es futuro.** |
| CP-24 | RF-39 | NO APLICA | **RF-39 (multi-dispositivo): NO implementado — la llave del payload está ligada al dispositivo (P-48); recuperación en 2.º dispositivo es futuro.** |
| CP-25 | RF-40 | MANUAL parcial ✅ (B2) | Panel implementado (B1/B2). Acceso con rol admin verificado en uso real del panel (2026-07-17). Denegación a cuentas sin claim implementada (`signOut` inmediato + mensaje genérico, `admin/src/auth/AuthContext.tsx`); **caso negativo verificado en vivo el 2026-07-17 (corrida B3, §7): cuenta sin rol → acceso denegado con mensaje genérico.** |
| CP-26 | RF-41, RF-42, RF-44 | MANUAL ✅ (2026-07-17) + PASA (panel) | E2E panel→app en el proyecto real: tipo editado y publicado reflejado en la app (el borrador NO es visible antes de publicar); teléfono de entidad actualizado y reflejado; eliminar una entidad referenciada queda bloqueado listando las rutas; una ruta con entidad sin publicar no se publica hasta publicar la entidad. Auto (panel): vitest 66/66 (teléfonos, referencias cruzadas, órdenes, normalización). Corrida formal + RF-24/25 en B3. |
| CP-27 | RF-43 | MANUAL ✅ (2026-07-17) + PASA (panel) | E2E: la edición publicada rige el cuestionario de la app (versión automática max+1; la app toma el vivo de mayor versión). Bloqueos verificados en vivo: sin preguntas/niveles, umbral inalcanzable, marcador [POR CONFIRMAR] (compuerta M-2) y empate de versión entre varios formularios (pide re-guardar). Eliminación solo de cuestionarios no vigentes. Auto: `coherence.test.ts` (reglas derivadas del motor, fixture = f01 real). |
| CP-28 | RNF-01 | PASA + MANUAL (P-28/P-46) | Auto: cabecera SQLCipher ≠ «SQLite format 3» (instrum. P-03), `KeystorePayloadCipherTest` (5/5). Manual: Device Explorer sobre `databases/journal_store.db`(+`-wal`) y `shared_prefs/` → ilegibles / solo Base64. |
| CP-29 | RNF-02 | PASA + MANUAL (P-43/P-14) | Auto: `SecureFlagLedgerTest` (5) (contabilidad de FLAG_SECURE por pantalla interna). Manual: intentar captura en portada/pregunta/resultado y registros → bloqueada; recientes → sin contenido interno. |
| CP-30 | RNF-03 | PASA + MANUAL | Auto: `SessionManagerTest` (12), `InteractionThrottleTest` (4). Manual: dejar el doble fondo inactivo 5 min → cierre automático a la fachada. Cubierto por construcción para scroll (P-21) y campos de texto (P-15). |
| CP-31 | RNF-04 | PASA + MANUAL | Auto: `NoTracesGuardTest` (7), `ManifestMinimizationTest` (5). Manual: auditoría de `/data/data` con Device Explorer contra el inventario aprobado (junto a CP-28). |
| CP-32 | RNF-05 | PASA + MANUAL | Auto: `CamouflageResourcesTest` (2) (términos vedados en recursos/código) + verificación DEX de P-05 (§2). Manual: recorrer la lista de verificación de camuflaje ítem por ítem (nombre, ícono, notificaciones, recientes, permisos, strings, paquete). |
| CP-33 | RNF-06 | PASA ✅ (P-04) | Auto: `firestore/rules.test.mjs`. Manual ✅ (2026-07-15): CP-37 en el Rules playground **10/10** — dueña allow, otra cuenta/anónima deny sobre registros ajenos; contenido solo lectura autenticada. |
| CP-34 | RNF-07 | MANUAL | Manual en dispositivo de gama media: arranque ≤ 3 s, interacciones locales ≤ 2 s. |
| CP-35 | RNF-09 | NO APLICA (Fase 4) | Evaluación heurística formal de usabilidad — corresponde a Fase 4. |
| CP-36 | RNF-10 | MANUAL | Manual: ejecutar en la versión mínima (API 26) y en un dispositivo reciente. `minSdk = 26` configurado. |
| CP-37 | RNF-11 | PASA | Revisión de arquitectura por capas: agente `revisora-arquitectura` (pasada final Día 7) + muestreo de `docs/ARQUITECTURA.md`. |

### Cobertura resumida

- **CP con cobertura automatizada (total o parcial):** CP-01..CP-10, CP-13, CP-14, CP-15,
  CP-17, CP-18, CP-20, CP-22, CP-23, CP-28..CP-33, CP-37.
- **CP solo manual (emulador/dispositivo):** CP-16, CP-34, CP-36.
- **CP NO APLICA (RF Media / Fase 4):** CP-11, CP-12, CP-19, CP-21,
  CP-24, CP-35, y las partes RF-04/RF-09/RF-35/RF-36/RF-39 de CP mixtos.
- **CP del panel verificados en las Fases B2/B3 (2026-07-17, ver §6 y §7):**
  CP-25 (incluido el caso negativo, corrida B3), CP-26, CP-27 y CP-15
  (automatizado + corrida manual E2E de la usuaria, P-50 ✅).

## 5. Pasadas manuales en emulador ya ejecutadas por la usuaria

| Referencia | Fecha | Resultado |
|---|---|---|
| P-34 (CP-06, RF-10) | 2026-07-11 | ✅ Tutorial con práctica real, sin repetir al reingresar |
| P-28 parcial (CP-05, RF-08) | 2026-07-11 | ✅ Rechazos de la clave en vivo; resta Device Explorer |
| P-49 (CP-23, RF-37) | 2026-07-15 | ✅ Sync cifrada offline→online; blobs ilegibles en Firestore |
| P-04/CP-37 (CP-33, RNF-06) | 2026-07-15 | ✅ Rules playground 10/10 |
| CP-26 (RF-41/42/44, Fase B2) | 2026-07-17 | ✅ E2E panel→app completa (ver §6) |
| CP-27 (RF-43, Fase B2) | 2026-07-17 | ✅ Editor del cuestionario, bloqueos y reflejo en la app (ver §6) |
| Reglas + `/drafts` (RNF-06) | 2026-07-17 | ✅ Playground tras desplegar: `/drafts` solo admin; `/users` intacto |
| P-50 (CP-15 + regresión, Fase B3) | 2026-07-17 | ✅ Ciclo panel→app completo: reflejo sin reinstalar, corrupto simulado tolerado, caché en modo avión, regresión RF-21/22/23/27 y caso negativo de CP-25 (ver §7) |

Los CP manuales restantes (P-11, P-14, P-26, P-27, P-29, P-43, P-46 y las inspecciones de
Device Explorer de CP-28/31) quedan para la pasada final en emulador con la instalación del
`app-release.apk` (criterio de salida del Día 7).

## 6. Fase B2 — Panel administrativo (post `v0.1.0-trl4`, 2026-07-17)

Alcance: RF-40 a RF-44, commits `3827b83`, `c413523`, `555a602`, `98f1f88`,
`3e6f863` y `73a29da`. El panel produce EXACTAMENTE el esquema que la app
consume (espejo de los DTO de `content/data` y `questionnaire/data`;
`schemaVersion` = 1, sin campos nuevos) y la app solo ve lo publicado: los
borradores viven en la colección `/drafts`, exclusiva del claim admin. La
regla nueva fue **desplegada en la consola y verificada en el Playground el
2026-07-17** (`/drafts` denegada sin claim; `/users/**` intacto, RNF-06).

**Corrida automatizada del panel** (`admin/`): `npm run test` → **66/66**
(vitest: teléfonos colombianos, referencias cruzadas rutas↔entidades↔cuestionario,
coherencia del cuestionario con el f01 real como fixture, normalización y
guardarraíl de versiones), `npm run lint` y `npm run build` en verde —
verificados además commit por commit. Android sin cambios:
`./gradlew test ktlintCheck assembleDebug` en verde (corrido por la agente
verificadora; ningún archivo de `app/` tocado en la fase).

**Corrida manual E2E de la usuaria** (2026-07-17, proyecto real
`aurora-31c37` + emulador `Medium_Phone_API_35`): todos los pasos de
`admin/README.md` §6 en verde — publicación reflejada y borradores invisibles
para la app (RF-41/RF-44), teléfono actualizado y bloqueos de integridad
referencial (RF-42), cuestionario con versión automática, bloqueos de
coherencia y compuerta `[POR CONFIRMAR]` (RF-43), y el flujo con varios
cuestionarios: solo rige el vigente, empate de versión bloqueado y eliminación
permitida únicamente para no vigentes.

**Pendientes que esta corrida NO cierra:**
- Corrida formal de CP-25 (caso negativo de RF-40), CP-26, CP-27 + CP-15
  (RF-24/RF-25 contra contenido editado desde el panel, incluido esquema
  corrupto simulado) y regresión de RF-21/22/23/26/27 → **Fase B3**.
- Validación de CONTENIDO con la directora (P-38 y datos `[POR CONFIRMAR]`
  de entidades/líneas): el `reviewNote` del cuestionario se vació solo para
  estas pruebas; la decisión editorial sigue abierta.

## 7. Fase B3 — Cierre del ciclo de contenido en la app (2026-07-17)

Alcance: RF-24, RF-25 y RF-44 (lado app), commits `ef1f925`, `ec9355d`,
`14b62d0` y `054b1ce`. La exploración confirmó que el mecanismo del ciclo ya
operaba desde antes (la app lee solo las colecciones vivas publicadas, con
`get()` servidor-primero y caché persistente de respaldo, y filtra
`schemaVersion` desconocido); la brecha real de RF-25 estaba en la capa
snapshot→DTO: los getters tipados del SDK (`getString`/`getLong`) **lanzan**
ante un campo con tipo incorrecto, así que UN documento corrupto publicado por
error tumbaba el fetch completo del módulo (error genérico visible) en vez de
omitirse — y esa capa no tenía ningún test.

**Cambio aplicado (único de producción):** el parsing de ambos remote sources
pasó a casts seguros sobre el mapa crudo del documento
(`internal fun Map<String, Any?>.toXxxDto(id)`): un tipo inesperado equivale a
campo ausente, el mapper omite solo ese documento (content) o descarta el
formulario y sigue rigiendo el válido de mayor versión presente en `forms`
(questionnaire) — esa es la conservación de la última versión válida de RF-25.
Un decimal no entero en campos numéricos (`weight`, `minScore`, `order`…) se
trata como corrupto en vez de truncarse (mejora M-2 de la revisora: truncar
2,5 → 2 sesgaría umbrales calibrados sin descartar el documento).

**Corrida automatizada (2026-07-17):** `./gradlew test ktlintCheck
assembleDebug` en verde; la verificadora forzó re-ejecución con
`cleanTestDebugUnitTest`: **278 tests debug / 556 totales, 0 fallos**. Suites
nuevas: `ContentDocumentParsingTest` (7) y `QuestionnaireDocumentParsingTest`
(8) — tipos corruptos por campo, listas/entradas embebidas corruptas, mapa
vacío, decimales rechazados y la cadena completa hasta el descarte del mapper —
más casos nuevos en los tests de repositorio: malformado omitido sin romper el
resto, esquema desconocido que no sustituye a la versión vigente (RF-25),
colección vacía = éxito con lista vacía, y actualización reflejada en la
siguiente consulta (RF-24/RF-44). La regresión automatizada de
RF-21/22/23/26/27/42/43 sigue en verde (los DTOs, mappers, repositorios y UI
no cambiaron).

**Veredictos de agentes (2026-07-17):** verificadora-rf APROBADO en RF-24,
RF-25 y RF-44 (parte automatizable); revisora-arquitectura APROBADO (M-1
registrada en P-51; M-2/M-3 aplicadas en `054b1ce`); auditora-seguridad
APROBADO sin hallazgos — cero logs/telemetría, fallos silenciosos (§3/P-24),
colecciones neutras intactas, sin cambios de manifest/reglas/dependencias, y
la tolerancia nueva ELIMINA un comportamiento anómalo observable (el fetch que
caía entero) en lugar de introducir uno.

**Corrida manual E2E ✅ (usuaria, 2026-07-17, P-50 cerrado):** ejecutada
completa con emulador `Medium_Phone_API_35` + panel (`npm run dev`) + proyecto
real `aurora-31c37`; **todos los pasos en verde**. Checklist ejecutado:
1. Con red, abrir el módulo de información y anotar el texto de un tema y una
   línea.
2. Editar y publicar ese tema/línea en el panel; salir del módulo en la app y
   reabrirlo: debe verse el texto editado sin reinstalar (RF-24/RF-44).
3. Publicar una nueva versión del cuestionario; abrirlo en la app: debe regir
   la nueva versión (RF-44).
4. Modo avión: reabrir módulo y cuestionario — última versión ya vista, sin
   error (RF-25/RF-26).
5. Adversarial (consola Firestore): `title` numérico en un doc de `topics` →
   el módulo carga sin ese documento y sin error visible; corromper el `forms`
   de mayor versión dejando uno válido menor → rige el válido. Restaurar al
   terminar.
6. Regresión rápida RF-21/22/23/27 con el contenido editado + caso negativo de
   CP-25 (cuenta sin claim → acceso denegado con mensaje genérico).

Con esta corrida quedan verificados de punta a punta CP-15 (RF-24/RF-25),
la parte app de CP-26 (RF-44) y el caso negativo de CP-25 (RF-40); los
documentos corruptos de prueba fueron restaurados en la consola al terminar.
La Fase B (panel + ciclo de contenido) queda funcionalmente cerrada; siguen
abiertas solo la validación editorial con la directora (P-23/P-38) y los
pendientes post-MVP registrados (P-51 y anteriores).

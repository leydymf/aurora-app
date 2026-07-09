# Pendientes registrados por las auditorías

> Hallazgos no bloqueantes de los agentes de revisión (revisora-arquitectura y
> auditora-seguridad). Al cerrar cada uno, marcarlo y anotar el commit.
> Los resueltos se mueven a la sección final como evidencia para el informe.

## Abiertos

| # | Pendiente | Origen | Fecha límite | Sesión prevista |
|---|---|---|---|---|
| P-03 | Agregar SQLCipher al configurar `core/database` (no después). Incluye H-3: declarar exclusiones explícitas en `backup_rules.xml` y `data_extraction_rules.xml` (`database/`, `sharedpref/`) para no depender solo de `allowBackup=false` | revisora-arquitectura + auditora-seguridad H-3 | antes de crear la BD del doble fondo | Día 3, Sesión 3 |
| P-04 | `firestore.rules` versionado en la raíz del repo (RNF-06) | revisora-arquitectura | con la sincronización | Día 6, Sesión 3 |
| P-05 | Activar `isMinifyEnabled = true` (+ `isShrinkResources`) en release para ofuscar nombres internos. Condición dura (auditoría D2 A-01, reiterada D3 M2): NINGÚN APK sale del entorno de desarrollo sin R8 — los nombres `SecretCode*`/`SearchQueryGate`/`Secure*` (`SecureBackStackGuard`, `SecureFlagLedger`, `secureGraph`, `SecureDialog`) quedan legibles en el DEX sin minificar | auditora-seguridad H-3 + A-01 + M2 | antes de que cualquier APK salga del entorno | Día 7 |
| P-06 | Revisar el manifest fusionado del APK: permisos que agregan Firebase/Retrofit (`INTERNET`, `ACCESS_NETWORK_STATE`) — confirmar que no aparece ninguno injustificado (RNF-04). *Avance 2026-07-09:* revisados los fusionados de debug y release — solo `INTERNET` + `ACCESS_NETWORK_STATE` (y permisos internos de GMS); las activities exportadas de tooling (`PreviewActivity`, `ComponentActivity`) existen solo en debug. Repetir sobre el APK final | auditora-seguridad | antes del APK v0.1.0-trl4 | Día 7 |
| P-07 | Verificar que el repositorio remoto sea PRIVADO al crearlo: los docs versionados describen el propósito real y un repo público rompería el camuflaje (H-4) | auditora-seguridad | al crear el remoto | — |
| P-08 | Actualizar el criterio 2 de RF-01 en el xlsx fuente (`Aurora_Modelado_Requerimientos.xlsx`) con el refinamiento F1: "Aurora" solo como marca de clima. Nota provisional ya agregada en `docs/REQUERIMIENTOS.md` | verificadora-rf D1 | antes del cierre de Fase 3 | manual (usuaria) |
| P-09 | Validar con la directora que el ícono launcher (arte de `18-aurora-app-icon.svg`) no circule idéntico en material institucional de fundaciones (O1). (La parte de FLAG_SECURE quedó resuelta: hoy `core/security/SecureNavigation.kt`, D2 S1) | auditora-seguridad | antes de la difusión | manual (usuaria) |
| P-11 | Verificar en emulador si el teclado (Gboard) aprende el código tecleado pese a `autoCorrect=false` y lo sugiere después (RF-14 criterio 2); si ocurre, evaluar `IME_FLAG_NO_PERSONALIZED_LEARNING` sin delatar la barra | auditora-seguridad D2 B-03 + verificadora-rf D2 | antes del cierre de Fase 3 | Día 7 |
| P-12 | Riesgo aceptado y documentado: el texto tecleado (incluido un código válido) viaja a la API de geocoding de Open-Meteo como cualquier búsqueda — requisito de indistinguibilidad de red (RF-13); sin caché OkHttp en disco. Mitigación: RF-08 exige mínimo 6 caracteres y prohíbe nombres de ciudad. Revisar con la directora al validar el mecanismo de códigos | auditora-seguridad D2 | — | validación con directora |
| P-13 | Tests de la capa data de access: `LocalSecretCodeRepository` (round-trip save/read, prefs ausentes → null, Base64 corrupto → null) y `KeystoreSecretCodeHasher` (determinismo por (salt, código), salts distintos → hashes distintos), con Robolectric o instrumentados. El fake actual solo cubre el caso de uso, no la implementación real | revisora-arquitectura D2 M1 | antes del cierre de Fase 3 | Día 3, Sesión 3 (junto a Room+SQLCipher) o Día 7 |
| P-14 | Verificar en emulador (CP de RF-14) que la miniatura de recientes NO muestra el código a medio teclear tras pulsar Home mientras se escribe (la limpieza corre en ON_PAUSE, mejor esfuerzo). Si aun así aparece: `setRecentsScreenshotEnabled(false)` mientras `isSearchActive` (API 33+, la miniatura muestra el splash). NO usar FLAG_SECURE en la fachada: una tarjeta negra solo al buscar sería un distinguidor | auditora-seguridad D2 M-04 | antes del cierre de Fase 3 | Día 2 cierre (prueba manual) / Día 7 |

## Resueltos

| # | Pendiente | Resolución |
|---|---|---|
| P-00 | `allowBackup="true"` permitiría que el auto-backup de Android copie la futura BD del doble fondo (H-1, severidad alta) | `allowBackup="false"` en el manifest — commit `2e4a0d6` |
| P-01 | Identidad de clima: `app_name` e ícono del template | RF-01: "Aurora" como marca de clima (decisión F1), ícono adaptativo del amanecer |
| P-02 | Quitar `Greeting`/`GreetingPreview` y colores púrpura del template | Reemplazados por `WeatherScreen` y paleta del amanecer (RF-01) |
| P-10 | Sacar el grafo interno del back stack al pasar a background; la restauración desde recientes/muerte de proceso no debe caer en el doble fondo sin código (M-02) | `SecureBackStackGuard` en `core/security` (2026-07-09): pop hasta la fachada en ON_STOP (salvo rotación) y re-comprobación al restaurar. La salida rápida (RF-16, día 3) debe además hacer su propio `popBackStack` |

# Pendientes registrados por las auditorías

> Hallazgos no bloqueantes de los agentes de revisión (revisora-arquitectura y
> auditora-seguridad). Al cerrar cada uno, marcarlo y anotar el commit.
> Los resueltos se mueven a la sección final como evidencia para el informe.

## Abiertos

| # | Pendiente | Origen | Fecha límite | Sesión prevista |
|---|---|---|---|---|
| P-01 | Identidad de clima: reemplazar `app_name` "aurora" e ícono del template (viola RNF-05 si se instala así) | auditora-seguridad H-2 | antes de instalar en dispositivo real | Día 1, Sesión 2 (RF-01) |
| P-02 | Quitar composables `Greeting`/`GreetingPreview` de `MainActivity.kt` y colores púrpura del template en `core/ui/theme/Color.kt` | revisora-arquitectura | al implementar la pantalla de clima | Día 1, Sesión 2–3 |
| P-03 | Agregar SQLCipher al configurar `core/database` (no después) | revisora-arquitectura | junto con Room+SQLCipher | Día 3, Sesión 3 |
| P-04 | `firestore.rules` versionado en la raíz del repo (RNF-06) | revisora-arquitectura | con la sincronización | Día 6, Sesión 3 |
| P-05 | Activar `isMinifyEnabled = true` (R8) en release para ofuscar nombres internos | auditora-seguridad H-3 | antes del APK v0.1.0-trl4 | Día 7 |
| P-06 | Revisar el manifest fusionado del APK: permisos que agregan Firebase/Retrofit (`INTERNET`, `ACCESS_NETWORK_STATE`) — confirmar que no aparece ninguno injustificado (RNF-04) | auditora-seguridad | antes del APK v0.1.0-trl4 | Día 7 |
| P-07 | Verificar que el repositorio remoto sea PRIVADO al crearlo: los docs versionados describen el propósito real y un repo público rompería el camuflaje (H-4) | auditora-seguridad | al crear el remoto | — |

## Resueltos

| # | Pendiente | Resolución |
|---|---|---|
| P-00 | `allowBackup="true"` permitiría que el auto-backup de Android copie la futura BD del doble fondo (H-1, severidad alta) | `allowBackup="false"` en el manifest — commit `2e4a0d6` |

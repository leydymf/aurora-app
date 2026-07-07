# Pendientes registrados por las auditorías

> Hallazgos no bloqueantes de los agentes de revisión (revisora-arquitectura y
> auditora-seguridad). Al cerrar cada uno, marcarlo y anotar el commit.
> Los resueltos se mueven a la sección final como evidencia para el informe.

## Abiertos

| # | Pendiente | Origen | Fecha límite | Sesión prevista |
|---|---|---|---|---|
| P-03 | Agregar SQLCipher al configurar `core/database` (no después). Incluye H-3: declarar exclusiones explícitas en `backup_rules.xml` y `data_extraction_rules.xml` (`database/`, `sharedpref/`) para no depender solo de `allowBackup=false` | revisora-arquitectura + auditora-seguridad H-3 | antes de crear la BD del doble fondo | Día 3, Sesión 3 |
| P-04 | `firestore.rules` versionado en la raíz del repo (RNF-06) | revisora-arquitectura | con la sincronización | Día 6, Sesión 3 |
| P-05 | Activar `isMinifyEnabled = true` (R8) en release para ofuscar nombres internos | auditora-seguridad H-3 | antes del APK v0.1.0-trl4 | Día 7 |
| P-06 | Revisar el manifest fusionado del APK: permisos que agregan Firebase/Retrofit (`INTERNET`, `ACCESS_NETWORK_STATE`) — confirmar que no aparece ninguno injustificado (RNF-04) | auditora-seguridad | antes del APK v0.1.0-trl4 | Día 7 |
| P-07 | Verificar que el repositorio remoto sea PRIVADO al crearlo: los docs versionados describen el propósito real y un repo público rompería el camuflaje (H-4) | auditora-seguridad | al crear el remoto | — |
| P-08 | Actualizar el criterio 2 de RF-01 en el xlsx fuente (`Aurora_Modelado_Requerimientos.xlsx`) con el refinamiento F1: "Aurora" solo como marca de clima. Nota provisional ya agregada en `docs/REQUERIMIENTOS.md` | verificadora-rf D1 | antes del cierre de Fase 3 | manual (usuaria) |
| P-09 | Crear el componente único de FLAG_SECURE en `core/security` ANTES del primer composable del doble fondo (RNF-02); validar con la directora que el ícono launcher (arte de `18-aurora-app-icon.svg`) no circule idéntico en material institucional de fundaciones (O1) | auditora-seguridad | antes de RF-12 / difusión | Día 2 |

## Resueltos

| # | Pendiente | Resolución |
|---|---|---|
| P-00 | `allowBackup="true"` permitiría que el auto-backup de Android copie la futura BD del doble fondo (H-1, severidad alta) | `allowBackup="false"` en el manifest — commit `2e4a0d6` |
| P-01 | Identidad de clima: `app_name` e ícono del template | RF-01: "Aurora" como marca de clima (decisión F1), ícono adaptativo del amanecer |
| P-02 | Quitar `Greeting`/`GreetingPreview` y colores púrpura del template | Reemplazados por `WeatherScreen` y paleta del amanecer (RF-01) |

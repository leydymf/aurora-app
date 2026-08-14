# Siembra del contenido en Firestore

Contenido administrable del módulo informativo (RF-21, RF-22, RF-23): tipos de
violencia, rutas de atención con sus entidades y líneas de atención; y el
cuestionario orientador (RF-27, RF-29; base del futuro panel RF-43). Los textos
de `content/*.json` son **BORRADOR A VALIDAR por la directora**; todo dato
marcado `[POR CONFIRMAR]` debe verificarse con fuentes oficiales antes de que
llegue a usuarias reales.

## Llave de servicio — reglas de seguridad

1. Consola de Firebase → ⚙️ *Configuración del proyecto* → *Cuentas de servicio*
   → **Generar nueva clave privada**.
2. Guardarla **FUERA del repositorio**, por ejemplo:

   ```bash
   mkdir -p ~/.config/aurora-seed
   mv ~/Downloads/<proyecto>-firebase-adminsdk-*.json ~/.config/aurora-seed/service-account.json
   chmod 600 ~/.config/aurora-seed/service-account.json
   ```

3. La llave da acceso administrativo total al proyecto:
   - **Nunca** copiarla dentro del repo ni adjuntarla a correos o chats.
     Barreras: la ruta canónica está fuera del repo, `.gitignore` veda
     `service-account*.json` y `*-adminsdk-*.json`, y `seed.mjs` aborta si la
     ruta de la llave cae dentro del repositorio.
   - Rotarla o revocarla desde la consola (IAM → Cuentas de servicio) si se
     sospecha exposición, y al terminar el piloto.

## Antes de la PRIMERA siembra (obligatorio)

1. **Publicar `firestore.rules`** (raíz del repo) en la consola: Firestore →
   *Reglas* → pegar y *Publicar*. Mientras el proyecto siga en «modo de
   prueba», el contenido es legible y **escribible** por cualquiera. El script
   pide confirmación de este paso (o `AURORA_RULES_PUBLISHED=1`).
2. Revisar qué documentos siguen con `[POR CONFIRMAR]`: el script los **omite
   por defecto**. `--borrador` los siembra de todas formas y solo debe usarse
   en un entorno de prueba, jamás en el proyecto que verán usuarias reales.

## Ejecutar la siembra

```bash
cd tools/seed
npm ci          # instala exactamente las versiones del package-lock.json
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
npm run seed    # agrega -- --borrador SOLO en un entorno de prueba
```

- El upsert es idempotente (`set` con `merge`): tras cada corrección de la
  directora, editar el JSON y volver a ejecutar.
- El script **no borra** documentos retirados de los JSON: si un contenido se
  retira, eliminar el documento a mano en la consola.
- Todo documento lleva `schemaVersion` (hoy `2` en `topics`/`routes` —
  `icon`/`accent` del ADR 2026-08-14 — y `1` en `agencies`/`lines`): la app
  ignora versiones que no conoce (ARQUITECTURA §6), así el panel puede
  evolucionar el esquema sin romper instalaciones viejas. Ojo: un APK anterior
  al soporte v2 descarta los documentos v2 — actualizar la app antes de
  re-sembrar.
- Usar `npm ci` (no `npm install`): el `package-lock.json` versionado fija las
  versiones exactas en la máquina que custodia la llave administrativa
  (auditoría D4 M-3). Riesgo residual aceptado: `npm audit` reporta un aviso
  *moderate* transitivo (`uuid` vía google-gax) cuyo único arreglo automático
  degrada firebase-admin a la serie 10; no aplica a este uso local sin
  entradas de terceros. Revisar al actualizar firebase-admin.

## Modelo de datos

| Colección | Documento | Campos |
|---|---|---|
| `topics` | tipo de violencia | `schemaVersion, order, icon, accent, title, summary, signals[], examples[]` |
| `routes` | sector de la ruta (Salud/Protección/Justicia) | `schemaVersion, order, icon, accent, title, summary, agencyIds[]` |
| `agencies` | entidad de atención | `schemaVersion, name, role, whenToUse, phones[], address?, hours?` |
| `lines` | línea de atención | `schemaVersion, order, name, number, description, hours?` |
| `forms` | configuración completa del cuestionario | `schemaVersion, version, title, intro, notice, scale[], questions[], levels[]` |

Los nombres de colección **y los IDs de documento** son deliberadamente
neutros/opacos (`topics/t01`, `agencies/a06`, `forms/f01`): ambos viajan en las
rutas de la API de Firestore y quedan en la caché local del dispositivo, así que
no deben sugerir el propósito real (RNF-05, auditoría D4 H-1). `routes` referencia
`agencies` por id; un id sin resolver simplemente no se muestra (RF-42).

### El cuestionario (`forms`)

A diferencia del resto del contenido, **todo el cuestionario vive en un solo
documento**: publicar es escribir un documento completo, de forma atómica, y el
futuro panel (RF-43) no puede dejar preguntas nuevas conviviendo con niveles
viejos. La app descarta cualquier documento incompleto (sin preguntas, sin
niveles, sin escala o sin aviso) — el espejo del criterio 2 de RF-43. Si hay
varios documentos válidos, rige el de mayor `version` (número de publicación).

Estructura del documento (`schemaVersion` hoy `1`):

- `scale[]` — la escala de respuesta compartida por todas las preguntas
  (decisión F6 de A2-6: «Nunca / Alguna vez / Con frecuencia»):
  `{ id, order, label, value }`.
- `questions[]` — `{ id, order, text, weight, alertLevelId? }`. El puntaje de
  una respuesta es `weight × value`. `alertLevelId` marca una **pregunta
  crítica**: elegir la opción de mayor `value` garantiza al menos ese nivel,
  sin importar el total (así tratan los instrumentos de valoración las
  amenazas y la escalada).
- `levels[]` — `{ id, order, minScore, title, summary, advice[], routeIds[] }`.
  El nivel resultante es el de mayor `minScore ≤ puntaje total`; a igual
  `minScore` gana el de mayor `order` (ante configuración ambigua se
  sobre-orienta, nunca al revés). `routeIds` referencia `routes` (r01–r03)
  igual que `routes` referencia `agencies`.
- `notice` — el aviso de alcance orientador (RF-30), editable sin tocar la app.

El cuestionario semilla lleva un campo `reviewNote` con `[POR CONFIRMAR …]`:
la compuerta M-2 del script lo **omite por defecto** hasta que la directora
valide preguntas, pesos, umbrales y pisos críticos (ver P-38 en
`docs/PENDIENTES.md`). Para desarrollar, sembrarlo con `--borrador` SOLO en el
entorno de prueba; tras la validación, retirar `reviewNote` y re-ejecutar.

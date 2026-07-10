# Siembra del contenido en Firestore

Contenido administrable del módulo informativo (RF-21, RF-22, RF-23): tipos de
violencia, rutas de atención con sus entidades y líneas de atención. Los textos
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
- Todo documento lleva `schemaVersion` (hoy `1`): la app ignora versiones que
  no conoce (ARQUITECTURA §6), así el futuro panel puede evolucionar el esquema
  sin romper instalaciones viejas.
- Usar `npm ci` (no `npm install`): el `package-lock.json` versionado fija las
  versiones exactas en la máquina que custodia la llave administrativa
  (auditoría D4 M-3). Riesgo residual aceptado: `npm audit` reporta un aviso
  *moderate* transitivo (`uuid` vía google-gax) cuyo único arreglo automático
  degrada firebase-admin a la serie 10; no aplica a este uso local sin
  entradas de terceros. Revisar al actualizar firebase-admin.

## Modelo de datos

| Colección | Documento | Campos |
|---|---|---|
| `topics` | tipo de violencia | `schemaVersion, order, title, summary, signals[], examples[]` |
| `routes` | sector de la ruta (Salud/Protección/Justicia) | `schemaVersion, order, title, summary, agencyIds[]` |
| `agencies` | entidad de atención | `schemaVersion, name, role, whenToUse, phones[], address?, hours?` |
| `lines` | línea de atención | `schemaVersion, order, name, number, description, hours?` |

Los nombres de colección **y los IDs de documento** son deliberadamente
neutros/opacos (`topics/t01`, `agencies/a06`): ambos viajan en las rutas de la
API de Firestore y quedan en la caché local del dispositivo, así que no deben
sugerir el propósito real (RNF-05, auditoría D4 H-1). `routes` referencia
`agencies` por id; un id sin resolver simplemente no se muestra (RF-42).

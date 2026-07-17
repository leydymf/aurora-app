# Aurora Admin — Panel de administración

Panel web **interno** para gestionar el contenido que consume la app Aurora:
tipos de violencia (RF-41), rutas/entidades/líneas (RF-42), cuestionario (RF-43)
y la publicación reflejada en la app (RF-44). Autentica a la administradora
(RF-40) y **nunca** accede a datos de usuarias (RNF-06).

Vive en `admin/` dentro del monorepo, pero **no forma parte del APK**: Gradle no
lo compila. Stack: Vite + React + TypeScript estricto + SDK web de Firebase, sin
backend propio (A2-1).

> Estado: **Fase B2** — CRUD completo de los cuatro recursos con flujo
> borrador → publicado. Falta B3 (verificación del ciclo en la app, RF-24/25).

## 1. Requisitos

- Node 20+ (probado con Node 22).
- Acceso al proyecto Firebase `aurora-31c37` (el mismo de la app).
- **Email/Password** habilitado en Firebase Console → Authentication → Sign-in
  method (la app ya usa Auth, así que suele estar activo).

## 2. Configurar y correr

```bash
cd admin
npm install

# Config del SDK web (NO secreta; ver nota en .env.example):
cp .env.example .env
# Rellena VITE_FIREBASE_* con los valores de:
#   Firebase Console → Configuración del proyecto → Tus apps → App web → SDK.
#   Si no hay app web en el proyecto, crea una (icono </>). No altera el APK.

npm run dev      # http://localhost:5173
```

Scripts:

| Comando          | Qué hace                                              |
| ---------------- | ----------------------------------------------------- |
| `npm run dev`    | Servidor de desarrollo (Vite)                         |
| `npm run build`  | Build de producción (`tsc -b && vite build`)          |
| `npm run lint`   | ESLint + `prettier --check`                           |
| `npm run format` | `prettier --write`                                    |
| `npm run test`   | Vitest: validaciones críticas (teléfonos, coherencia) |

## 3. Crear la cuenta de administradora (RF-40)

**No hay registro público.** La cuenta admin se crea con un script aparte
(`tools/admin/`, Node + firebase-admin) que además le asigna el custom claim
`admin=true`. Ver `../tools/admin/README.md`. Resumen:

```bash
cd ../tools/admin
npm install
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
node create-admin.mjs --email admin@ejemplo.org --password 'una-contraseña-fuerte'
```

La llave de servicio **nunca** se versiona (misma política que `tools/seed`).

### Cómo probar el login

1. **Admin válida:** entra en el panel con el correo/contraseña de la cuenta que
   creaste con el script. El panel refresca el token, lee el claim `admin=true`
   y muestra la navegación de los 4 recursos.
2. **Cuenta sin rol (rechazo):** crea en Firebase Console → Authentication un
   usuario cualquiera **sin** pasar por el script (o usa una cuenta de prueba de
   la app). Intenta entrar: el panel autentica, detecta que falta el claim
   `admin`, cierra la sesión de inmediato y muestra el mismo mensaje genérico
   («No se pudo iniciar sesión…»). Nunca llega al layout. El mensaje es idéntico
   al de credenciales inválidas, para no revelar si la cuenta existe.

## 4. Reglas de Firestore (RNF-06)

Las reglas viven en `../firestore.rules` (raíz del repo, compartidas con la app).
El contenido pasó de `write: false` a `write: if request.auth.token.admin == true`;
`/users/**` **no cambió** y sigue accesible solo por su dueña — un admin no puede
leer datos de usuarias (RNF-06). Cómo verificarlo y cómo desplegarlas: ver
`../firestore/README.md`.

## 5. Arquitectura del panel

```
src/
  firebase/config.ts        # init de Firebase (Auth + Firestore) desde .env
  auth/                     # RF-40: AuthProvider (único estado global) + login
  lib/
    drafts.ts               # flujo borrador → publicado (colección /drafts)
    useEntries.ts           # carga vivo+borrador por recurso (hook local)
    useResourceEditor.ts    # máquina de estados del editor (guardar/publicar/eliminar)
    ids.ts                  # ids opacos t01/r01/a01/l01… (auditoría D4 H-1)
    phone.ts                # validación dura de teléfonos colombianos (RF-42)
    validation.ts           # marcador [POR CONFIRMAR] (compuerta M-2) y tipos
    parse.ts                # lectura tolerante de documentos (espejo de los DTO)
  components/               # presentacionales: Layout, StatusBadge, listas, botonera
  resources/
    content/                # RF-41: topics (editor + vista previa) + tests
    routes/                 # RF-42: rutas/entidades/líneas en pestañas + tests
    questionnaire/          # RF-43: formSchema + coherence (validaciones) + tests
    publish/                # RF-44: estado global y publicación pendiente
```

Reglas de código en `CLAUDE.md`: TypeScript estricto, un componente por recurso,
sin estado global salvo autenticación, jamás datos de usuarias.

### Flujo borrador → publicado (RF-44)

La app lee las colecciones vivas (`topics/routes/agencies/lines/forms`)
**completas y sin filtros**, así que los borradores viven en la colección
`drafts` (solo rol admin; la app jamás la consulta). «Publicar» copia el campo
`data` del borrador **tal cual** al documento vivo: en las colecciones vivas
solo existe lo publicado y los documentos conservan exactamente el esquema que
la app consume (ningún campo nuevo — `schemaVersion` sigue en 1). Publicar
exige pasar la validación dura de cada recurso; guardar borrador es libre.

## 6. Verificación end-to-end (panel → app)

Camino recomendado: proyecto real (la app no se toca). Pasos:

1. **Reglas**: publica el `../firestore.rules` actualizado (Firebase Console →
   Firestore → Reglas) — añade la colección `drafts` solo-admin. Verifícalo en
   el Rules Playground (una cuenta sin claim no lee `drafts/x`, RNF-06) o, en
   una máquina con Firebase CLI, corre
   `firebase emulators:exec --only firestore "node --test firestore/rules.test.mjs"`
   y registra CP-37.
2. **Panel**: `npm run dev`, entra con la cuenta admin.
3. **RF-41**: edita el título de un tipo (t01) y **guarda borrador**. En la app
   (emulador, cuenta de prueba), abre Información: NO cambia. Vuelve al panel,
   **publica**, reabre la sección en la app: cambia (RF-44 criterio 1).
4. **RF-42**: cambia un teléfono de la Policía (a03), publica y verifica el
   detalle de la entidad en la app. Intenta eliminar una entidad referenciada
   (a02): el panel bloquea y lista las rutas (criterio 2). Crea una entidad
   nueva, agrégala a una ruta e intenta publicar la ruta ANTES que la entidad:
   bloqueado; publica en orden entidad → ruta y verifica en la app.
5. **RF-43**: cambia un umbral o el texto de una pregunta, publica (la versión
   sube automáticamente: la app toma el form de mayor `version`) y responde el
   cuestionario en la app. Intenta quitar todos los niveles o poner un umbral
   mayor al puntaje máximo: no se puede publicar (criterio 2).
6. **RNF-06**: confirma que `/users/**` sigue denegado para la admin (CP-37,
   `../firestore/README.md`).

## 7. Despliegue (hosting)

Fuera del alcance de la Fase B. Cuando se decida, `npm run build` genera `dist/`
estático (Firebase Hosting u otro). El panel **no** debe servirse desde el mismo
dominio ni identidad visible que sugiera relación con la app de clima.

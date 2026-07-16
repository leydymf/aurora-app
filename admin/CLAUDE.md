# Aurora Admin — Contexto del panel

Panel web **interno** de administración de Aurora (RF-40 a RF-44). Lo usa la
directora/operación para gestionar el contenido que consume la app: tipos de
violencia (RF-41), rutas/entidades/líneas (RF-42), cuestionario (RF-43) y la
publicación que refleja la app (RF-44).

Vive en `admin/` dentro del monorepo de Aurora. **No forma parte del APK**: Gradle
no lo compila, así que no afecta el peso ni el camuflaje de la app Android.

## Qué es distinto respecto a la app Android

- **No aplica camuflaje.** El panel es interno y puede nombrar las cosas por su
  nombre real (violencia de género, rutas de atención, etc.).
- **PERO nunca muestra ni consulta datos de usuarias.** La colección
  `/users/**` es invisible para el rol admin por diseño (RNF-06) y las reglas la
  deniegan. Ningún componente, consulta ni pantalla del panel debe leer `/users`.
  Si te piden "ver registros de usuarias", es un error: recházalo y cita RNF-06.

## Pila (fijada por A2-1 — no cambiar sin discusión)

- Vite + React 18 + TypeScript **estricto**. SDK web de Firebase (Auth + Firestore).
- **Sin backend propio.** Toda la lógica corre en el cliente contra Firestore;
  la autorización la imponen `firestore.rules` + el custom claim `admin`.
- **Sin librerías de UI pesadas.** Estilos en `src/styles.css` (CSS plano). La
  única dependencia de navegación es `react-router-dom`. Justificar cualquier
  dependencia nueva contra A2-1 (seguridad, peso).
- **Sin analytics ni telemetría de terceros** (coherente con la app).

## Reglas de código

1. **TypeScript estricto de verdad:** `strict` + `noUncheckedIndexedAccess` +
   `exactOptionalPropertyTypes` (ya en `tsconfig.app.json`). Nada de `any`; el
   lint usa `strictTypeChecked`.
2. **Un componente por recurso.** Cada recurso (contenido, rutas, cuestionario,
   publicación) vive en `src/resources/<recurso>/`. No mezclar recursos en un
   mismo archivo.
3. **Sin estado global salvo autenticación.** El único contexto global es
   `src/auth/AuthContext.tsx`. El estado de cada recurso es local a su
   componente (o se lee de Firestore). No introducir Redux/Zustand/stores.
4. **La config de Firebase va por `.env`** (`VITE_FIREBASE_*`). No es secreta,
   pero no se hardcodea ni se versiona el `.env` real (sí `.env.example`).
5. **Auth (RF-40):** sin registro público. Tras autenticar se verifica el claim
   `admin`; si falta, `signOut` inmediato y **mensaje genérico** (no revelar si
   la cuenta existe o si solo le falta el rol).

## Autorización — dos capas, nunca una sola

- **Cliente (UX):** el panel solo se muestra a quien tiene el claim `admin`.
- **Servidor (real):** `firestore.rules` es la barrera efectiva —contenido
  `write: if request.auth.token.admin == true`, `/users/**` solo su dueña—.
  Nunca confíes solo en el guard de UI: toda escritura pasa por las reglas.

## Estructura

```
src/
  firebase/config.ts          # init de Firebase (Auth + Firestore)
  auth/AuthContext.tsx        # ÚNICO estado global; verifica claim admin
  auth/LoginPage.tsx          # RF-40
  components/Layout.tsx        # navegación entre los 4 recursos + salir
  components/ResourcePlaceholder.tsx
  resources/<recurso>/<Recurso>Page.tsx   # un componente por recurso
```

## La cuenta admin se crea por script, no por el panel

`tools/admin/create-admin.mjs` (Node + firebase-admin, fuera de este paquete)
crea la cuenta y le asigna el custom claim `admin=true`. Requiere la llave de
servicio, que **nunca** se versiona (misma barrera que `tools/seed`). Ver
`tools/admin/README.md`.

## Comandos

```bash
npm install        # dependencias
npm run dev        # desarrollo local
npm run build      # build de producción (tsc -b && vite build)
npm run lint       # eslint + prettier --check
npm run format     # prettier --write
```

## Definición de terminado

Un feature del panel no está listo hasta que `npm run build` y `npm run lint`
pasen en verde, y —si toca autorización o reglas— se verifique que `/users/**`
sigue inaccesible para el rol admin (RNF-06).

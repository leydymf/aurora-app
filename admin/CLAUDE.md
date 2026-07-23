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
  lib/                        # infraestructura genérica (NUNCA lógica de un recurso):
    drafts.ts                 #   flujo borrador→publicado sobre la colección /drafts
    useEntries.ts             #   hook local de carga vivo+borrador (no es estado global)
    useResourceEditor.ts      #   máquina de estados del editor (guardar/publicar/eliminar)
    ids.ts / phone.ts / validation.ts / parse.ts / errors.ts
  components/                 # presentacionales compartidos (Layout, StatusBadge,
                              #   StringListField, EditorActions, EditorStatus)
  resources/<recurso>/        # un recurso por carpeta: página + esquema espejo + tests
```

## Flujo borrador → publicado (RF-44) — regla de oro

La app lee las colecciones vivas completas y SIN filtros: un borrador jamás
puede escribirse en ellas. Los borradores viven en `/drafts` (solo admin) y
«Publicar» copia `data` LITERAL al documento vivo: ningún campo nuevo
(`status`, fechas, etc.) cae en las colecciones que la app consume, y
`schemaVersion` viaja tal como se editó, sin forzarse (hoy: 1 en el contenido
—`topics`/`routes`/`agencies`/`lines`— y 2 en el cuestionario `forms`, que pasó
al instrumento validado v2; ver ADR 2026-07-22 en `docs/ARQUITECTURA.md`). Todo
esquema del panel es espejo exacto de los DTO de la app (`ContentDtos.kt`,
`FormDtos.kt`); cambiar un campo es un cambio de esquema y exige decisión
explícita del proyecto. Publicar pasa
SIEMPRE por la validación dura del recurso (incluida la compuerta
`[POR CONFIRMAR]`, M-2); guardar borrador es libre.

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
npm run test       # vitest (validaciones críticas: teléfonos, coherencia del cuestionario)
```

## Definición de terminado

Un feature del panel no está listo hasta que `npm run build`, `npm run lint` y
`npm run test` pasen en verde, y —si toca autorización o reglas— se verifique
que `/users/**` sigue inaccesible para el rol admin (RNF-06).

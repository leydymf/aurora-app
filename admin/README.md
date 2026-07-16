# Aurora Admin — Panel de administración

Panel web **interno** para gestionar el contenido que consume la app Aurora:
tipos de violencia (RF-41), rutas/entidades/líneas (RF-42), cuestionario (RF-43)
y la publicación reflejada en la app (RF-44). Autentica a la administradora
(RF-40) y **nunca** accede a datos de usuarias (RNF-06).

Vive en `admin/` dentro del monorepo, pero **no forma parte del APK**: Gradle no
lo compila. Stack: Vite + React + TypeScript estricto + SDK web de Firebase, sin
backend propio (A2-1).

> Estado: **Fase B1** — fundación, autenticación y layout. El CRUD de cada
> recurso llega en la Fase B2.

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

| Comando          | Qué hace                                    |
| ---------------- | ------------------------------------------- |
| `npm run dev`    | Servidor de desarrollo (Vite)               |
| `npm run build`  | Build de producción (`tsc -b && vite build`)|
| `npm run lint`   | ESLint + `prettier --check`                 |
| `npm run format` | `prettier --write`                          |

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
  auth/
    auth-context.ts         # definición del contexto (tipo + createContext)
    AuthContext.tsx         # AuthProvider: ÚNICO estado global; verifica claim
    useAuth.ts              # hook de acceso al contexto
    LoginPage.tsx           # RF-40
  components/
    Layout.tsx              # navegación entre los 4 recursos + cerrar sesión
    ResourcePlaceholder.tsx
  resources/<recurso>/<Recurso>Page.tsx   # un componente por recurso
```

Reglas de código en `CLAUDE.md`: TypeScript estricto, un componente por recurso,
sin estado global salvo autenticación, jamás datos de usuarias.

## 6. Despliegue (hosting)

Fuera del alcance de B1. Cuando se decida, `npm run build` genera `dist/`
estático (Firebase Hosting u otro). El panel **no** debe servirse desde el mismo
dominio ni identidad visible que sugiera relación con la app de clima.

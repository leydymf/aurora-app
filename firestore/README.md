# Pruebas de `firestore.rules` — CP-37 (manual)

Las reglas (`firestore.rules`, raíz del repo) protegen dos mundos (RNF-06, RF-37):

1. **Contenido administrable** (`topics`/`routes`/`agencies`/`lines`/`forms`): solo
   lectura para usuarias autenticadas; **escritura solo con el custom claim
   `admin==true`** (panel web, RF-40/RF-41–44). La siembra por firebase-admin
   (`tools/seed`) omite las reglas por diseño.
2. **Datos de la usuaria** (`/users/{uid}/**`): lectura y escritura **exclusivas**
   de su propia cuenta. Otro `uid` —incluido un rol administrativo con
   `admin==true`— queda denegado. El claim admin **no** otorga ningún acceso a
   `/users` (RNF-06).

## Cómo desplegar las reglas (tras editar `firestore.rules`)

En este entorno no hay Firebase CLI; el despliegue es manual por consola:

1. Firebase Console → proyecto `aurora-31c37` → **Firestore Database** → pestaña
   **Reglas**.
2. Pegar el contenido completo de `firestore.rules` (raíz del repo).
3. **Revisar el diff** que muestra la consola y pulsar **Publicar**.
4. Verificar con el *Rules playground* (tabla de abajo) al menos los casos del
   claim `admin`.

> Alternativa con CLI (si se instala): `firebase deploy --only firestore:rules`
> desde la raíz, con un `firebase.json` que apunte a `firestore.rules`.

## Por qué es una CP manual

En esta fase el entorno de desarrollo **no tiene Firebase CLI ni el emulador de
Firestore instalados**, así que estas pruebas no corren en el build de Gradle. Las
reglas de producción ya están **publicadas y verificadas en la consola** por la
usuaria (P-04, compuerta M-1 de la siembra, 2026-07-13). `rules.test.mjs` queda
listo para ejecutarse en cuanto el emulador esté disponible.

## Cómo ejecutarlas (cuando haya emulador)

```bash
npm install --no-save firebase @firebase/rules-unit-testing
firebase emulators:exec --only firestore "node --test firestore/rules.test.mjs"
```

## Verificación manual en la consola (alternativa sin emulador)

En Firebase Console → Firestore → **Rules playground**:

Para los casos con claim, en el playground activa **Autenticado** y agrega el
custom claim `admin` con valor `true` (botón *Agregar* en la sección de token).

| Caso | Ubicación | Auth | Esperado |
|---|---|---|---|
| Contenido, lectura anónima | `topics/t01` get | sin auth | **denegado** |
| Contenido, lectura autenticada | `topics/t01` get | uid `ana` | permitido |
| Contenido, escritura sin claim | `topics/t01` set | uid `ana` (sin claim) | **denegado** |
| Contenido, escritura admin (RF-40) | `topics/t01` set | uid `admin` + claim `admin=true` | permitido |
| Registro propio | `users/ana/entries/7` get/set/delete | uid `ana` | permitido |
| Registro ajeno | `users/ana/entries/7` get/set | uid `eva` | **denegado** |
| Registro, admin (RNF-06) | `users/ana/entries/7` get/set | uid `admin` + claim `admin=true` | **denegado** |
| Registro, anónima | `users/ana/entries/7` get | sin auth | **denegado** |
| Ruta desconocida | `secretos/x` get/set | uid `ana` | **denegado** |

El caso **«Registro, admin»** es la prueba explícita de RNF-06: aun con
`admin=true`, un rol administrativo no lee ni escribe datos de otra usuaria.
Estos casos también están cubiertos por `rules.test.mjs` (para cuando haya
emulador): *«la administradora sí escribe el contenido»* y *«la administradora
NO puede leer ni escribir datos de usuarias (RNF-06)»*.

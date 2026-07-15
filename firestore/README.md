# Pruebas de `firestore.rules` — CP-37 (manual)

Las reglas (`firestore.rules`, raíz del repo) protegen dos mundos (RNF-06, RF-37):

1. **Contenido administrable** (`topics`/`routes`/`agencies`/`lines`/`forms`): solo
   lectura para usuarias autenticadas; escritura solo por credenciales admin (la
   siembra, que no pasa por reglas).
2. **Datos de la usuaria** (`/users/{uid}/**`): lectura y escritura **exclusivas**
   de su propia cuenta. Otro `uid` —incluido un rol administrativo— queda denegado.

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

| Caso | Ubicación | Auth | Esperado |
|---|---|---|---|
| Contenido, lectura anónima | `topics/t01` get | sin auth | **denegado** |
| Contenido, lectura autenticada | `topics/t01` get | uid `ana` | permitido |
| Contenido, escritura | `topics/t01` set | uid `ana` | **denegado** |
| Registro propio | `users/ana/entries/7` get/set/delete | uid `ana` | permitido |
| Registro ajeno | `users/ana/entries/7` get/set | uid `eva` | **denegado** |
| Registro, anónima | `users/ana/entries/7` get | sin auth | **denegado** |
| Ruta desconocida | `secretos/x` get/set | uid `ana` | **denegado** |

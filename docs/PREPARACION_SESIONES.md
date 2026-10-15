# Aurora — Preparación de los teléfonos para las sesiones de evaluación

> Para la evaluación heurística (3 a 5 evaluadoras) y las sesiones SUS (10 a 12
> estudiantes), en uno o dos teléfonos Android propios.
> Última revisión: 2026-10-14.

Cada participante debe poder hacer el **primer uso completo**: activación con la
clave en el buscador de ciudades → consentimiento → crear cuenta → clave personal
→ tutorial. Eso obliga a dejar el teléfono como recién instalado entre una
persona y la siguiente, porque en cuanto existe una clave personal la clave de
activación deja de abrir (§3).

---

## 1. Qué build usar: debug de `v0.3.1-trl4`

**La propuesta es correcta y es, hoy, la única opción viable.** El motivo y el
costo, dichos completos:

- La build **release no puede activarse en una instalación limpia**: ninguna build
  de release siembra la clave de activación, porque `app/src/release/` no existe
  (P-111, hermano de P-20). Sobre un teléfono recién instalado con release, la
  barra de búsqueda nunca abriría el espacio protegido y el primer uso sería
  imposible de ejercitar.
- La build **debug sí la siembra**, en el primer arranque, si la ranura está vacía
  (`DebugSecretCodeSeeder`). Es exactamente lo que hace falta.

El costo de usar debug está registrado en P-65 y conviene tenerlo a la vista: la
recomendación general del proyecto es «piloto solo con release». Para estas
sesiones se acepta la build debug porque los teléfonos son propios, no salen de la
sesión y los datos que se crean son de prueba. **Para el piloto con usuarias
reales la decisión no vale**: antes hay que cerrar P-111 dándole al release una
vía de activación.

### 1.1 La interfaz de debug es idéntica a la de release

Lo verifiqué contra el código, no de memoria. **No hay una sola diferencia visible
en pantalla**, y no por disciplina, sino porque no existe el mecanismo que la
produciría:

| Qué podría cambiar | Estado real |
|---|---|
| Nombre de la app | Idéntico: `@string/app_name` en `app/src/main/res/values/strings.xml`, sin sobrescritura en debug |
| Ícono | Idéntico: `@mipmap/ic_launcher` / `ic_launcher_round` |
| Nombre de paquete | Idéntico: no hay `applicationIdSuffix` |
| Versión visible | Idéntica: no hay `versionNameSuffix` |
| Textos, colores, tipografías, iconos | Idénticos: `app/src/debug/` **no tiene carpeta `res/`**, así que no sobrescribe ningún recurso |
| Pantallas o rótulos condicionados al tipo de build | **No existen**: `BuildConfig` no se referencia ni una vez en el código de la aplicación, así que ninguna rama del código distingue debug de release |
| Banderas de depuración en pantalla, marcas de agua, menús ocultos | No hay ninguno |
| Pantallas con entrada propia en el lanzador | Idénticas: `MainActivity` es la única con `intent-filter` de lanzador en las dos builds. El manifiesto de debug retira la `PreviewActivity` exportada que arrastra la biblioteca de previsualizaciones de Compose; el release nunca la tuvo |

Es decir: lo que vean las participantes es lo que vería una usuaria con el
release. Las capturas y los hallazgos de usabilidad son válidos tal cual.

**Precisión sobre el manifiesto, para que la afirmación sea exacta.** Diferencié
los dos manifiestos **fusionados** ya empaquetados y las diferencias son
exactamente dos:

1. `android:debuggable="true"` en debug (ver §1.2).
2. Una actividad de más en debug: `<activity android:name="androidx.activity.ComponentActivity" android:exported="true" />`,
   que fusiona `ui-test-manifest`, la otra dependencia `debugImplementation`, y
   que existe solo para las pruebas instrumentadas de Compose.

Esa actividad **no se ve**: no tiene `intent-filter`, ni icono, ni rótulo, ni
entrada en el lanzador, y no abre ninguna pantalla de Aurora. **La validez de las
sesiones no está en cuestión.** Pero es un componente exportado y sin permiso que
cualquier app del teléfono puede lanzar, y que el release no tiene: una razón más
para no activar la depuración USB (§1.2). Queda registrado como **P-112** en
`docs/PENDIENTES.md`, hermano de P-92 y con la misma prioridad (antes del
piloto): evaluar retirarla con `tools:node="remove"` en
`app/src/debug/AndroidManifest.xml` —como ya se hace con la `PreviewActivity`— o
mover `ui-test-manifest` a `androidTestImplementation`, y fijar la retirada en
`ManifestMinimizationTest`.

(Permisos, `label`, `icon`, `theme` y todo lo demás del manifiesto fusionado son
idénticos en las dos builds. Las actividades exportadas que aporta Firebase Auth
—`genericidp://` y `recaptcha://`— están en **ambas**, y son P-92, no una
diferencia de debug.)

### 1.2 Lo que sí cambia, y nada de eso se ve en pantalla

| Diferencia | Dónde se nota |
|---|---|
| **Siembra de la clave de activación** | Solo en debug. Es la diferencia que hace posible la sesión |
| **Sin R8**: la build debug no ofusca ni reduce | Tamaño del APK y arranque: el release arranca en 228–265 ms en un emulador Pixel 2 / API 30 (P-80); el debug arranca algo más lento. En un teléfono real la diferencia es de décimas, pero **si alguien comenta la velocidad, hay que anotar que se midió sobre debug** |
| **Trazas en logcat**: la biblioteca de PDF escribe avisos técnicos al exportar (fuentes y estructura de los PDF anexados; nunca contenido ni contraseñas) | Solo con `adb logcat` conectado. La regla que las silencia solo actúa con R8 (P-65) |
| **Identificadores del sistema visual legibles en el DEX** | Solo decompilando el APK (P-65) |
| **`android:debuggable="true"`**, que el plugin de Android inyecta en toda build debug | Permite a quien tenga **depuración USB activada y el teléfono en la mano** copiar los archivos privados de la app. Están cifrados y las llaves del Keystore no son exportables, pero es una superficie que el release no tiene. Mitigación: **no activar la depuración USB en los teléfonos de sesión** (no hace falta: el APK se instala desde el propio teléfono, §3) |
| **Una actividad exportada de más**, que fusiona `ui-test-manifest` | No se ve en pantalla (sin `intent-filter`, sin icono, sin rótulo). Detalle y pendiente en §1.1 |
| **Sin guardián de artefacto**: `verificarCamuflajeDex` está encadenado solo a `assembleRelease` y `bundleRelease`, y juzga únicamente el APK de release | El APK de las sesiones **no pasa por él**. Lo que sí corre son los guardianes de fuentes: `CamouflageResourcesTest` (barre `src/main`) y `NoTracesGuardTest` (barre `src/main` **y** `src/debug`). Aceptable aquí porque los teléfonos son propios y no salen de la sesión; otro motivo por el que el piloto debe ser release (P-65) |

### 1.3 Qué compilar exactamente

Desde la etiqueta `v0.3.1-trl4` solo hay commits de documentación: el diff de
`app/` y `admin/` contra `main` está vacío. Se puede compilar desde `main` y es el
mismo código.

```bash
cd ~/dev/unab/aurora
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

(Si se prefiere compilar la etiqueta exacta: `git switch --detach v0.3.1-trl4`,
compilar, y volver con `git switch main`.)

---

## 2. Dónde dejar el APK

El APK que produce Gradle vive en `app/build/outputs/apk/debug/app-debug.apk`,
dentro de `build/`, que `.gitignore` excluye y que cualquier `clean` borra. **No
sirve como copia de trabajo.**

**En el computador**, copiarlo fuera del repositorio con un nombre neutro y
reconocible:

```bash
mkdir -p ~/aurora-sesiones
cp app/build/outputs/apk/debug/app-debug.apk ~/aurora-sesiones/aurora-clima-0.3.1.apk
shasum -a 256 ~/aurora-sesiones/aurora-clima-0.3.1.apk   # anotar: identifica el APK de las sesiones
```

El nombre del archivo es parte del camuflaje: `aurora-clima-0.3.1.apk` es
coherente con una aplicación de clima. No usar nombres como `aurora-debug.apk` ni
nada que mencione pruebas o el propósito real, porque el archivo queda a la vista
en la carpeta de descargas del teléfono.

**En el teléfono**, dejarlo en **Descargas**, que es almacenamiento compartido:
sobrevive a desinstalar y reinstalar la aplicación, así que una sola copia sirve
para todas las sesiones.

```bash
# con el teléfono conectado por USB (solo esta vez, para pasar el archivo):
adb push ~/aurora-sesiones/aurora-clima-0.3.1.apk /sdcard/Download/
```

Sin cable: pasarlo por la nube o por un cable de datos como archivo normal y
guardarlo en Descargas.

**No dejar el APK dentro del repositorio** ni adjuntarlo a los entregables: el
repositorio es público.

> **Falta una barrera mecánica.** `.gitignore` ignora `app/build/`, así que el APK
> que produce Gradle no corre peligro, pero **no veda `*.apk` ni `*.aab` por
> patrón**: si alguien copia un APK al árbol, nada impide commitearlo. Antes de
> empezar las sesiones, agregar estas dos líneas a `.gitignore`:
>
> ```gitignore
> # Artefactos compilados — nunca versionar (el repositorio es público)
> *.apk
> *.aab
> ```

---

## 3. Dejar la app como recién instalada entre una sesión y otra

### 3.1 Por qué es obligatorio, no una formalidad

`ValidateSecretCodeUseCase` consulta **primero** la ranura de clave personal. En
cuanto una participante crea la suya, la clave de activación **ya no abre nada**:
la siguiente persona no podría hacer el primer uso. Además hay que borrar la
cuenta, la clave y los registros de la persona anterior.

### 3.2 Procedimiento desde el propio teléfono, sin computador

1. **Desinstalar.** Mantener pulsado el ícono de Aurora → *Desinstalar* (o
   *Ajustes → Aplicaciones → Aurora → Desinstalar*). Confirmar.
2. **Reinstalar.** Abrir *Archivos* (o *Mis archivos*) → **Descargas** → tocar
   `aurora-clima-0.3.1.apk` → *Instalar*.
3. **Abrir la aplicación una vez.** El primer arranque es el que repone la clave
   de activación. Comprobar que la fachada de clima carga.
4. **Cerrar la aplicación** y dejar el teléfono en la pantalla de inicio, listo
   para la siguiente participante.

Dos avisos que ahorran un mal momento:

- La **primera vez** que se instale desde Archivos, Android pedirá permiso para
  *Instalar aplicaciones desconocidas* para esa app. Se concede una sola vez y
  queda.
- Play Protect puede mostrar un aviso de aplicación no verificada (el APK está
  firmado con el certificado de depuración, P-91). Hay que elegir *Instalar de
  todas formas* y, si ofrece **enviar la aplicación a Google para analizarla,
  rechazarlo**. Conviene además desactivar *Play Protect → Mejorar la detección de
  apps dañinas* en los teléfonos de sesión y silenciar las notificaciones durante
  la sesión: Play Protect reescanea cada cierto tiempo y puede sacar un aviso que
  nombre «Aurora» como app no verificada **en mitad de una tarea**, delante de la
  participante. En este prototipo el impacto es nulo porque el camuflaje ya está
  gastado (P-108), pero en un despliegue real subir el APK a un tercero no sería
  aceptable.
- **Por eso conviene hacer la reinstalación antes de que llegue la
  participante**, no delante de ella: esos avisos no son parte de lo que se evalúa
  y contaminan la primera impresión.

Tiempo real del ciclo: dos o tres minutos. Con **dos teléfonos** se alterna —
mientras una persona usa el teléfono A, el B se reinstala—, y no se pierde tiempo
entre sesiones.

### 3.3 Qué borra la desinstalación (y qué no)

Desinstalar borra **todo el estado local**, que es exactamente lo que hace falta:

- Las preferencias privadas, incluido el hash de la clave personal
  (`search_index`) y los sobres cifrados de la base, el respaldo y las
  credenciales (`store_meta`, `backup_meta`, `account_meta`).
- Las bases cifradas (`journal_store.db`, `guidance_store.db`) y la caché de
  clima.
- Los adjuntos sellados de `files/media_store/` y las cachés.
- **Los alias del Android Keystore**, que se destruyen con la aplicación.
- La caché local de Firestore.

Y no se restaura nada al reinstalar: el manifiesto declara
`android:allowBackup="false"`, con exclusiones explícitas de bases y
preferencias.

**Lo que NO borra:**

- **La cuenta en Firebase y sus documentos en la nube.** Eso se limpia al final de
  todas las sesiones, con el script de la §5. Como cada participante usa su propio
  correo (§4), las cuentas que queden vivas no estorban a las siguientes sesiones.
- **Los PDF que la participante haya guardado con «Exportar».** Salen del
  almacenamiento privado de la aplicación: el selector del sistema deja que ella
  elija la carpeta, normalmente **Descargas**, con el nombre neutro
  `documento_AAAA-MM-DD.pdf`. Lo mismo con cualquier copia que haya salido por
  «Compartir» hacia otra aplicación. La desinstalación no los toca, y contienen
  los registros de esa participante —cifrados, pero abiertos con la contraseña de
  exportación que ella misma puso—. **Si el guión llega a la tarea de exportar,
  revisar Descargas y borrar los `documento_*.pdf` antes de la siguiente
  participante.**

### 3.4 Con computador, si está a mano

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
adb uninstall com.auroraclima.app
adb install -r ~/aurora-sesiones/aurora-clima-0.3.1.apk
adb shell monkey -p com.auroraclima.app -c android.intent.category.LAUNCHER 1   # primer arranque: repone la activación
```

Es el mismo resultado, más rápido. Requiere depuración USB activada, así que si se
usa esta vía conviene **desactivarla de nuevo** antes de las sesiones (§1.2).

> Existe un atajo, `adb shell pm clear com.auroraclima.app`, que borra los datos
> sin desinstalar. **No lo recomiendo aquí:** si los alias del Keystore
> sobrevivieran al borrado de datos en alguna versión de Android, el estado
> resultante no sería el de una instalación limpia, y esa diferencia es
> precisamente lo que las sesiones deben reproducir. Desinstalar y reinstalar no
> deja dudas. Si en algún momento se quiere usar `pm clear`, hay que probarlo una
> vez a conciencia antes de confiar en él.

---

## 4. Correos y contraseñas de prueba

### 4.1 Crear la cuenta no exige verificar el correo

Confirmado en el código: `CreateAccountUseCase` valida en el dispositivo el
formato del correo y que la contraseña tenga **8 caracteres o más**, y llama a
`createUserWithEmailAndPassword`. **No se envía ningún correo de verificación y
ninguna pantalla pide confirmar nada por correo.** No hay en toda la aplicación
una sola llamada a `sendEmailVerification` ni ninguna comprobación de
`isEmailVerified`.

La única parte de la aplicación que envía correo es la recuperación de contraseña
(RF-46), y **no debe usarse en las sesiones** (§4.4).

### 4.2 Esquema de direcciones

Un bloque por tipo de sesión, con numeración de dos dígitos y dos direcciones de
reserva por si alguien se equivoca al teclear:

| Sesión | Direcciones | Cuántas |
|---|---|---|
| Evaluación heurística | `heu01@aurora.test` … `heu05@aurora.test` | 5 |
| Sesiones SUS | `sus01@aurora.test` … `sus12@aurora.test` | 12 |
| Reserva | `heu06@aurora.test`, `sus13@aurora.test` | 2 |

**Por qué el dominio `aurora.test`:** el TLD `.test` está **reservado** por el
RFC 2606 / RFC 6761 y nunca puede delegarse en el DNS, así que ningún mensaje
enviado a esas direcciones puede llegar a nadie, ni hoy ni en el futuro. No es un
dominio que alguien pueda registrar. Además, el nombre es coherente con la
identidad de clima si alguien lo ve en la pantalla.

**Comprobación de dos minutos antes de la primera sesión:** crear `heu01@aurora.test`
en un teléfono y confirmar que la cuenta se crea. Firebase valida el formato del
correo, no la existencia del dominio, así que debería aceptarla sin más; pero es
un paso barato y no quiero que la primera sorpresa llegue con una evaluadora
sentada al lado. Si por lo que sea lo rechazara, la alternativa es
`heu01@example.com` / `sus01@example.com`: `example.com` está reservado por la
IANA y no acepta correo.

### 4.3 Contraseñas y clave personal

- **Contraseña de la cuenta:** una sola para todas las sesiones, de **12
  caracteres o más**, **generada fuera del repositorio** y escrita únicamente en
  las tarjetas impresas y en la hoja de la facilitadora. La aplicación exige un
  mínimo de 8; se piden 12 por el motivo del párrafo siguiente. No usar una
  contraseña personal real, y cambiarla entre el bloque heurístico y el bloque
  SUS.

  > **Por qué no aparece escrita aquí.** El repositorio es público, el registro
  > de cuentas de Firebase está abierto durante las sesiones (P-109) y las
  > direcciones de la tabla de arriba son deterministas. Si la contraseña
  > estuviera en este archivo, cualquiera podría autenticarse como
  > `sus07@aurora.test` mientras duran las sesiones. Y no se quedaría en «entrar
  > a la cuenta»: **la contraseña de la cuenta es la raíz de secreto del
  > respaldo** —envuelve la llave que descifra los blobs de
  > `users/{uid}/entries`—, así que permitiría descifrar y borrar los registros
  > que la participante acaba de crear. Las 600 000 iteraciones de PBKDF2 no
  > protegen nada si la contraseña se publica.
- **Clave personal (la del buscador):** la elige **cada participante** y es parte
  de lo que se evalúa. Dos reglas, que la pantalla muestra en vivo con
  palomitas: **6 caracteres o más** y **que no sea el nombre de una ciudad real**
  (se verifica contra un directorio local de unas 620 entradas). Si alguien se
  bloquea, la pista neutra es «una palabra que no sea un lugar». **Anotar en la
  hoja de observación los intentos rechazados**: es un hallazgo de usabilidad
  valioso, no un error de la participante.
- **La clave de activación** la dice la facilitadora al empezar. Su valor está en
  `app/src/debug/java/com/auroraclima/app/access/data/DebugSecretCodeSeeder.kt`;
  no se transcribe en este documento porque el repositorio es público.

### 4.4 Tres avisos operativos

1. **No usar «¿Olvidaste tu contraseña?» durante las sesiones.** La plantilla del
   correo de recuperación todavía no se ha personalizado, y la que Firebase trae
   por omisión nombra el proyecto y usa un remitente de su propio dominio (P-19,
   bloqueante del piloto). Con direcciones `.test` el mensaje no llegaría a
   ninguna parte, pero el flujo no debe ejercitarse hasta cerrar P-19. Si el guión
   de la sesión toca ese botón, detenerse en la pantalla de confirmación.
2. **Hace falta conexión.** Crear la cuenta contacta a Firebase, y la fachada de
   clima consulta Open-Meteo. Sin red, el primer uso no se puede completar.
   Comprobar el Wi-Fi antes de cada sesión.
3. **Dejar el teléfono con los ajustes de fábrica relevantes**: idioma español,
   tamaño de letra normal, tema claro. Si se quiere evaluar el comportamiento con
   letra grande, hacerlo como paso aparte y anotado, no como condición inicial de
   todas las participantes.

4. **No se puede grabar la pantalla dentro del espacio protegido.** `FLAG_SECURE`
   se aplica por construcción a toda ruta del grafo seguro, así que la grabación y
   las capturas quedan en negro o vacías; `adb shell screencap` devuelve 0 bytes.
   El truco del framebuffer (`adb emu screenrecord`) **solo existe en emulador**,
   no en un teléfono. Hay que decidir **antes** de la primera sesión cómo se
   registra: cámara externa apuntando al teléfono, observación en papel, o
   grabación de audio con pensamiento en voz alta. Si el plan era capturar
   pantalla, no va a funcionar más allá de la fachada de clima.

5. **La sesión se cierra a los 5 minutos de inactividad**, y también al pasar la
   aplicación a segundo plano. Una participante que se detenga a pensar en voz
   alta, atienda una llamada o se ponga a leer el consentimiento en papel vuelve a
   la fachada de clima y tiene que teclear otra vez su clave personal. Es el
   comportamiento correcto del producto (RNF-03) y puede ser un hallazgo legítimo
   de usabilidad: la facilitadora debe saberlo para **no confundirlo con un fallo**
   y para anotarlo bien.

### 4.5 Encuadre de la sesión

Esto no es logística: es la parte que protege a las participantes. El guión les
pide usar una aplicación sobre violencia de género y, si llega a la tarea de
registro o al cuestionario, **escribir hechos y responder preguntas sobre señales
de violencia**. Con 15 a 17 personas, la probabilidad de que alguien reconozca
algo propio no es teórica.

1. **Consentimiento informado firmado antes de empezar**, que diga explícitamente
   que la sesión se hace con cuentas de prueba, que lo que se escriba se elimina
   al terminar, y que la participante puede detenerse en cualquier momento sin dar
   explicaciones.
2. **Frase literal de la facilitadora antes de la tarea de registro:** «para esta
   prueba escribe un hecho inventado; no uses datos reales tuyos ni de nadie».
   Repetirla también antes del cuestionario.
3. **Si hay una revelación real:** detener la tarea, no anotarla en la hoja de
   observación, no insistir, y tener a mano las líneas de atención impresas —las
   mismas que la Guía de la aplicación muestra—. La sesión es una prueba de
   usabilidad, no una entrevista ni una intervención.
4. **Si alguna participante escribió algo real**, correr el borrado de la §5 **ese
   mismo día** para su cuenta, sin esperar al final de todas las sesiones, y no
   incluir esa sesión en el análisis de contenido.

---

## 5. Borrar todas las cuentas y sus datos al terminar

`delete-test-account.mjs` trabaja **una cuenta por invocación** y **en modo de
inspección por omisión**: sin `--delete` es de solo lectura (muestra uid, fechas y
cuántos documentos hay, sin leer su contenido). Con `--delete` purga primero
`users/{uid}` en Firestore —recursivo: `entries`, `media`, `mediaparts`, `meta`,
`outcomes`— y después elimina la cuenta de Auth, verificando al final que ya no
existe. Ese orden importa: eliminar el usuario de Auth no borra sus documentos.

**Los comandos siguientes NO se han ejecutado.** Hay que correrlos a mano al
terminar las sesiones.

Primero, ver qué cuentas existen (solo lectura, no toca nada):

```bash
cd ~/dev/unab/aurora/tools/admin
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"
node listar-cuentas.mjs
```

Después, la pasada de inspección sobre las 17 direcciones — **modo de prueba, no
borra nada**:

```bash
cd ~/dev/unab/aurora/tools/admin
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"

CUENTAS="heu01 heu02 heu03 heu04 heu05 heu06 sus01 sus02 sus03 sus04 sus05 sus06 sus07 sus08 sus09 sus10 sus11 sus12 sus13"

for c in $CUENTAS; do
  echo "──────── $c@aurora.test"
  node delete-test-account.mjs --email "$c@aurora.test"
done
```

Revisar esa salida con calma. Y solo entonces, el borrado real:

```bash
cd ~/dev/unab/aurora/tools/admin
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"

CUENTAS="heu01 heu02 heu03 heu04 heu05 heu06 sus01 sus02 sus03 sus04 sus05 sus06 sus07 sus08 sus09 sus10 sus11 sus12 sus13"

for c in $CUENTAS; do
  echo "──────── $c@aurora.test"
  node delete-test-account.mjs --email "$c@aurora.test" --delete
done
```

Notas:

- Una dirección que no exista (las de reserva, si no se usaron) imprime «no existe
  en Auth. Nada que hacer» y sigue. El bucle no se rompe.
- Es idempotente: se puede repetir.
- **No incluir en la lista la cuenta de la administradora del panel** ni ninguna
  otra cuenta que deba seguir viva. Por eso la primera pasada es de inspección y
  por eso conviene correr `listar-cuentas.mjs` antes y después.
- Al terminar, volver a correr `listar-cuentas.mjs` y guardar la salida como
  evidencia de que no quedaron datos de participantes.
- **Y lo último, que no es un comando:** desactivar el registro de cuentas en
  *Firebase Console → Authentication → Settings → User actions*. El registro se
  mantiene activo solo mientras duren las sesiones; al terminar se apaga (P-109).
  Es un interruptor y se vuelve a encender cuando haga falta.

---

## 6. Lista de verificación

**Una vez, antes de la primera sesión**

- [ ] Agregar `*.apk` y `*.aab` a `.gitignore` (§2).
- [ ] Compilar el debug y copiar el APK a `~/aurora-sesiones/` con nombre neutro
      (§1.3, §2); anotar su SHA-256.
- [ ] Dejar el APK en **Descargas** de cada teléfono (§2).
- [ ] Instalar, abrir una vez y comprobar que la fachada de clima carga con datos
      reales.
- [ ] Crear `heu01@aurora.test` de prueba y confirmar que la cuenta se crea sin
      pedir verificación de correo (§4.2). Después borrarla con el script (§5).
- [ ] Confirmar que *Authentication → Settings → User actions* permite el registro
      durante las sesiones (P-109).
- [ ] Desactivar la depuración USB en los teléfonos (§1.2); rechazar el envío del
      APK a Google y desactivar el reescaneo de Play Protect (§3.2).
- [ ] **Generar la contraseña común fuera del repositorio** (12 caracteres o más)
      y escribirla solo en las tarjetas y en la hoja de la facilitadora (§4.3).
- [ ] **Decidir cómo se registra la sesión**, dado que no se puede grabar la
      pantalla dentro del espacio protegido (§4.4, aviso 4).
- [ ] Preparar el **consentimiento informado** y leer el encuadre del §4.5.
- [ ] Imprimir las tarjetas: una por participante, con su correo, la contraseña
      común y la pista para la clave personal.
- [ ] Imprimir las **líneas de atención** para tenerlas a mano (§4.5, punto 3).
- [ ] Idioma español, letra normal, tema claro, Wi-Fi funcionando.

**Antes de cada participante**

- [ ] Desinstalar y reinstalar desde el teléfono (§3.2).
- [ ] Abrir la aplicación una vez y cerrarla.
- [ ] Teléfono en la pantalla de inicio, sin notificaciones del instalador a la
      vista.
- [ ] Tarjeta de la participante preparada, con un correo que no se haya usado.

**Al terminar todas las sesiones**

- [ ] `listar-cuentas.mjs` y guardar la salida.
- [ ] Pasada de inspección sobre las 17 direcciones (§5).
- [ ] Borrado real con `--delete`.
- [ ] `listar-cuentas.mjs` otra vez, como evidencia.
- [ ] Desactivar el registro de cuentas en la consola (P-109).
- [ ] Desinstalar la aplicación de los teléfonos y borrar el APK de Descargas.

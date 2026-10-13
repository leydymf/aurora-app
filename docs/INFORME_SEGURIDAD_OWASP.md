# Aurora — Evaluación de seguridad con OWASP MAS

> Prueba de seguridad de la Fase 4 del trabajo de grado, sobre la versión
> **v0.3.0-trl4** (rama `main`, commit `16ce199`). El anteproyecto compromete
> evaluar la seguridad «con referencia a la OWASP Mobile Security Testing
> Guide»; esa guía se llama hoy **MASTG** y se verifica contra el estándar
> **MASVS**. Este informe recorre los controles de MASVS que corresponden al
> alcance acordado y deja, por cada uno, qué se probó, cómo, el resultado y la
> evidencia que lo respalda.
>
> Fechas de la evaluación: **8, 9 y 10 de octubre de 2026**, más las dos
> comprobaciones manuales de la desarrolladora del **10 de octubre** que cierran
> P-11 y abren el hallazgo H-7 (EV-14).
> Evidencias en `docs/evidencias-owasp/` (referenciadas como EV-NN).
>
> Este documento pasó por una auditoría interna que lo **rechazó** en una primera
> versión, con veinte correcciones. Todas se aplicaron: la trazabilidad de las
> afirmaciones (EV-12), la verificación en vivo que faltaba (EV-13), la
> redacción de identificadores, dos veredictos que se rebajaron a «Pasa con
> observación», y varias cifras y citas que decían más de lo que la evidencia
> sostenía. La sección 9 resume ese proceso.

---

## 1. Versión del estándar utilizada

Consultada en los repositorios oficiales el 2026-10-08 (evidencia: **EV-00**):

| Documento | Versión vigente | Fecha | Comprobación |
|---|---|---|---|
| **OWASP MASVS** (estándar de verificación) | **v2.1.0** | 18 de enero de 2024 | `api.github.com/repos/OWASP/masvs/releases` — es la última; la anterior, v2.0.0, es de abril de 2023 |
| **OWASP MASTG** (guía de pruebas) | **v2.0.0** | 30 de junio de 2026 | `api.github.com/repos/OWASP/mastg/releases`; anuncio del 4 de julio de 2026 en `mas.owasp.org/news/` |
| **OWASP MASWE** (enumeración de debilidades) | repositorio `OWASP/maswe`, 78 debilidades | — | se usa donde MASTG no tiene una prueba atómica propia |

Sobre la correspondencia con el anteproyecto: la **MSTG** (*Mobile Security
Testing Guide*) pasó a llamarse **MASTG** (*Mobile Application Security Testing
Guide*) en la refactorización que OWASP cerró con la v2.0.0. Ya no es un libro
lineal: es un grafo de componentes con identificador estable —pruebas
(`MASTG-TEST-xxxx`), técnicas, herramientas, demostraciones y buenas
prácticas— enlazados a los controles de MASVS. Por eso cada control se acompaña
aquí de los identificadores concretos de las pruebas aplicadas. Esta evaluación
cita **87 pruebas MASTG distintas** y 5 debilidades MASWE; cada una se
contrastó contra el catálogo oficial, y EV-00 [4] trae la lista completa agrupada
por categoría.

MASVS v2.1.0 tiene **ocho categorías y 24 controles**; EV-00 reproduce el texto
literal de los 24. Este informe cubre **20** (las siete categorías en alcance) y
declara **No aplica** los cuatro de MASVS-RESILIENCE, con la justificación de la
sección 3.

---

## 2. Objeto de la prueba

| | |
|---|---|
| Versión | `v0.3.0-trl4` (etiqueta anotada) → commit `16ce199`, igual a `HEAD` de `main` |
| Artefacto estático | `app/build/outputs/apk/release/app-release.apk`, 23 951 586 bytes |
| SHA-256 del APK | `28b9a1ff07cd5e85d532977e872fb6c6f2f321149e03004c4004441dd54eeb42` |
| Identidad | `com.auroraclima.app`, etiqueta «Aurora», minSdk 26, targetSdk 35 |
| Entorno dinámico | Emulador `Medium_Phone_API_35` (API 35, 4 núcleos, 4 GB, imagen `google_apis_playstore`) |
| Datos | La cuenta de **prueba** de la corrida manual del 2026-10-07: 5 registros, 16 cabeceras de evidencia con 48 trozos, 2 resultados de cuestionario guardados y 1 documento de metadatos |

`./gradlew assembleRelease` terminó con todas las tareas de empaquetado
`UP-TO-DATE`, lo que confirma que el APK en disco corresponde exactamente a ese
commit (EV-01).

---

## 3. Alcance

**Categorías principales**, con pruebas estáticas y dinámicas:
MASVS-STORAGE, MASVS-CRYPTO, MASVS-AUTH, MASVS-PLATFORM y MASVS-PRIVACY.

**Cobertura ligera**, solo estática: MASVS-NETWORK (TLS, tráfico en claro,
configuración de red) y MASVS-CODE (secretos en el código, dependencias,
depuración desactivada en release).

**Fuera de alcance: MASVS-RESILIENCE (controles 1 a 4) — No aplica.**
Justificación: estos cuatro controles piden detección de root, antimanipulación,
antianálisis estático y antianálisis dinámico. Aurora es un prototipo TRL 4 que
se entrega en mano a un grupo reducido de usuarias en un piloto controlado, y su
modelo de amenaza es **un agresor con acceso físico al teléfono**, no un analista
con herramientas de ingeniería inversa. Invertir en estas defensas no reduciría
el riesgo del modelo y sí añadiría peso y librerías nativas atípicas, lo que
jugaría en contra del camuflaje y del criterio A2-1 (gama baja).

Dicho eso, **la app ya satisface de hecho parte de MASVS-RESILIENCE-3 como efecto
secundario del camuflaje**, y eso sí se midió: el release se compila con R8
(`isMinifyEnabled` + `isShrinkResources`), no lleva bandera `debuggable` y no
conserva llamadas a `android.util.Log`. El alcance exacto de esa ofuscación —y
su límite— es el hallazgo **H-1** de la sección 6.

**No se hicieron**: ataques de denegación de servicio, pruebas contra la
infraestructura de Firebase en producción, volcados de memoria del proceso, ni
análisis con instrumentación dinámica (Frida). Tampoco se usó MobSF: el demonio
de Docker no estaba activo y la alternativa —herramientas explícitas y
reproducibles— es más defendible en un informe académico, porque cada resultado
queda ligado al comando que lo produjo.

---

## 4. Método, herramientas y limitaciones

### 4.1 Herramientas

| Herramienta | Versión | Para qué |
|---|---|---|
| `jadx` | 1.5.6 | descompilación del DEX del release a Java legible |
| `apktool` | 3.0.3 | decodificación del manifiesto y de `resources.arsc` |
| `apkanalyzer`, `aapt2`, `apksigner` | Android SDK build-tools 35.0.0 / cmdline-tools | manifiesto, permisos, firma, paquetes del DEX |
| `adb` | platform-tools del SDK | inspección del sandbox, logcat, capturas crudas, recorrido de la interfaz, sondeo de componentes, respaldos |
| `firebase-tools` | 15.33.0 | emulador local de Firestore para ejecutar las pruebas de reglas |
| `strings`, `unzip`, `sqlite3`, Python 3 | del sistema | búsqueda de secretos, entropía, lectura de la caché, medición de píxeles |
| API de **OSV.dev** | — | vulnerabilidades conocidas de las 166 dependencias Maven |
| Suite del propio proyecto | — | pruebas de JVM e instrumentadas, ambas ejecutadas en esta auditoría |

jadx y apktool se instalaron como archivos portables en una carpeta temporal
fuera del repositorio, no con Homebrew: la fórmula de Homebrew quería tocar el
JDK con el que compila el proyecto y el estado de Homebrew en la máquina estaba
roto. Corren sobre el Java ya instalado y no modifican nada del sistema.

### 4.2 Reutilización de evidencia existente

Donde el proyecto ya tenía una prueba automática, una auditoría o un ADR que
cubría un control, se **cita como evidencia en lugar de repetir la prueba**, pero
solo después de volver a ejecutarla. Se ejecutaron las dos suites:

- **Pruebas de JVM**: `./gradlew cleanTestDebugUnitTest cleanTestReleaseUnitTest
  test ktlintCheck` → **791 pruebas en 105 clases, en las variantes debug y
  release, 0 fallos y 0 errores** (EV-05).
- **Pruebas instrumentadas**: `./gradlew connectedDebugAndroidTest` sobre el
  emulador A → **45 pruebas, 0 fallos, 0 omitidas** (EV-05). Son las que cubren
  el Keystore y SQLCipher reales, que no pueden correr en la JVM del equipo.

Lo que ninguna prueba cubría se probó de cero.

### 4.3 Limitaciones metodológicas (importantes para leer los resultados)

1. **La inspección del sandbox se hizo sobre la build `debug`, no sobre la
   `release`.** Las dos imágenes de emulador instaladas son `google_apis_playstore`,
   donde `adb root` responde «adbd cannot run as root in production builds». La
   vía disponible es `adb shell run-as`, que solo funciona con una app depurable.
   Qué cambia y qué no: **todo el código de almacenamiento, cifrado, Keystore y
   preferencias vive en `src/main`**, de modo que es idéntico en las dos variantes;
   `src/debug` solo añade el sembrador del código de activación y el *tooling* de
   Compose. Lo único que sí difiere —qué nombres quedan legibles tras R8— se
   evaluó aparte y directamente sobre el APK release (EV-03). Por tanto las
   conclusiones de MASVS-STORAGE se transfieren al release; las de camuflaje del
   DEX se midieron sobre el release y no sobre la debug.
2. **MASVS-NETWORK se cubrió solo de forma estática.** No se montó un proxy de
   intercepción, así que `MASTG-TEST-0236` (tráfico en claro observado en la red)
   y `MASTG-TEST-0244` (ausencia de pinning observada en la red) no se ejecutaron.
   Era lo acordado para una cobertura ligera.
3. **Dos comprobaciones no eran verificables por `adb`, y se hicieron a mano.**
   `adb shell input text` inyecta el texto directamente en la conexión de
   entrada, **sin pasar por el teclado**, así que Gboard nunca ve lo tecleado y
   no puede aprenderlo; y este build del emulador no admite `cmd clipboard` ni
   la combinación de teclas de copiar, ni expone el menú flotante de selección
   en el volcado de accesibilidad. Las dos las ejecutó **a mano la
   desarrolladora sobre el emulador** el 2026-10-10, y sus resultados están
   incorporados: el teclado no aprende la clave personal (cierra **P-11**) y el
   relato sí se puede copiar en modo edición (hallazgo **H-7**). Evidencia:
   EV-14.
4. **El recorrido del espacio interno se hizo el tercer día, tras un incidente.**
   Las dos primeras jornadas (8 y 9 de octubre) la auditoría no pudo entrar: la
   cuenta de prueba ya
   tenía clave personal y, por diseño (RF-12), el código de activación deja de
   abrir el acceso en cuanto esa clave existe. Al ejecutar las pruebas
   instrumentadas, **Gradle desinstaló la app al terminar** —es su comportamiento
   normal— y con ello se borró el estado local de la cuenta de prueba en el
   emulador A: bases cifradas, evidencias selladas, preferencias y alias del
   Keystore. No se perdió nada en la nube. La cuenta se restauró reinstalando la
   build debug e iniciando sesión, lo que además sirvió como verificación en
   vivo de RF-39, y a partir de ahí se pudo recorrer el espacio interno y
   verificar lo que antes dependía solo de pruebas automáticas (EV-13). El
   incidente queda registrado aquí por honestidad metodológica: afectó al estado
   del emulador, no a ningún resultado de la evaluación.

---

## 5. Resultados por control

Escala: **Pasa** · **Pasa con observación** · **Falla** · **No aplica**.

### 5.1 MASVS-STORAGE — Almacenamiento

#### MASVS-STORAGE-1 · «La app almacena los datos sensibles de forma segura» — **Pasa con observación**

**Qué se probó.** Que todo dato sensible escrito en disco esté cifrado, que las
llaves no estén junto a los datos y que nada salga del almacenamiento privado.

**Cómo.** Se extrajo el sandbox completo de la app del emulador **en su build
debug** (ver limitación 4.3.1) con `run-as … tar`, 35 archivos, y sobre cada uno
se midió la entropía de Shannon y se buscó un diccionario de 27 palabras de
contenido natural en español (*que, para, cuando, dijo, miedo, golpe, amenaza,
violencia, incidente*…). Se leyeron las cabeceras de las cuatro bases de datos y
se abrió la caché de Firestore con `sqlite3`. Pruebas MASTG aplicadas: 0001,
0207, 0287, 0304, 0306, 0200, 0202.

**Resultado.**

| Archivo | Bytes | Entropía | Palabras de contenido |
|---|---|---|---|
| `journal_store.db` | 4 096 | 7,951 | **0** |
| `journal_store.db-wal` | 346 112 | 7,999 | **0** |
| `guidance_store.db` | 4 096 | 7,955 | **0** |
| `guidance_store.db-wal` | 53 592 | 7,997 | **0** |
| `files/media_store/*` (14 evidencias) | 416 – 299 633 | 7,45 – 8,00 | 3, descartadas (ver abajo) |
| `shared_prefs/*` (5 propios) | 65 – 854 | — | **0** |

Las dos bases sensibles empiezan con bytes aleatorios, no con `SQLite format 3`:
SQLCipher está activo, **y también cifra el WAL** (el archivo grande). Las 14
evidencias presentes en el almacén local —de las 16 que la cuenta tiene en la
nube— no tienen los bytes mágicos de JPEG ni de PDF y su nombre es un
identificador opaco de 32 hexadecimales. El extremo bajo del rango de entropía
(7,454) corresponde al archivo de 416 bytes: con tan pocas muestras sobre 256
símbolos posibles es justo lo que da el ruido aleatorio; los trece restantes
están entre 7,98 y 8,00.

Sobre las **3 coincidencias** en `media_store`: el barrido las encontró en tres
archivos distintos, una en cada uno, y se descartaron por inspección directa —
aparecen con mayúsculas mezcladas (`cOn`, `QUe`) y rodeadas de bytes no
imprimibles, que es lo esperable por azar en 300 KB de ciphertext. Se declaran
aquí en vez de redondear a cero.

Las preferencias propias usan claves neutras (`a0`, `a1`, `b0`…`b4`, `i0_h`,
`s0`, `s1`) y **solo valores base64, más un contador entero** (`b3`, la
generación del respaldo). Las longitudes coinciden exactamente con lo que
describen los ADR: 32 bytes de HMAC-SHA256 y 16 de salt para el índice de
códigos, 48 bytes para la passphrase envuelta de SQLCipher, 48 para la DEK
envuelta.

Fuera del sandbox: `/sdcard/Android/data/com.auroraclima.app` **no existe** y la
app no declara ningún permiso de almacenamiento externo.

**Observación.** El almacén cifrado no es el único sitio donde la app escribe.
El residual **P-60a** registra que, durante una captura con la cámara, el JPEG
existe **en claro** unos segundos en `cache/media_pending/` antes de cifrarse, y
**P-60d** que el visor materializa una copia del PDF por milisegundos (borrada
siempre en un `finally`). Esta auditoría **no pudo observar esa ventana**: el
sandbox se inspeccionó con la caché prácticamente vacía, lo que es consecuencia
de no haber adjuntado una evidencia nueva durante la prueba, no evidencia de
ausencia. El veredicto se rebaja a «con observación» por eso.

**Evidencia.** EV-06 · EV-02 · pruebas instrumentadas ejecutadas hoy
`EncryptedDatabaseFactoryTest` (3), `LocalDekStoreTest` (6),
`LocalCredentialStoreTest` (11) y de JVM `JournalDatabaseMigrationTest` (8)
(EV-05) · ADR 2026-07-10 (SQLCipher) y 2026-07-24 (evidencias) de
`docs/ARQUITECTURA.md`.

#### MASVS-STORAGE-2 · «La app evita la fuga de datos sensibles» — **Falla**

**Qué se probó.** Registros del sistema, respaldos, caché del teclado,
portapapeles, terceros y notificaciones.

**Cómo y resultado, punto por punto.**

- **Logs** (MASTG-TEST-0003, 0231). Dos capturas, con el búfer limpio antes de
  cada una. La primera, 25 181 líneas de todos los búferes durante un ciclo
  completo de uso **de la fachada**: de las 315 que mencionan a la app, **todas**
  provienen de componentes del sistema; las 20 que emite el proceso con etiqueta
  propia son del runtime ART (GC, JIT), y las cuatro sondas buscadas aparecen
  **0 veces**. La segunda, 1 515 líneas recorriendo **el espacio interno** —
  panel, Guía, Mi registro, abrir un registro, escribir en el editor, Ajustes y
  salida rápida—: **0 ocurrencias** de la sonda tecleada en el relato, de la
  clave personal, del contenido de un registro real, y de `payload`, `journal`,
  `guidance`, `SecretCode` o el léxico vedado. Las 10 líneas que contienen
  «SessionManager» las emite Gboard sobre sus propias sesiones de entrada; del
  proceso de Aurora, cero. ✅
- **Respaldos** (MASTG-TEST-0009, 0216, 0262). `allowBackup="false"` más
  `data_extraction_rules` y `backup_rules` que excluyen explícitamente los cinco
  dominios. Se intentó un respaldo real: `bmgr backupnow com.auroraclima.app`
  responde **`Backup is not allowed`** — no es que el respaldo falle, es que el
  gestor se niega a intentarlo. Además, el monitor del sistema se volcó completo
  y se emparejó cada evento con su paquete: de los 80 eventos, 42 son de
  `com.auroraclima.app`, y se reparten en **21 «Invoked restore at install» y 21
  «Skip restore at install»** — correspondencia 1 a 1 desde agosto. Salidas
  literales en EV-12 [1] y [1·bis]. ✅
- **Portapapeles** (MASWE-0030). ⚠️ **Aquí está el fallo.** La app nunca
  *escribe* en el portapapeles —`ClipboardManager`, `ClipData` y
  `setPrimaryClip`: cero ocurrencias en todo el código— pero **sí deja que la
  usuaria copie el relato de un registro mientras lo escribe**, y de ahí puede
  pegarse en cualquier otra aplicación. Verificado a mano sobre el emulador. Es
  el hallazgo **H-7**. El *estado* del portapapeles no se pudo medir con las
  sondas disponibles: `dumpsys clipboard` devuelve salida vacía sin un control
  que acredite qué significa, y `service call clipboard 1` no es una lectura
  sino `setPrimaryClip` —una escritura que el servicio rechaza—, de modo que su
  «No items» es el mensaje de una excepción de la propia sonda. EV-12 [3] lo
  documenta con el Parcel y su traza. Los campos de clave y contraseña sí están
  bien: `V2SecretField`, con `KeyboardType.Password` y
  `PasswordVisualTransformation`, desactiva «copiar» en la barra de selección.
  El del relato no. ❌
  > **Errata (2026-10-10, H-9).** Las dos últimas frases son falsas, y lo eran ya
  > en v0.3.0. Compose anula «copiar» solo **mientras** la transformación visual
  > ES de contraseña (`instanceof PasswordVisualTransformation` en
  > `TextFieldSelectionManager.showSelectionToolbar`, medido en el bytecode de
  > foundation 1.7.8), y `V2SecretField` la cambia a `None` al pulsar el ojo. Con
  > el ojo abierto, los campos de clave y contraseña ofrecían «copiar» igual que el
  > relato. El párrafo no se reescribe porque es lo que la evaluación concluyó; el
  > hallazgo y su corrección están en §10.8.
- **Caché del teclado** (MASTG-TEST-0006, 0258). Resultado mixto. Los campos
  secretos usan `KeyboardType.Password`, que impide el aprendizaje del IME, y
  las dos barras de búsqueda usan `autoCorrect = false`
  (`WeatherScreen.kt:253`, `TutorialScreen.kt:356`): **la verificación manual
  confirmó que el teclado NO aprende la clave personal**, lo que cierra el
  pendiente **P-11**. ✅ Pero el campo del relato, `V2ParagraphField`, **no
  declara ningún `keyboardOptions`** —ni `autoCorrect = false` ni ninguna otra
  protección—, así que para él la prueba no se cumple: es la segunda vía de
  H-7. ❌
- **Terceros y notificaciones** (MASTG-TEST-0004, 0005, 0315). Sin Crashlytics,
  sin Analytics, sin SDK de publicidad ni de medición en el DEX. 0 usos de
  `NotificationManager`: la app no emite notificaciones. ✅
- **Memoria** (MASTG-TEST-0011). No ejecutado: un volcado de memoria excede el
  modelo de amenaza y el residual ya está aceptado y documentado (P-33, P-35,
  P-62c: la clave y la contraseña viven como `String` inmutable, que en la JVM no
  se puede poner a cero).

**Veredicto.** El control pide que la app «evite la fuga de datos sensibles…
donde quien desarrolla tiene una forma de impedirlo». Aquí la tiene —es lo que
ya hace con todos los demás campos— y no la aplicó al campo que contiene el
dato más sensible de la app. Por eso **Falla**, aunque todo lo demás de este
control esté bien resuelto: registros, respaldos, terceros y notificaciones.

**Observaciones que se mantienen.** Dos.

La primera: la caché persistente del SDK de Firestore es **SQLite plano**, sin
SQLCipher. Es el residual **P-22**, y esta auditoría lo acota con número exacto:
de las 213 coincidencias de palabras de contenido en esa caché, **las 213 están
en el contenido público** (tipos, rutas, entidades, líneas, cuestionario) y **0 en
los documentos de la usuaria**. En toda la caché de la cuenta los únicos
nombres de campo legibles son `payload` e `iv`, y solo aparecen en 20 de los 72
documentos (4 de `entries`, 14 de `media` y 2 de `outcomes`; los 52 restantes,
casi todos trozos de evidencia, no exponen ninguno). Es exactamente el residual
**P-48b**: revelan que hay cifrado, no qué se cifró. El
relato, el tipo y la fecha viajan dentro del ciphertext. Leer esa caché exige
root o `adb`, que ya están fuera del modelo de amenaza.

La segunda ya no es observación sino el hallazgo H-7, y es la que hace fallar
el control.

**Evidencia.** EV-06 · EV-08 · **EV-13 [11]** (logcat del espacio interno) ·
EV-12 · EV-02 · **EV-14** (las dos comprobaciones manuales) ·
`NoTracesGuardTest` (9), `StoredCredentialsTest` (2).

---

### 5.2 MASVS-CRYPTO — Criptografía

#### MASVS-CRYPTO-1 · «La app emplea criptografía fuerte y la usa bien» — **Pasa**

**Qué se probó.** Algoritmos, modos, tamaños de llave, reutilización de vectores
de inicialización, fuente de aleatoriedad y proveedor.

**Cómo.** Inventario exhaustivo de `src/main`: todas las llamadas a
`Cipher.getInstance`, `KeyGenerator`, `SecretKeyFactory`, `Mac`, `SecureRandom`,
`PBEKeySpec` y `KeyGenParameterSpec`. Pruebas MASTG: 0013, 0014, 0208, 0221,
0232, 0309, 0312, 0016, 0204, 0205.

**Resultado.**

- `Cipher.getInstance` aparece **16 veces** (8 archivos × cifrar y descifrar) y
  **siempre** con `"AES/GCM/NoPadding"`. Cero ECB, cero CBC, cero
  `Cipher.getInstance("AES")` a secas, cero DES/3DES/RC4/Blowfish. Tampoco se usa
  `MessageDigest`: el hash del código secreto es un HMAC-SHA256 con llave del
  Keystore.
- Todas las llaves son de **256 bits**; el tag GCM es de 128 bits.
- En los dos cifradores que manejan el nonce explícitamente se generan **12 bytes
  nuevos de `SecureRandom` en cada operación**, y viajan junto al ciphertext: no
  hay IV constante ni derivado del contenido. En los que van sobre Keystore el IV
  lo genera el proveedor de la plataforma.
- La **única** fuente de aleatoriedad en todo el código es
  `java.security.SecureRandom` (8 usos: salt, passphrase, DEK, nonce,
  identificadores opacos, referencias de evidencia, nombres temporales).
  `java.util.Random` y `Math.random`: **0 ocurrencias**.
- Derivación por contraseña: **PBKDF2-HMAC-SHA256 con 600 000 iteraciones**,
  salt de 16 bytes por sobre, salida de 256 bits, y `spec.clearPassword()` en un
  `finally`. 600 000 es exactamente la cifra que recomienda la *OWASP Password
  Storage Cheat Sheet* para ese algoritmo.
- Los PDF exportados usan `/Filter /Standard, /V 5, /R 6, AESV3` con
  `/Length 256` **dentro del diccionario `/Encrypt`**, es decir **AES-256 de PDF
  2.0** — no el RC4 que MASTG marca como débil. Verificado sobre **los siete**
  PDF que la app exportó en las corridas manuales (3, 5 y 7 de octubre) y que
  siguen en `/sdcard/Download`, medidos uno a uno en EV-06 [7]: los siete
  cifrados, los siete con 0 palabras legibles. Entre ellos los dos de ~520 KB,
  que son los que llevan los adjuntos embebidos.
- Ningún `Cipher.getInstance(..., "proveedor")`: se usa el proveedor por defecto
  de la plataforma, y «AndroidKeyStore» solo donde corresponde.

**Evidencia.** EV-04 · EV-06 [7] · pruebas de JVM `DekPayloadCipherTest` (6),
`DekMediaCipherTest` (7), `PasswordKeyWrapperTest` (8),
`Pbkdf2PasswordKeyDerivationTest` (4) e instrumentadas `KeystorePayloadCipherTest`
(5) y `KeystoreMediaCipherTest` (6), todas ejecutadas hoy (EV-05).

#### MASVS-CRYPTO-2 · «La app gestiona las llaves según las buenas prácticas» — **Pasa con observación**

**Qué se probó.** Dónde nacen, dónde viven y cómo mueren las llaves; que ninguna
esté embebida; que cada una tenga un propósito.

**Resultado.** Seis alias en **Android Keystore**, todos con nombre neutro y un
único propósito cada uno —no hay una llave que firme y cifre a la vez—:
`local_store_key` (envuelve la passphrase de SQLCipher), `local_sync_key`
(payload de sincronización), `local_media_key` (evidencias), `local_backup_key`
(copia local de la DEK), `local_account_key` (blob de credenciales) y
`local_index_key` (HMAC-SHA256 del código secreto, con `PURPOSE_SIGN`).

La búsqueda de secretos sobre el APK release no encuentra **ninguna** llave
embebida: 0 llaves de API, 0 llaves privadas PEM, 0 tokens, 0 JWT (EV-03). El
borrado criptográfico de RF-20 destruye los alias, y está fijado por
`DatabaseKeyManagerEraseTest`.

Hubo además una comprobación no planificada del ciclo de vida: al desinstalar la
app (incidente de la limitación 4.3.4) los alias del Keystore se destruyeron con
ella, y sin embargo la cuenta se restauró al reinstalar e iniciar sesión. Es
exactamente el comportamiento que RF-39 diseñó —la DEK se recupera del sobre
envuelto con la contraseña— y confirma que las llaves no sobreviven a la
desinstalación.

**Observación.** Las llaves están **ligadas al dispositivo** y la usuaria no
aporta entropía al cifrado de la base local: quien tenga root y pueda invocar la
llave del Keystore descifra la base sin conocer la clave personal. Es una
decisión deliberada de RF-09 —la alternativa, perder los datos al olvidar la
clave, es peor para esta población— y ya está registrada como **P-37b**, con sus
derivadas **P-62** y **P-70**. A ello se añade un dato medido aquí: la **única**
regla que la app aplica a la contraseña de la cuenta es `length >= 8`
(`CreateAccountUseCase.kt:33` y `:61`), sin comprobación de complejidad ni lista
de contraseñas comunes, y en pantalla el único texto de ayuda es «Mínimo 8
caracteres». Como desde RF-39 esa contraseña es la raíz del secreto del respaldo,
eso confirma **P-62a** con una referencia exacta.

**Evidencia.** EV-04 [7] · EV-03 [1] · EV-06 [3] · EV-13 [9].

---

### 5.3 MASVS-AUTH — Autenticación y autorización

#### MASVS-AUTH-1 · «Protocolos de autenticación y autorización seguros» — **Pasa**

**Qué se probó.** Que la autorización esté en el extremo remoto y no solo en el
cliente, y que cada usuaria solo alcance lo suyo.

**Cómo.** Revisión línea por línea de `firestore.rules` y —esto es nuevo— la
**ejecución real** de `firestore/rules.test.mjs`, que nunca se había corrido
porque el entorno no tenía el emulador de Firestore. Se instaló `firebase-tools`
en una carpeta temporal fuera del repositorio, se levantó un emulador local y
offline y se ejecutaron los ocho casos contra una copia **byte a byte** de las
reglas (sha256 verificado en EV-10). El proyecto real de Firebase no se tocó.

**Resultado: 8 de 8 en verde.**

```
ok 1 - anónima no puede leer el contenido
ok 2 - autenticada lee el contenido pero no lo escribe
ok 3 - la administradora (claim admin) sí escribe el contenido (RF-40/41-44)
ok 4 - los borradores del panel son exclusivos de la administradora (RF-44)
ok 5 - la administradora NO puede leer ni escribir datos de usuarias (RNF-06)
ok 6 - la usuaria lee y escribe sus propios registros
ok 7 - nadie puede leer los registros de otra usuaria (RNF-06)
ok 8 - todo lo demás está negado
```

El aislamiento usa la forma canónica —`request.auth.uid == uid` contra el
segmento real de la ruta, no contra un campo del documento— y el comodín
`{document=**}` cubre las cinco subcolecciones que existen de verdad
(`entries`, `media`, `mediaparts`, `outcomes`, `meta`), sin que ninguna pueda
quedar fuera por olvido. El rol administrativo **no aparece** en el bloque
`/users`: la separación es estructural, no una exclusión que pudiera olvidarse.
Y como defensa en profundidad, aunque las reglas fallaran, lo que hay allí es
ciphertext.

**Esto cierra CP-37**, que estaba pendiente desde julio de 2026.

**Evidencia.** EV-10 · `firestore.rules` · `firestore/rules.test.mjs`.

#### MASVS-AUTH-2 · «Autenticación local segura» — **Pasa**

**Qué se probó.** El mecanismo de acceso al doble fondo: que el código no se
guarde en claro, que la comparación sea en tiempo constante, que un código
incorrecto sea indistinguible de una búsqueda fallida de ciudad, que haya
bloqueo tras varios intentos y que la sesión se cierre.

**Resultado — parte estática y de código.**

- El código **no se almacena**: en `search_index.xml` hay ranuras con un
  HMAC-SHA256 de 32 bytes y un salt de 16, y la llave del HMAC vive en el
  Keystore (`local_index_key`, `PURPOSE_SIGN`). Verificado en el dispositivo, y
  además `KeystoreSecretCodeHasherTest` (5, instrumentada, ejecutada hoy).
- `constantTimeEquals` recorre siempre la longitud completa acumulando XOR, sin
  cortocircuito, y compara hashes de longitud fija. Fijado por `ConstantTimeTest` (4).
- La indistinguibilidad está construida en el diseño:
  `ValidateSecretCodeUseCase` devuelve `PassThrough` tanto si el código no
  coincide como si falla el Keystore o el almacenamiento, de modo que la fachada
  nunca muestra un error distinto. Fijado por `ValidateSecretCodeUseCaseTest` (10).
- Todos los rechazos del proveedor de autenticación devuelven el **mismo**
  mensaje, y a los 5 fallos entra un bloqueo temporal que sobrevive a forzar la
  detención. `SignInUseCaseTest` (16) y `LoginViewModelTest` (13).
- El cierre de sesión converge en un único `SessionManager`. `SessionManagerTest` (20).

Son **63 pruebas automáticas** entre las cinco clases, todas verdes hoy.

**Resultado — verificación en vivo (EV-13).** Se recorrió el flujo completo:

- Teclear la clave personal en la barra abre el espacio interno; la bandera
  `SECURE` aparece en la ventana en ese mismo momento.
- **Salida rápida (RF-16):** la nube flotante devuelve a la fachada, el proceso
  **no muere** (mismo pid antes y después, 14776), la bandera `SECURE` desaparece
  y la tecla Atrás desde la fachada sale al escritorio: el espacio interno **no
  queda en la pila de navegación**.
- **Cierre al pasar a otra app:** al ir a Inicio y volver a abrir Aurora, la app
  aterriza en la fachada, no donde se la dejó. También aquí el proceso seguía
  vivo, así que fue el pop del grafo seguro y no una muerte del proceso.
- **Cierre por inactividad (RNF-03):** se entró al panel y se dejó el emulador
  quieto, sin un solo evento de entrada, durante **5 minutos y 40 segundos**
  (09:24:02 → 09:29:42). Al comprobar, la app estaba **sola en la fachada** y la
  bandera `SECURE` había desaparecido. Hasta ahora esto solo estaba fijado por
  `SessionManagerTest` con tiempo virtual.
- **Reingreso silencioso (P-31 opción C):** tras el cierre, teclear solo la clave
  personal devuelve al panel sin volver a pedir correo ni contraseña.

**Observación de red, ya registrada.** El residual **P-12** anota que el texto
tecleado en la barra —incluido un código válido— viaja a la API de geocoding como
cualquier búsqueda, de modo que la indistinguibilidad es perfecta en la pantalla
pero no necesariamente en la red. La mitigación implementada es buena y conviene
cobrarla: la puerta verifica **offline** contra un directorio local de ~620
ciudades (`res/raw/city_index.txt`), exige mínimo 6 caracteres antes de siquiera
hashear, y la clave no puede ser el nombre de una ciudad real — con lo que un
código válido nunca coincide con una consulta que la fachada tuviera que
resolver. Está ratificado por la directora y cerrado.

MASTG-TEST-0017 (*Confirm Credentials*), 0018 y 0326–0330 (biometría) **no
aplican**: Aurora no usa biometría ni `KeyguardManager`, por decisión de
camuflaje —un prompt biométrico sobre una app de clima sería un distinguidor.

**Evidencia.** EV-13 [1][4][5][5·bis][6] · EV-06 [3][4] · EV-04 [8] · EV-05.

#### MASVS-AUTH-3 · «Las operaciones sensibles piden autenticación adicional» — **Pasa**

Las operaciones destructivas o de exportación exigen un segundo factor de
conocimiento, cada una con su prueba:

| Operación | Qué exige | Prueba |
|---|---|---|
| Cambiar la clave personal (RF-09) | la clave vigente | `ChangeSecretCodeUseCaseTest` (5) |
| Cambiar la contraseña | la contraseña actual | `ChangePasswordUseCaseTest` (10) |
| **Eliminar la cuenta** (RF-20) | re-autenticación con contraseña, antes de la purga remota | `DeleteAccountUseCaseTest` (9) |
| Exportar a PDF (RF-45) | una contraseña para el archivo | `ExportPolicyTest` (5), `JournalExportPipelineTest` (2, instrumentada) |

En Ajustes se vieron en pantalla las entradas correspondientes: «Cambiar tu
código de acceso», «Cambiar tu contraseña», «Eliminar todo y salir».

**Evidencia.** EV-05 · EV-13 [8] · ADR 2026-07-20 (RF-20) y 2026-10-03 (RF-45).

---

### 5.4 MASVS-PLATFORM — Interacción con la plataforma

#### MASVS-PLATFORM-1 · «Uso seguro de los mecanismos de IPC» — **Pasa con observación**

**Qué se probó.** Componentes exportados, proveedores de contenido, enlaces
profundos, intents implícitos y `PendingIntent`, con sondeo real desde `adb`.

**Resultado.** Aurora exporta **un solo componente propio**: `MainActivity`,
porque es el lanzador. No declara esquemas ni espera extras: lanzarla desde
fuera equivale a tocar el icono. El `FileProvider` es `exported="false"` y lo
confirma el sondeo:

```
adb shell content read --uri content://com.auroraclima.app.files/d/prueba.pdf
→ java.lang.SecurityException: Permission Denial: … not exported from UID 10207
```

Solo expone dos carpetas temporales de la caché (`media_pending/`, `doc_out/`),
nunca el almacén cifrado, y `ManifestMinimizationTest` fija exactamente esas dos.
De los intents que la app lanza, ninguno lleva datos de la usuaria en los extras
(MASTG-TEST-0374, que MASTG clasifica en MASVS-CODE): `ACTION_DIAL` lleva un
número del contenido **público**, y `ACTION_SEND` lleva una URI del FileProvider a
un PDF **cifrado**, con permiso de lectura puntual. No hay enlaces profundos
propios ni `PendingIntent`.

**Observación.** El manifiesto fusionado incorpora **dos actividades exportadas de
`firebase-auth`** con filtros `BROWSABLE` sobre esquemas propios
(`genericidp://firebase.auth/` y `recaptcha://firebase.auth/`), alcanzables por
cualquier app instalada o por una página web. Aurora no usa autenticación
federada. El sondeo real muestra que **no exponen nada**: tras lanzarlas, la
actividad en primer plano sigue siendo `MainActivity`, el proceso sigue vivo y no
se registra ninguna excepción. Queda como observación por superficie innecesaria,
no como fallo. Ver hallazgo **H-4**.

Residual ya conocido que se confirma: `ACTION_DIAL` deja el número pre-marcado
visible en el marcador y en su miniatura de recientes, fuera del alcance de
`FLAG_SECURE` (**P-26**, ya advertido en la propia interfaz).

**Evidencia.** EV-02 · EV-11 [1][2][3].

#### MASVS-PLATFORM-2 · «Uso seguro de WebViews» — **No aplica**

Aurora no usa WebView: 0 ocurrencias de `WebView`, `WebSettings`, `loadUrl` o
`addJavascriptInterface` en todo su código. En el DEX del release hay 3
referencias a `android.webkit.WebView` y 1 a `addJavascriptInterface`, todas
dentro de `com.google.android.recaptcha` (verificado en el código descompilado:
`recaptcha/internal/zzil.java`), que entra como dependencia de `firebase-auth`.
Es un WebView de Google que Aurora no configura ni alimenta.

**Evidencia.** EV-11 [4].

#### MASVS-PLATFORM-3 · «Uso seguro de la interfaz» — **Pasa con observación**

**Qué se probó.** Bloqueo de capturas, miniatura de recientes, enmascaramiento de
campos y notificaciones.

**Cómo.** En vez de mirar un PNG, se pidió el búfer **RGBA crudo**
(`adb exec-out screencap`, sin `-p`) y se contaron los píxeles no negros dentro de
la **región de la app** (y de 110 a 2290), excluyendo la barra de estado y la de
navegación, que son ventanas de SystemUI. Muestreo de 1 de cada 3 píxeles en cada
eje = 261 720 píxeles por captura.

**Resultado.**

| Pantalla | Píxeles | No negros | % |
|---|---|---|---|
| Fachada (control, antes de entrar) | 261 720 | 261 720 | 100,00 |
| Consentimiento (RF-17/18) | 261 720 | **0** | 0,00 |
| Panel del espacio interno | 261 720 | **0** | 0,00 |
| Editor de registro | 261 720 | **0** | 0,00 |
| Editor **+ selector de fecha abierto** | 261 720 | **0** | 0,00 |
| Ajustes | 261 720 | **0** | 0,00 |
| Fachada tras la salida rápida | 261 720 | 261 720 | 100,00 |

El caso del **selector de fecha** es el decisivo: es una **ventana propia**
(`SecureDayPicker`), y las ventanas propias no heredan la bandera de la Activity.
Con el selector abierto la captura sigue en 0/261 720, así que el envoltorio de
`core/security` sí le aplica la bandera. Es la verificación en vivo de la regla
de `ARQUITECTURA.md` §4 sobre Dialog, Popup, ModalBottomSheet y pickers, que
hasta ahora solo estaba fijada por `NoTracesGuardTest`.

Que **la fachada no lleve** `FLAG_SECURE` es correcto y deliberado: una app de
clima que no se dejara fotografiar sería, ella misma, un distinguidor.

**Miniatura de recientes.** Dos verificaciones:

- Desde la **fachada** con texto a medio teclear en la barra (RF-14, cierre de
  P-14): la tarjeta sale **en blanco** (EV-07c); sin teclear, la misma secuencia
  muestra la fachada normal (EV-07d). La segunda es el control que descarta que
  el blanco sea un artefacto. *Nota metodológica:* la primera captura de esta
  pareja se tomó en una corrida en la que la app se había caído (hallazgo H-2),
  así que el blanco podía deberse al crash; la prueba se repitió hasta obtener
  una corrida con el proceso **vivo** y sin excepción, y el resultado se mantuvo.
- Desde el **espacio interno**, estando en «Mi registro» con los registros
  listados: la tarjeta también sale en blanco (EV-13a). Aquí el peso
  probatorio no lo lleva la imagen —una tarjeta en blanco se ve igual venga de
  donde venga— sino los dos volcados tomados en el instante anterior a pulsar
  Inicio: la jerarquía de accesibilidad mostrando «Mi registro» con 4 registros
  listados, y `dumpsys window` mostrando `SECURE` en las banderas. Las tres
  capturas se publican sin recortar, para que cada una sea un artefacto propio
  distinguible por el reloj (EV-13 [3] explica por qué).

**Campos sensibles**: `V2SecretField` aplica `PasswordVisualTransformation` y
`KeyboardType.Password` en inicio de sesión, creación de cuenta, clave personal,
cambio de contraseña, eliminación de cuenta y exportación; en pantalla se vieron
enmascarados.
> **Errata (2026-10-10, H-9).** «La excepción» de la frase siguiente es inexacta:
> los campos enmascarados también ofrecían «copiar» **con el ojo de visibilidad
> abierto**, porque la anulación de Compose es condicional a la máscara. Lo que la
> evaluación midió —que en pantalla se vieron enmascarados— se sostiene; lo que no
> es que el relato fuera la única excepción. Ver §10.8.

**Observación (MASWE-0030):** la excepción es el campo del relato,
cuyo menú de selección sí ofrece «copiar» y «cortar» — es el hallazgo H-7, que
se califica en MASVS-STORAGE-2 por ser una fuga de datos, pero que nace de una
decisión de interfaz y por eso rebaja también este control. **Notificaciones**: ninguna. **Superposición** (MASTG-TEST-0035,
0340): no hay protección explícita, coherente con que MASVS-RESILIENCE quede
fuera y con que el modelo de amenaza no contemple una app maliciosa instalada a
propósito.

**Evidencia.** EV-13 [1][2][3] · EV-07a–d · EV-12 [2] (volcado de banderas de la
fachada) · `RecentsThumbnailGuardTest`
(6), `NoTracesGuardTest` (9), `SecureFlagLedgerTest` (5).

---

### 5.5 MASVS-PRIVACY — Privacidad

#### MASVS-PRIVACY-1 · «Minimiza el acceso a datos y recursos sensibles» — **Pasa**

Cuatro permisos en el manifiesto fusionado del release, **ninguno peligroso**:
`INTERNET` y `ACCESS_NETWORK_STATE` (ambos de nivel normal y exactamente los que
una app de clima necesita), más `READ_GSERVICES` de GMS y un permiso propio de
nivel *signature* que inserta AndroidX.

Permisos peligrosos: **cero**. Sin `CAMERA` —la cámara se usa lanzando un intent
al sistema—, sin permisos de almacenamiento —se usan el selector de documentos y
el selector de fotos—, sin ubicación —el clima se busca por nombre de ciudad—,
sin contactos, sin micrófono y sin notificaciones. No hay ningún permiso que
delate el propósito real, que es justo lo que piden RNF-04 y RNF-05.

Sin SDK de terceros que manejen datos personales más allá de Firebase Auth
(correo y contraseña, necesarios) y Firestore (ciphertext).

**Evidencia.** EV-02 [2] · EV-11 [5][6] · `ManifestMinimizationTest` (8).

#### MASVS-PRIVACY-2 · «Previene la identificación de la usuaria» — **Pasa con observación**

La app no lee el identificador de publicidad, ni `ANDROID_ID`, ni IMEI, ni
construye huella de dispositivo. Las referencias internas se generan con
`SecureRandomOpaqueIds` (16 bytes aleatorios). No hay analítica que pudiera
reutilizar una señal para otro fin, que es la preocupación central del control.

**Observación.** El operador de la nube ve **metadatos**, no contenido: el
identificador de documento es secuencial, hay marcas de tiempo de servidor, la
longitud del ciphertext aproxima el tamaño del relato y el número de trozos
aproxima el tamaño de una evidencia. Permiten inferir cuántos registros hay y
cuándo se crearon. Está todo registrado en **P-48c, P-62e, P-62i y P-62j**, con
su mitigación futura, y nada de eso es visible para el agresor del modelo de
amenaza, que tiene el teléfono y no la consola de Firebase.

**Evidencia.** EV-06 [5] · EV-11 [6] · `core/security/OpaqueIds.kt`.

#### MASVS-PRIVACY-3 · «Es transparente sobre la recolección y el uso de datos» — **Pasa**

`ConsentScreen` se muestra **antes** de crear la cuenta (RF-17, RF-18) y
`PrivacyScreen` queda accesible desde Ajustes. Verificado en pantalla: la
pantalla de consentimiento presenta «Antes de empezar, decides tú.» con tres
bloques —«Qué guardamos.», «Qué no hacemos.», «Tus derechos.»—, una casilla
explícita «He leído y acepto el tratamiento de mis datos personales» y dos
salidas, «Aceptar y continuar» y «No aceptar y volver al clima». El texto nombra
el derecho y la norma aplicable: *«Puedes consultar, corregir o eliminar todo
cuando quieras, desde Configuración. Tratamiento conforme a la Ley 1581 de
2012.»* En Ajustes hay además «Tu privacidad» y «Qué guardamos y por qué».

Todo ello está redactado para no delatar el propósito, que es la tensión propia
de esta app: la transparencia se cumple hacia la usuaria sin romper el camuflaje
hacia un tercero que mire la pantalla.

**Evidencia.** EV-13 [8] · `accounts/ui/ConsentScreen.kt`,
`accounts/ui/PrivacyScreen.kt`, `strings.xml:69` · `ConsentViewModelTest`.

#### MASVS-PRIVACY-4 · «Ofrece control sobre los propios datos» — **Pasa con observación**

| Control efectivo | RF | Prueba verde hoy |
|---|---|---|
| Editar y eliminar un registro | RF-35/36 | `UpdateJournalEntryUseCaseTest`, `DeleteJournalEntryUseCaseTest` |
| Descartar el resultado del cuestionario (el guardado es opt-in) | RF-31 | `GuidanceViewModelTest` (26), `DeleteSavedOutcomeUseCaseTest` |
| Cambiar la clave personal y la contraseña | RF-09 | `ChangeSecretCodeUseCaseTest`, `ChangePasswordUseCaseTest` |
| **Exportar todo a PDF cifrado** (portabilidad) | RF-45 | `BuildExportDocumentUseCaseTest`, `PdfBoxExportWriterTest` (16) |
| **Eliminar la cuenta completa** | RF-20 | `DeleteAccountUseCaseTest`, `OutcomeErasersTest`, `JournalRemoteEraserTest`, `JournalMediaEraserTest`, `PendingErasureRecoveryTest` |

Todos esos controles se vieron ofrecidos en Ajustes: «Cambiar tu código de
acceso», «Cambiar tu contraseña», «Cerrar sesión», «Eliminar todo y salir», más
«Exportar» en la pantalla de registros. La eliminación es el caso fuerte:
re-autenticación → purga remota **confirmada por el servidor** → borrado de los
alias del Keystore → marca durable que reanuda la purga si el proceso muere a
medio camino.

**Observación.** Dos residuales acotan ese control. **P-56** registra que tras el
borrado los XML de preferencias quedan **vacíos en vez de desaparecer**, que
`terminate()` deja la instancia de Firestore inválida hasta reiniciar el proceso
y que existe una ventana de milisegundos en la marca durable; esta auditoría
observó el síntoma sin buscarlo (`sync_meta.xml` presente y vacío, EV-06 [3]).
Y **P-55**, que sigue **pendiente de ratificación de la directora**, registra que
en release, tras eliminar la cuenta, volver a usar Aurora exige reinstalar,
porque la resiembra del código de activación solo existe en builds debug. Eso es
directamente un límite del «control sobre los propios datos»: la usuaria puede
irse, pero no puede volver sin ayuda.

**Evidencia.** EV-05 · EV-11 [7] · EV-13 [8] · ADR 2026-07-20.

---

### 5.6 MASVS-NETWORK — Comunicación en red (cobertura ligera)

#### MASVS-NETWORK-1 · «Asegura todo el tráfico de red» — **Pasa con observación**

`usesCleartextTraffic` no está declarado y `targetSdk` es 35, así que **la
plataforma bloquea el tráfico en claro por defecto**. En el código: 0
`TrustManager`, 0 `HostnameVerifier`, 0 `SSLContext`/`SSLSocket`, 0
`ConnectionSpec` que rebaje TLS, 0 sockets de bajo nivel, 0 URLs `http://`. El
cliente es el de OkHttp por defecto a través de Retrofit, sin interceptores. Los
extremos son `https://api.open-meteo.com`, `https://geocoding-api.open-meteo.com`
y los de Firebase. En el DEX, las únicas tres cadenas `http://` son espacios de
nombres XML/XMP y un literal `http://localhost` de librería.

**Observaciones.** Dos. La primera: no existe un archivo
`network_security_config` propio; la app se apoya en el valor por defecto de
`targetSdk`. Funciona hoy, pero es una garantía implícita. Ver hallazgo **H-5**.

La segunda, ya registrada como **P-12** y relevante aquí: lo que se teclea en la
barra viaja a la API de geocoding como cualquier búsqueda. La mitigación
—verificación offline contra un directorio local de ciudades, mínimo de 6
caracteres y prohibición de usar nombres de ciudad como clave— hace que un código
válido nunca llegue a convertirse en una consulta de red, pero el diseño depende
de esa regla y conviene decirlo.

**Evidencia.** EV-09 [1][2][3] · EV-03 [2].

#### MASVS-NETWORK-2 · «Fijación de identidad de los extremos propios» — **No aplica**

El control exige *pinning* para «todos los extremos remotos **bajo el control de
la persona desarrolladora**». Aurora no tiene ninguno: Open-Meteo es un servicio
público de terceros y Firebase es infraestructura de Google, que rota
certificados con frecuencia y desaconseja fijarlos. No hay pinning, y es la
decisión correcta.

---

### 5.7 MASVS-CODE — Calidad del código (cobertura ligera)

#### MASVS-CODE-1 · «Exige una versión de plataforma actualizada» — **Pasa con observación**

`targetSdk 35` (Android 15) es el más reciente, y es el que determina las
defensas que la plataforma aplica. `minSdk 26` (Android 8.0, 2017) amplía el
alcance del piloto a teléfonos de gama baja, que es un criterio explícito del
proyecto (A2-1), a cambio de un residual concreto ya registrado: entre API 26 y
32 no existe `setRecentsScreenshotEnabled`, de modo que allí la protección de la
miniatura degrada a mejor esfuerzo (**P-84**).

#### MASVS-CODE-2 · «Mecanismo de actualización forzada» — **No aplica**

El APK del piloto se entrega en mano a un grupo reducido; no hay tienda ni canal
de distribución donde forzar una actualización tenga sentido en TRL 4.

#### MASVS-CODE-3 · «Solo componentes sin vulnerabilidades conocidas» — **Pasa con observación**

Se extrajo el classpath **completo** de ejecución del release
(`releaseRuntimeClasspath`), se resolvieron las versiones finales y se consultó
cada coordenada Maven contra la API de **OSV.dev**:

> **166 artefactos consultados · 0 con vulnerabilidad conocida · 0 sin respuesta.**

**Observación, y es importante leerla.** La cobertura de OSV para artefactos de
AndroidX y de Google en Maven es parcial: un 0 significa «ninguna coincidencia en
la base», no «ninguna vulnerabilidad». Por eso se añadió el criterio de
actualidad, y ahí sí hay dos cosas que anotar:

- `androidx.compose.*` resuelve a **1.7.2**, de septiembre de 2024, cuando la
  línea estable actual va por 1.12/1.13. Esa distancia de dos años es la causa
  directa del hallazgo **H-2**.
- `com.tom-roush:pdfbox-android 2.0.27.0` no publica versiones desde enero de
  2023. Riesgo ya registrado en **P-75** y mitigado por construcción: Aurora solo
  **escribe** PDF y excluye BouncyCastle, así que no ejecuta el analizador
  sintáctico, que es donde viven los CVE históricos de PDFBox.

**Evidencia.** EV-09 [5].

#### MASVS-CODE-4 · «Valida y sanea toda entrada no confiable» — **Pasa**

- **SQL.** Todas las consultas son `@Query` de Room con parámetros ligados. Cero
  `@RawQuery`, cero `SupportSQLiteQuery`, cero `rawQuery`. Las dos únicas
  interpolaciones usan `$SINGLE_ROW_ID`, que es un `const val Int = 0` de Kotlin
  resuelto en **tiempo de compilación**, no una entrada. `execSQL` solo aparece en
  las migraciones, con DDL literal. No hay ContentProvider propio que exponga
  consultas, así que MASTG-TEST-0339 no aplica.
- **Deserialización.** `kotlinx.serialization` con `ignoreUnknownKeys = true`
  sobre DTO de tipos fijos; sin `Serializable`/`ObjectInputStream` de Java ni
  polimorfismo abierto (MASTG-TEST-0337 sin hallazgos).
- **Documentos de Firestore.** Parsing tolerante con validación de forma: un
  documento malformado degrada en lugar de romper
  (`ContentDocumentParsingTest`, `QuestionnaireDocumentParsingTest`).
- **Archivos adjuntos.** `AttachmentPolicy` impone lista blanca de 10 tipos MIME
  (PDF y audios), tope de 10 MB por documento y máximo 10 adjuntos por registro.
  Toda imagen se **re-codifica** a JPEG de calidad 85 con lado mayor de 2048, lo
  que elimina los metadatos EXIF —incluido el GPS— y cualquier carga útil
  escondida en el contenedor original.

**Evidencia.** EV-11 [8] · `AttachmentPolicyTest` (6), `ImageImporterTest` (5),
`ImageImporterRejectionTest` (2, instrumentada, ejecutada hoy).

#### Depuración desactivada en el release

Pedido dentro de MASVS-CODE, aunque MASTG lo archiva en MASVS-RESILIENCE-2
(`MASTG-TEST-0226`): la bandera `android:debuggable` **no está presente** en el
manifiesto del APK release (0 ocurrencias), ni `android:testOnly`. R8 elimina
además todas las llamadas a `android.util.Log` mediante `-assumenosideeffects`
(`app/proguard-rules.pro:60-68`, cubre `v/d/i/w/e/wtf/println`). ✅

#### Secretos en el código

`MASWE-0004`. Sobre las 55 368 cadenas del DEX del release: 0 llaves de API de
Google, 0 llaves privadas PEM, 0 llaves de AWS, 0 tokens de Slack o GitHub, 0
JWT, y **0 apariciones del código de activación del piloto** —que existe solo en
`src/debug`—. El único correo es `android@android.com`, una constante de
librería.

Los recursos **sí** contienen los identificadores de Firebase que inserta el
plugin `google-services` (`google_api_key`, `google_app_id`, `project_id`…). No
son credenciales: son identificadores públicos que lleva cualquier APK de
Firebase, y el control de acceso lo hacen las reglas de Firestore y Firebase
Auth, no el secreto de esos valores. Aun así, **los seis se redactaron también
en las evidencias**, incluido el identificador del proyecto, que ni siquiera
sería un secreto: ya figura en siete archivos versionados de este repositorio
—que además es privado— (`admin/.env.example`, `admin/README.md`,
`admin/src/firebase/config.ts`, `firestore/README.md`, `tools/admin/README.md`,
`docs/PENDIENTES.md`, `docs/RESULTADOS_PRUEBAS.md`).

**Evidencia.** EV-03.

---

### 5.8 MASVS-RESILIENCE — **No aplica** (los cuatro controles)

| Control | Justificación |
|---|---|
| RESILIENCE-1 · validar la integridad de la plataforma | Detectar root no reduce el riesgo del modelo de amenaza (agresor con acceso físico, no analista) y añadiría peso contra A2-1. |
| RESILIENCE-2 · antimanipulación | El APK se entrega en mano en un piloto controlado; no hay distribución abierta que proteger. |
| RESILIENCE-3 · antianálisis estático | Fuera de alcance como objetivo, **pero parcialmente satisfecho de hecho** por el camuflaje: R8 con ofuscación y reducción de recursos renombra la práctica totalidad de las clases propias a `a`, `b`, `c`… Su límite es el hallazgo H-1, y la excepción conocida son las tres bases de datos que el `keep` de reflexión de Room obliga a conservar (`JournalDatabase`, `GuidanceDatabase`, `WeatherDatabase`, con sus `_Impl`), de nombre neutro pero con la ruta de paquete completa — residual P-78. |
| RESILIENCE-4 · antianálisis dinámico | Mismo argumento que RESILIENCE-1; detectar depuradores o Frida no protege contra quien toma el teléfono desbloqueado. |

Nota adicional surgida de la prueba de firma: el APK release está firmado con el
**certificado de depuración** (`CN=Android Debug`), decisión documentada en
`app/build.gradle.kts` para poder instalar y probar el release ofuscado. Es
coherente con TRL 4, pero antes del piloto debe resolverse. Ver hallazgo **H-3**.

---

## 6. Hallazgos, ordenados por gravedad

Siete hallazgos: **uno alto** (H-7), dos medios, tres bajos y uno que la
verificación manual dejó cerrado. Ninguno es **crítico**. H-7 es el único por el
que puede salir contenido de una usuaria del espacio protegido; los demás no
exponen datos. Las etiquetas H-n siguen el orden en que se encontraron, no la
gravedad; aquí van ordenados de mayor a menor.

### H-7 · Alta · El relato de un registro puede salir del espacio protegido por el portapapeles

**Qué.** Mientras el registro se está escribiendo, el campo «Qué pasó» permite
seleccionar, **copiar y cortar** su contenido y pegarlo en cualquier otra
aplicación. Verificado a mano. Ya guardado el registro, la vista de detalle no
lo permite, así que la ventana es la de la edición.

**Por qué el código lo permite.** El campo es `V2ParagraphField`
(`core/ui/v2/Fields.kt:86`), y es **el único campo de texto del espacio interno
que no es de contraseña**: no declara `keyboardOptions` de ningún tipo. Todos
los demás campos sensibles usan `V2SecretField`, que aplica
`PasswordVisualTransformation` y `KeyboardType.Password`, y eso desactiva
«copiar» y «cortar» en la barra de selección de Compose y le dice al teclado que
no aprenda. El campo del relato no hereda nada de eso.
> **Errata (2026-10-10, H-9).** Esta frase se parte en dos y solo una mitad es
> cierta. La del teclado **sí**: `KeyboardType.Password` es incondicional y el
> método que decide el menú no consulta el tipo de teclado en ninguna rama
> (verificado: cero referencias a `KeyboardType`/`inputType`/`imeOptions` en
> `showSelectionToolbar`). La de «copiar» y «cortar» **no**: la da
> `PasswordVisualTransformation` y solo mientras está activa, y el ojo la apaga.
> Ver §10.8. Los campos de correo
(`V2PlainField` con `KeyboardType.Email`) están en el mismo caso, en menor
grado: el correo identifica la cuenta pero no describe lo ocurrido.

**Por qué es alta, y no media como el resto.** Es el **único hallazgo de esta
evaluación por el que el contenido de un registro —el dato más sensible de la
app— puede salir del espacio protegido hacia una superficie que el agresor del
modelo de amenaza alcanza sin root, sin descompilar y sin conocer la clave**:
basta con que tenga el teléfono, abra cualquier aplicación con un campo de texto
y pegue. A eso se suman dos agravantes:

- La **vista previa del portapapeles de Android 13+** es una ventana de SystemUI
  que `FLAG_SECURE` **no cubre**: aparece sola al copiar, en la esquina
  inferior, mostrando un fragmento del texto.
- El campo tampoco pide al teclado que no aprenda, de modo que el **diccionario
  personal del IME** puede quedarse con palabras del relato y sugerirlas después
  en otras aplicaciones. Esta segunda vía no se verificó —el teclado no aprendió
  la clave porque la barra sí lleva `autoCorrect = false`, protección que el
  relato no tiene— pero la diferencia en el código es la misma que explica el
  resultado de la barra.

**Corrección propuesta.** Retirar «copiar» y «cortar» del menú de selección en
`V2ParagraphField` —dejando «pegar», que la usuaria necesita—, y declarar en ese
campo que el teclado no debe aprender. Extender ambas cosas a los campos de
correo. Añadir una prueba que fije el contrato para que no se pierda en un
refactor.

### H-1 · Media · El DEX del release conserva más vocabulario propio del registrado, incluida la palabra «Secret»

**Qué.** El residual **P-78** ya registra que las plantillas del `toString()`
generado sobreviven a R8, y enumera diez ejemplos, sin dar una cifra total ni
declararse exhaustivo. La medición sobre el APK release pone número y añade dos
casos que no estaban:

- **109 plantillas** de `toString()` de *data class* propias, de las cuales **82
  son del espacio interno** (30 de `questionnaire`, 25 de `records`, 13 de
  `content`, 8 de `accounts`, 6 de `access`).
- Entre ellas, **`SecretCodeChecks(hasMinLength=`**, que contiene literalmente
  «Secret» — el término más revelador del vocabulario propio y el que toda la
  convención de nombres evita.
- Un **mensaje de excepción en español** que nombra un componente interno:
  `"LocalSessionManager sin proveer: envolver la NavHost raíz"`, de
  `core/security/SecureNavigation.kt:39`.
- Además, firmas de referencias de función de Kotlin con el nombre del método
  (`onKindSelected(…ResolvedKind;)V`).

Las rutas de paquete que conserva la reflexión de Room **ya estaban registradas**
en la ampliación de P-78 del 2026-10-05; esta medición las confirma, no las
descubre.

**Por qué importa.** La §2 de `docs/RESULTADOS_PRUEBAS.md` acota correctamente su
tabla («Nombre buscado en **clases propias**», con la herramienta
`apkanalyzer dex packages --defined-only`), de modo que la tabla no sobre-afirma.
La frase que sí va más lejos de lo que esa verificación sostiene es la conclusión
en negrita de las líneas 121-122: **«VERIFICADO — ningún símbolo nuevo legible en
el DEX»**. «Símbolo» abarca los literales de cadena, y ahí sí hay símbolos
legibles. Si el informe de grado reprodujera esa frase, afirmaría más de lo
comprobado.

**Gravedad: media**, y la razón no es el riesgo —el agresor del modelo de amenaza
no descompila— sino la exactitud de lo que se afirma y el hecho de que
`SecretCodeChecks` es justo el término que todo el diseño evita.

**Corrección propuesta.**
1. Cambiar la conclusión de `RESULTADOS_PRUEBAS.md:121-122` por «ningún **nombre
   de clase** nuevo legible en el DEX», y añadir el barrido de literales como
   verificación aparte.
2. Renombrar `SecretCodeChecks` → `CodeChecks` y `questionnaire` → `form` (la
   colección de Firestore ya se llama `forms`), que es lo que P-78 ya proponía.
3. Cambiar el mensaje de `SecureNavigation.kt:39` por uno sin nombres propios.
4. Ampliar `CamouflageResourcesTest` para que, además de los recursos, barra los
   literales del DEX del release buscando la lista de términos vedados.

### H-2 · Media · Excepción fatal reproducible al dejar la app con texto en un campo

**Qué.** Abrir Aurora, tocar la barra de búsqueda, teclear y pulsar Inicio mata
el proceso con `NullPointerException` en
`androidx.compose.foundation.text.input.internal.LegacyCursorAnchorInfoController.updateCursorAnchorInfo`.
Tasa medida: **6 caídas de 9 corridas** con texto tecleado; **0 de 1** sin tocar
nada y **0 de 1** con el campo enfocado pero vacío.

**Causa — no es código de Aurora.** El proyecto resuelve
`androidx.compose.foundation` a **1.7.2**: el BOM declara 2024.04.01 (1.6.6) pero
`navigation-compose 2.8.9` lo eleva. En esa versión el método desreferencia
`textFieldValue`, `offsetMapping` y `textLayoutResult` sin comprobar nulidad, y
`invalidate()` los pone en `null` al pausarse. El commit *«Prevent NPE in
LegacyCursorAnchorInfoController»* entró en `androidx-main` el **1 de octubre de
2024**, después de 1.7.2; la rama actual ya trae las seis comprobaciones.

**Impacto real, medido.** Menor de lo que parece:
- **El camuflaje NO se ve comprometido**: comprobado con captura, el sistema **no**
  muestra el diálogo «Aurora se detuvo», porque la app ya estaba en segundo plano
  al caer. La pantalla de inicio queda limpia; desde fuera es indistinguible de
  que el sistema liberara la app por memoria.
- **No hay fuga**: sin Crashlytics, la traza se queda en el dispositivo.
- El efecto es **pérdida de estado**: en la fachada, un arranque en frío al
  volver. En el espacio interno —misma ruta de código, no verificado— el texto no
  guardado de un relato largo se perdería.

**Corrección propuesta.** Subir el BOM de Compose a una versión cuya
`foundation` sea posterior al 2024-10-01 (o fijar `androidx.compose.foundation`
con un `constraint`), y repetir esta prueba: teclear en la barra + Inicio, diez
veces, esperando 0 caídas. Conviene hacerlo antes del piloto porque la misma ruta
sirve a todos los campos de texto de la app.

### H-3 · Baja · El APK release está firmado con el certificado de depuración

`apksigner` reporta `Signer #1 certificate DN: C=US, O=Android, CN=Android Debug`,
con esquema v2 únicamente. La decisión está documentada en
`app/build.gradle.kts:32-35` («TRL 4: se firma con la clave de depuración para
poder instalar y probar el release ofuscado»), y el comentario remite a
`PENDIENTES.md` — **pero no existe ninguna entrada allí sobre la firma de
producción**; se barrió por `firma`, `firmar`, `keystore`, `signing`, `.jks` y
`certificado` en las tres secciones del archivo. El riesgo concreto: el almacén
de claves de depuración usa una contraseña universalmente conocida y vive sin
protección en `~/.android/`, así que cualquiera que lo obtenga puede firmar una
actualización que el dispositivo aceptará como la misma app.

**Corrección propuesta.** Abrir la entrada que falta en `PENDIENTES.md` y, antes
de entregar un APK a una usuaria del piloto, generar un *keystore* propio,
guardarlo fuera del repositorio (`.gitignore` ya veda `*.jks` y `*.keystore`) y
firmar con esquemas v2 y v3.

### H-4 · Baja · Dos actividades exportadas de `firebase-auth` con esquemas navegables que la app no usa

`GenericIdpActivity` y `RecaptchaActivity` quedan exportadas con filtros
`BROWSABLE` sobre `genericidp://firebase.auth/` y `recaptcha://firebase.auth/`.
Aurora solo usa correo y contraseña. El sondeo muestra que no exponen nada, pero
son superficie que no hace falta y, de paso, revelan a quien enumere los filtros
de intent que la app usa Firebase Auth.

**Corrección propuesta.** Evaluar retirarlas en el manifiesto con
`tools:node="remove"`, exactamente como ya se hace con `PreviewActivity` en
`src/debug/AndroidManifest.xml`, y verificar después que el inicio de sesión, la
creación de cuenta y la recuperación de contraseña (RF-46) siguen funcionando —
Firebase puede usar reCAPTCHA como defensa antiabuso, así que esto hay que
probarlo, no asumirlo.

### H-5 · Baja · No hay configuración de seguridad de red explícita

La app se apoya en el valor por defecto de `targetSdk 35` para bloquear el
tráfico en claro. Es correcto hoy, pero es una garantía implícita: si algún día
alguien bajara el `targetSdk`, el bloqueo desaparecería en silencio.

**Corrección propuesta.** Añadir un `res/xml/network_security_config.xml` con
`cleartextTrafficPermitted="false"` y confianza solo en el almacén del sistema, y
declararlo en el manifiesto. Son cinco líneas, no pesa nada y hace explícita una
propiedad de la que depende la confidencialidad en tránsito.

### H-6 · Informativa · **Cerrado** · Las dos comprobaciones que la automatización no podía hacer, hechas a mano

Las dos vías que esta evaluación describió pero no pudo medir —`adb shell input
text` no pasa por el teclado, y este build del emulador no permite forzar una
copia— las verificó **a mano la desarrolladora sobre el emulador**, el
2026-10-10, y dieron resultados opuestos:

- **El teclado NO aprende la clave personal.** Tecleada en la barra de la
  fachada, Gboard no la ofrece después como sugerencia en otras aplicaciones.
  Esto **cierra el pendiente P-11**, abierto desde la auditoría D2: el
  `autoCorrect = false` de las dos barras (`WeatherScreen.kt:253` y
  `TutorialScreen.kt:356`) basta en la práctica, sin necesidad de
  `IME_FLAG_NO_PERSONALIZED_LEARNING`.
  > **Errata (2026-10-13, H-10).** Esta conclusión era falsa y su comprobación
  > miraba en el sitio equivocado. El teclado **sí** había aprendido la clave: lo
  > que no hacía era sugerirla *en otras aplicaciones*, que es donde se miró. La
  > sugería **dentro de Aurora**, en el buscador del clima, y así apareció cuatro
  > días después. `autoCorrect = false` no protege de nada —se midió en §10.1— y el
  > flag sí hacía falta. No se reescribe el párrafo porque es lo que la evaluación
  > concluyó; el hallazgo, su medición y la lección de método están en §10.7.
- **El relato SÍ se puede copiar.** En modo edición, el texto del campo «Qué
  pasó» se selecciona, se copia y se pega en otra aplicación. Una vez guardado
  el registro, la vista de detalle ya no lo permite.

El segundo resultado deja de ser una observación y pasa a ser el hallazgo H-7.

---

## 7. Relación con los riesgos residuales ya registrados

Esta evaluación **no encontró ningún riesgo residual nuevo sobre los datos de las
usuarias**: todo lo que toca confidencialidad, cifrado o aislamiento ya estaba
previsto en `docs/PENDIENTES.md`. Lo que sí aporta es medición, y además **cuatro
hallazgos nuevos que no estaban registrados** (H-2 a H-5), todos de higiene
previa al piloto.

| Residual en `PENDIENTES.md` | Qué aporta esta auditoría |
|---|---|
| **P-22** · caché de Firestore sin cifrar | **Acotado con número.** 213 coincidencias de contenido legible, **todas** en el contenido público; **0** en los documentos de la usuaria. |
| **P-48b** · los campos `payload`/`iv` revelan que hay cifrado | **Confirmado en el dispositivo:** son los dos únicos nombres de campo legibles, y aparecen en 20 de los 72 documentos de la cuenta. |
| **P-78** · plantillas de `toString()` legibles | **Ampliado** → hallazgo H-1: 109 plantillas, con `SecretCodeChecks` y un mensaje que nombra `LocalSessionManager`. |
| **P-32 / P-40 / P-44 / P-60f** · recursos legibles en el APK | **Cuantificado.** 391 claves de cadena; 9 940 caracteres de texto del espacio interno legibles en `resources.arsc`, con prefijos semánticos (`journal_` 83, `access_` 73, `account_` 62, `guidance_` 39). Términos neutros, como exige RNF-05. |
| **P-37a** · `libsqlcipher.so` revela que hay cifrado | **Confirmado.** La librería está en el APK para las cuatro ABI. |
| **P-37b / P-62 / P-70** · llaves ligadas al dispositivo, contraseña como raíz | **Confirmados, y P-62a con referencia exacta:** la única regla de contraseña es `length >= 8` (`CreateAccountUseCase.kt:33`). |
| **P-48c / P-62e / P-62i / P-62j** · metadatos en la nube | **Confirmados.** Identificadores secuenciales y recuentos visibles en la caché. |
| **P-33 / P-35 / P-62c** · material de llave en memoria | No probado a propósito (fuera del modelo de amenaza); se mantiene como aceptado. |
| **P-60a / P-60d** · copia en claro durante la captura y el visor | **No observables** en esta auditoría; motivan la observación de STORAGE-1. |
| **P-26** · `ACTION_DIAL` deja el número visible | **Confirmado** por revisión del código; ya advertido en la interfaz. |
| **P-84** · sin `setRecentsScreenshotEnabled` bajo API 33 | **Confirmado** como consecuencia de `minSdk 26`. |
| **P-75** · PdfBox-Android sin mantenimiento | **Confirmado**, y matizado: la exposición es nula hoy porque la app solo escribe y excluye BouncyCastle. |
| **P-12** · el texto tecleado viaja al geocoding | **Incorporado** a AUTH-2 y NETWORK-1; la mitigación (verificación offline, mínimo 6 caracteres, sin nombres de ciudad) se cobra como fortaleza. |
| **P-20** · código de activación único del piloto | Relevante para AUTH-2: una filtración erosionaría el camuflaje de toda la flota. Mitigado por entrega en persona. |
| **P-55 / P-56** · restos y reingreso tras eliminar la cuenta | **Motivan la observación de PRIVACY-4**; P-56(b) se observó sin buscarlo (`sync_meta.xml` vacío). P-55 sigue pendiente de ratificación. |
| **P-11** · aprendizaje del teclado | **CERRADO.** La verificación manual del 2026-10-10 confirmó que el teclado no aprende la clave personal: el `autoCorrect = false` de las dos barras basta. El campo del relato, que no lo lleva, pasa a ser parte de H-7. |
| **CP-37** · pruebas de `firestore.rules` | **CERRADA.** Ejecutadas por primera vez: 8 de 8 en verde. |
| **P-14** · miniatura de recientes | **Verificado en vivo** con prueba de control, desde la fachada y desde el espacio interno. |
| **RF-39** · recuperación multi-dispositivo | **Verificada en vivo** por accidente: tras perder el estado local, la cuenta se restauró desde la nube con solo la contraseña. |

| **Ninguno** · fuga del relato por el portapapeles | **Hallazgo nuevo, H-7, y no estaba previsto.** Es el único camino encontrado por el que contenido de un registro puede salir del espacio protegido dentro del modelo de amenaza. |

**Hallazgos nuevos que conviene registrar en `PENDIENTES.md`:** H-1 (como
ampliación de P-78), H-2 (crash de Compose 1.7.2), H-3 (firma de producción, la
entrada que el comentario del `build.gradle.kts` daba por existente), H-4
(actividades exportadas de `firebase-auth`), H-5 (configuración de red
explícita) y **H-7** (copiar el relato al portapapeles).

---

## 8. Conclusión

Sobre los **20 controles en alcance** de MASVS v2.1.0, evaluados con MASTG
v2.0.0:

| Resultado | Controles |
|---|---|
| **Pasa** | 7 — CRYPTO-1, AUTH-1, AUTH-2, AUTH-3, PRIVACY-1, PRIVACY-3, CODE-4 |
| **Pasa con observación** | 9 — STORAGE-1, CRYPTO-2, PLATFORM-1, PLATFORM-3, PRIVACY-2, PRIVACY-4, NETWORK-1, CODE-1, CODE-3 |
| **Falla** | **1** — STORAGE-2 |
| **No aplica** | 3 — PLATFORM-2, NETWORK-2, CODE-2 |

Los cuatro controles de MASVS-RESILIENCE quedan fuera de alcance, declarados
**No aplica** con la justificación de la sección 3.

Conviene leer ese único fallo en su contexto, porque no toca los cimientos. Los
dos pilares del diseño —el cifrado en reposo y el aislamiento de los datos en la
nube— resisten la verificación directa: las bases sensibles y sus registros de
escritura anticipada son indistinguibles de ruido aleatorio, las evidencias
adjuntas están selladas, las preferencias solo guardan base64 bajo claves
neutras, los siete PDF exportados llevan AES-256, no queda ningún token de
sesión en disco, el sistema responde «Backup is not allowed» y nunca ha
restaurado la app en sus 21 instalaciones registradas, y las reglas de
Firestore superaron sus ocho casos incluido el que impide que un rol
administrativo lea datos de una usuaria.

El tercer pilar, el camuflaje de la interfaz, también se verificó en vivo y de
forma medible: con `FLAG_SECURE` activo, la captura de pantalla del espacio
interno devuelve **0 píxeles visibles de 261 720**, incluida la ventana propia del
selector de fecha, mientras la fachada devuelve los 261 720; la miniatura de
recientes sale en blanco tanto desde la fachada con la clave a medio teclear como
desde el espacio interno con los registros en pantalla; la salida rápida
devuelve a la fachada sin dejar el espacio interno en la pila de navegación; y
el espacio interno se cierra solo tras cinco minutos sin actividad, verificado
con reloj.

**El fallo del único control que no pasa, y lo que significa.** MASVS-STORAGE-2
falla por H-7: el campo donde se escribe el relato de un registro permite
copiarlo al portapapeles, desde donde puede pegarse en cualquier otra
aplicación. Es el único camino que esta evaluación encontró por el que contenido
de una usuaria puede salir del espacio protegido **dentro del modelo de
amenaza** —sin root, sin descompilar, sin conocer la clave—, y por eso se
califica como el hallazgo de mayor gravedad. No es un defecto de diseño del
cifrado ni del camuflaje: es un campo al que no se le aplicó la misma protección
que a todos los demás, y la corrección cabe en unas pocas líneas.

De los siete hallazgos, uno es **alto** (H-7), dos **medios** —vocabulario propio
que sobrevive a la ofuscación, y una caída heredada de una versión vieja de
Jetpack Compose—, tres **bajos** de higiene previa al piloto, y uno quedó
**cerrado** por la verificación manual. Todos tienen una corrección concreta y
acotada, y cuatro de ellos (H-1, H-2, H-5 y H-7) se abordan en la versión
siguiente.

---

## 9. Sobre la auditoría interna de este informe

Siguiendo la convención del proyecto, la primera versión de este documento pasó
por el agente revisor de seguridad, que la **rechazó** con veinte correcciones.
Se consideran todas y se aplicaron las que procedían:

- **Trazabilidad.** Cinco afirmaciones (respaldos, banderas de ventana,
  portapapeles, opciones de teclado, grafo seguro) se enunciaban sin el artefacto
  que las producía. Se añadió **EV-12** con las salidas literales.
- **Verificación que faltaba.** Se señaló que tres controles descansaban solo en
  pruebas automáticas. Se ejecutaron las **45 pruebas instrumentadas** y se
  recorrió el espacio interno en vivo: **EV-13**.
- **Dos veredictos se rebajaron** a «Pasa con observación»: STORAGE-1 (por
  P-60a/d) y PRIVACY-4 (por P-55/P-56).
- **Dos afirmaciones se acotaron** por ir más lejos que la evidencia: «0 nombres
  de clase del espacio interno legibles en el DEX» —falso, por las tres bases de
  Room— y «no se encontró ningún riesgo residual nuevo», que contradecía los
  propios hallazgos H-2 a H-5.
- **H-1 se reescribió**: atribuía a la §2 de `RESULTADOS_PRUEBAS.md` un error que
  no comete, entrecomillaba una paráfrasis como si fuera cita, y presentaba como
  nuevo un punto ya registrado.
- **Residuales omitidos** que se incorporaron: P-12, P-48b, P-55, P-56, P-60a/d,
  P-60f y P-20.
- **Redacción de identificadores**: se recortaron los sufijos base64 que
  codificaban el identificador de aplicación de Firebase.
- **Cifras corregidas**: 3 coincidencias en `media_store` en vez de «0», 26 en
  vez de 24 pruebas de `GuidanceViewModelTest`, 63 en vez de 59 en AUTH-2, 14 de
  16 evidencias, «seis alias» en EV-04, y las preferencias con un contador entero
  además de los valores base64.

**Segunda pasada.** El informe corregido volvió al mismo revisor, que lo
rechazó de nuevo, esta vez por quince puntos más finos y concentrados en EV-12
—el archivo creado precisamente para cerrar la queja de trazabilidad—: un
bloque que prometía salida cruda y entregaba prosa, un literal entrecomillado
que su propia evidencia desmentía, una afirmación sobre los respaldos que el
volcado no sostenía tal como estaba recortado, y una nota cuya cronología no
cerraba. Se rehízo EV-12 entero con las salidas literales y las fechas reales;
se midieron cuatro PDF exportados en vez de uno —la tercera pasada mostró
que eran siete—; se corrigió el rango de
entropía a 7,45–8,00 declarando por qué el archivo más pequeño baja; se acotó
«uno por documento» a «20 de los 72»; se recortaron las tres capturas de
recientes al mismo encuadre de la tarjeta de Aurora; y se fijó el contexto de
EV-13a con el volcado de banderas del instante previo, para que una tarjeta en
blanco no dependa de que el lector crea desde dónde se tomó. En un punto el
revisor se equivocó y la corrección fue en sentido contrario al que proponía:
leyó que `Skip restore at install` correspondía al APK de pruebas, cuando en el
formato de `dumpsys backup` la línea `Package:` va **debajo** de su evento; el
volcado completo, emparejado evento por evento, da 21 de 21 para la app. En la
tercera pasada el propio revisor lo verificó contra el dispositivo y retiró el
punto.

**Tercera pasada.** El revisor volvió a rechazar, esta vez por tres
afirmaciones del paquete de evidencias que un jurado podía desmentir con un
comando, y acertó en las tres. (a) El recorte de las capturas de recientes que
se había hecho para atender una sugerencia suya dejó **dos archivos idénticos
byte a byte** con pies que afirmaban procedencias distintas: se revirtió a las
capturas sin modificar —que se distinguen por el reloj— y el peso probatorio se
trasladó explícitamente a los volcados de contexto. (b) EV-06 decía haber
medido «todos» los PDF exportados y medía cuatro de **siete**: se midieron los
tres que faltaban —entre ellos los dos de ~520 KB, los que llevan adjuntos
embebidos— y los siete pasan. (c) La sonda `service call clipboard 1` se había
interpretado como una lectura del portapapeles cuando en realidad es
`setPrimaryClip`, una escritura que el servicio rechaza: su «No items» es el
mensaje de la excepción de la propia sonda. Se retiró como prueba de estado y
el control quedó apoyado donde sí es sólido, en la ausencia total de
`ClipboardManager` en el código. En la misma ronda se añadió lo que el revisor
echaba en falta: el extracto crudo del monitor de respaldos, la fecha de
captura en cada evidencia, la lista completa de pruebas MASTG citadas, agrupada por
categoría —un recuento intermedio dio 54 porque contaba solo la forma larga del
identificador y se dejaba fuera las listas abreviadas de las cabeceras; la cifra
correcta, validada número a número contra el catálogo, es 87— y, sobre todo, **la captura de logcat dentro del
espacio interno**, que cierra MASVS-STORAGE-2 con evidencia dinámica donde
antes solo había la de la fachada.

**Verificación manual posterior.** Con el informe ya aprobado, la desarrolladora
ejecutó a mano las dos comprobaciones que ninguna automatización podía hacer, y
el resultado cambió el veredicto de un control. El teclado **no** aprende la
clave personal, lo que cierra P-11 y confirma que `autoCorrect = false` basta
en la práctica. Pero el relato de un registro **sí** se puede copiar al
portapapeles mientras se edita, lo que convirtió una observación en el hallazgo
H-7, hizo **fallar** MASVS-STORAGE-2 y rebajó MASVS-PLATFORM-3. Es el único
control que no pasa, y se corrige en la versión siguiente.

Que un hallazgo de este calado apareciera solo cuando una persona se sentó
delante del teléfono dice algo que vale la pena anotar en el capítulo de método:
la automatización cubrió mucho terreno —ochenta y siete pruebas del estándar,
medición de píxeles, entropía, logcat, reglas de la nube— pero el gesto más
cotidiano, seleccionar un texto y copiarlo, quedó fuera de su alcance.

Un punto de la primera pasada se revisó y **no** se aceptó como se planteó: señalaba
como crítico que EV-08 publicara la clave personal y la contraseña de la cuenta
de prueba. No era así — las cuatro cadenas de ese bloque son **sondas inventadas**
para la auditoría, elegidas por ser únicas en el sistema, y ni la clave ni la
contraseña reales se escribieron nunca en un archivo. La observación sí reveló un
defecto de redacción, porque el texto permitía esa lectura, y se corrigió
dejándolo explícito.

---

## 10. Re-verificación en v0.3.1

> **Los resultados de las secciones 1 a 9 no se tocan.** Describen lo que se midió
> sobre **v0.3.0-trl4** y deben quedar así, incluido el fallo de MASVS-STORAGE-2:
> es el estado que tenía la aplicación cuando se evaluó. Esta sección registra qué
> se corrigió después, cómo se verificó y cómo queda el recuento.

Rama `fix/hallazgos-owasp`, sobre el merge `9da851c`. Se abordan **H-2, H-7, H-1 y
H-5**; **H-3 y H-4** quedan para antes del piloto, por decisión expresa.

Esta sección va en su segunda redacción. La primera pasó por los tres agentes de
revisión del proyecto y **los tres la rechazaron**, con razón: el recuento no
cuadraba con el texto del propio informe, el guardián nuevo del DEX se podía
dejar en verde con la corrección deshecha, y el guardián de H-7 también. Lo que
se corrigió a raíz de esas tres revisiones está marcado abajo. El detalle del
procedimiento está en §9, que describe el mismo mecanismo aplicado al informe de
v0.3.0.

### 10.1 H-7 · El relato ya no sale por el portapapeles ni por el teclado

**Qué se cambió.** `core/security/SecureTextEntry.kt` añade `SecureTextEntry`, un
envoltorio que aplica dos protecciones a la vez para que no se pueda poner una y
olvidar la otra: una barra de selección de texto que delega en la del sistema
pasando `null` en «copiar» y «cortar» —«pegar» y «seleccionar todo» se
conservan— y `IME_FLAG_NO_PERSONALIZED_LEARNING` sobre el `EditorInfo`, vía
`InterceptPlatformTextInput`. Se aplica a **los tres campos del kit** —relato, correo y el enmascarado— y
**también a las dos barras de búsqueda**.

El envoltorio se aplica **dentro del componente del kit**, no alrededor de la
pantalla. No es un detalle de estilo: un `Dialog` instala su propia barra de
selección, así que el campo de correo de RF-46, que vive dentro de una hoja, se
quedaría sin protección si el envoltorio estuviera por fuera de la hoja.

**Por qué no se usó `autoCorrect = false`, que era el plan inicial.** Porque se
midió, y no sirve. El `EditorInfo` real, leído con `adb shell dumpsys
input_method`:

| Campo | `inputType` | `NO_SUGGESTIONS` | `NO_PERSONALIZED_LEARNING` |
|---|---|---|---|
| Relato, antes | `0x28001` = TEXT ∣ AUTO_CORRECT ∣ MULTI_LINE | no | no |
| Barra de búsqueda, con `autoCorrect = false` | `0x1` = TEXT, pelado | no | no |

Es decir: ese parámetro **solo retira** `TYPE_TEXT_FLAG_AUTO_CORRECT`; no añade
`NO_SUGGESTIONS` ni nada que hable de aprendizaje. En el relato habría costado la
autocorrección —en un campo de texto largo, donde más molesta— sin proteger nada.

Hallazgo derivado, que conviene anotar: **las dos barras tampoco tenían
protección mecánica contra el aprendizaje**. El comentario del código afirmaba
que `autoCorrect = false` «evita que el IME aprenda», y eso no es lo que hace ese
flag. La comprobación manual de H-6 salió bien, pero no por el motivo que el
código daba por supuesto. Por eso el arreglo se extendió a las barras, y por eso
los dos comentarios que afirmaban lo contrario se corrigieron donde estaban
—seguían en pie en la primera redacción de esta sección, y la resolución de P-11
también—.

**Verificación.** `EditorInfo` antes y después, sobre el emulador:

| Campo | `imeOptions` antes | después | delta |
|---|---|---|---|
| Relato | `0x42000000` | `0x43000000` | `0x01000000` = el flag, y nada más |
| Barra de la fachada | `0x2000003` | `0x3000003` | igual |

El `inputType` del relato no cambia (`0x28001`): **la autocorrección se conserva**.

**Lo que fijan las pruebas, en dos niveles.** La estructura, en la JVM, con
`SensitiveFieldsGuardTest`: que ningún campo editable se cree fuera del kit salvo
las dos barras exentas, que todo campo del kit esté envuelto, enmascare o no,
que el envoltorio instale sus dos protecciones y conserve «pegar», y que viva en
un único archivo. La lista de campos **se deriva de `Fields.kt`**, no se escribe a
mano, así que un `V2NumberField` futuro sin envolver caería sin tocar la prueba.

**Esa prueba se endureció cinco veces, y conviene contarlo porque es el hallazgo
más grave de la evaluación y su guardián falló cuatro veces por el mismo motivo:
enumeraba formas en vez de cerrar la especie.** Cada ronda la rompió una revisión
con mutaciones, no un razonamiento.

1. **Primera versión:** buscaba literales en todo el archivo. Bastaba vaciar
   `SecureTextEntry` —`NoCopyTextToolbar` seguía ahí, huérfana, y el `grep` la
   encontraba— o borrar el flag del IME, porque el KDoc lo menciona.
2. **Segunda:** las aserciones pasaron a rebanar el **cuerpo** de cada función, y
   la lista de campos a derivarse de `Fields.kt`. Caían las cinco mutaciones
   anteriores, pero seguían pasando tres formas: un campo `internal fun` —que es
   la convención de este código para los composables de pantalla—, un segundo
   archivo en el kit, y la exención por ruta en vez de por archivo.
3. **Tercera:** y aquí está la peor. Bastaba **una línea de comentario**: quitar
   `SecureTextEntry {` del relato y escribir
   `// pendiente: volver a envolver en SecureTextEntry { } al subir a Compose 1.8`
   dejaba el relato desnudo con el guardián en verde. Es exactamente el comentario
   que P-97 y esta misma sección invitan a escribir. La variante con KDoc era igual
   de mala: al rebanar el cuerpo de un campo sin enmascarar se tragaba el KDoc del
   siguiente, y si ese KDoc mencionaba `KeyboardType.Password`, el campo quedaba
   clasificado como enmascarado y excusado. Arreglo: `codigoDe()` descuenta los
   comentarios de bloque y de línea **antes** de analizar.
4. **Cuarta:** quedaban dos formas estructurales. Un composable con **receptor de
   extensión** o con **parámetro de tipo** no era una firma reconocible, y la
   comprobación del envoltorio miraba si abría *antes* del campo, no si el campo
   estaba *dentro* de su lambda — así que `SecureTextEntry { Spacer(...) }` con el
   campo a continuación, o un segundo campo tras cerrar la lambda, pasaban. Arreglo:
   la firma admite receptor y genéricos, y el rango de la lambda se calcula contando
   llaves, exigiendo que **todos** los campos del cuerpo caigan dentro.

5. **Quinta, y la que zanja el asunto.** La cuarta revisión encontró **otra** forma
   —un campo con la anotación en la **misma línea** de la firma,
   `@Composable fun V2NoteField(…)`, que ktlint permite— y lo demostró con un campo
   real sin envolver y el guardián en verde. A esa altura era evidente que enumerar
   formas no converge. Arreglo de fondo: una **aserción de atribución**. Cada campo
   de texto del archivo tiene que caer DENTRO de alguna función que el guardián
   reconozca; si una declaración se le escapa al regex, sus campos no se atribuyen a
   nadie y la cuenta no cuadra. Eso cierra la **especie** en vez de la forma, y se
   lleva por delante el `val` de tipo función lambda que hasta entonces figuraba
   como residual declarado. Comprobado con las dos mutaciones.

Cada una de las mutaciones de las cinco rondas se reprodujo contra la versión
siguiente, y todas caen, con la rama sin mutar en verde.

**Y hay que corregir aquí una afirmación que estuvo escrita en el código:** el
respaldo de lo que este guardián no vea **no** es el guardián del DEX. R8 borra
`SecureTextEntry`, `NoCopyTextToolbar` y `NoImeLearning` del artefacto —medido: 0
ocurrencias de los tres—, así que ese guardián no puede ver un campo sin envolver.
El respaldo es la comprobación manual **§8-S1**, y nada más.

El comportamiento, en el emulador, con `SecureTextEntryTest` (nueva, 2 casos):
compone el envoltorio, pide el menú de selección desde dentro y comprueba qué
recibe la barra real — «copiar» y «cortar» llegan en `null`, «pegar» y
«seleccionar todo» no. El segundo caso es el control: **sin** el envoltorio,
«copiar» sí llega, lo que demuestra que la protección es del envoltorio y no del
montaje de la prueba.

**Residuales que esta corrección NO cierra**, y que antes no estaban declarados
en ninguna parte:

- **Servicios de accesibilidad.** El envoltorio quita las opciones del menú, no
  las acciones semánticas: `CopyText` y `SetSelection` están en el DEX del release
  y el delegado de accesibilidad de Compose las expone, así que la cadena
  seleccionar → copiar sigue disponible para un servicio de accesibilidad sin
  pasar por la barra. **Alcance exacto, medido en la pasada final:** en
  `CoreTextField` esas acciones se instalan detrás del mismo booleano de
  enmascaramiento, así que en los 13 campos enmascarados aparecen **justo cuando el
  ojo se abre** — y en ese mismo instante el árbol de accesibilidad pasa de leer
  viñetas a leer el texto en claro. Es decir, el residual alcanza también a la clave
  personal y a las contraseñas, no solo a los campos que no pueden enmascararse. La lectura del árbol de accesibilidad tampoco la cubre
  `FLAG_SECURE` — este mismo informe se apoya en ese árbol como instrumento de
  prueba en §5.4. No se tapa con `clearAndSetSemantics` a propósito: dejaría al
  relato, el campo más largo de la app, sin lector de pantalla, y ese precio no lo
  paga el modelo de amenaza. Queda como **residual declarado (P-95)**, y es uno de
  los dos motivos por los que MASVS-STORAGE-2 no queda en «Pasa» limpio, y el
  único por el que MASVS-PLATFORM-3 tampoco (§10.8).
- **El menú contextual por clic derecho**, que no estaba declarado en ninguna
  parte y lo encontró la pasada final. `CoreTextField` envuelve el campo en
  `ContextMenuArea`, que en 1.7.8 **ya está cableado en Android**, dibuja su propio
  menú **sin consultar `LocalTextToolbar`** y decide su «Copiar» con el mismo
  booleano de enmascaramiento. El envoltorio no lo cubre. Solo lo dispara el botón
  secundario, así que hace falta un ratón y con el dedo es inalcanzable; en el
  teléfono del piloto no aplica. Pero alcanza al relato siempre, así que **el título
  de esta sección hay que leerlo acotado al menú táctil**. Y en Compose 1.8 esa vía
  deja de necesitar un ratón (§10.6).
- **`Ctrl+C` con teclado físico.** Compose lo atiende en `TextFieldKeyInput`, que
  llama a `copy()` **sin consultar la máscara**: o sea que en los campos
  enmascarados nunca estuvo bloqueado, ni con el ojo cerrado — un matiz que la
  pasada final corrigió y que refuerza la raíz de H-9. En el teléfono del piloto no
  aplica. El comentario del código decía «declarado en el informe» cuando no lo
  estaba;
  ahora lo está (P-95).

**Acoplamiento de versión.** `InterceptPlatformTextInput` existe desde Compose
1.7 y es API experimental; en 1.8 la firma de `showMenu` cambia y la barra de
selección se sustituye por la API de menú contextual nueva. **Cualquier subida de
Compose obliga a repetir la comprobación manual del menú**: ninguna prueba puede
verificar que el menú que dibuja el sistema operativo obedezca.

**Lo que falta, y solo puede hacerlo una persona:** confirmar en pantalla que el
menú de selección del relato ya no ofrece «copiar» ni «cortar» y sí «pegar».

### 10.2 H-2 · La caída al salir con texto

BOM de Compose de `2024.04.01` a `2025.02.00`, que lleva foundation y ui a
**1.7.8** y **material3 de 1.2.1 a 1.3.1** — este segundo salto no estaba
declarado en la primera redacción y lo señalaron dos de los tres agentes. Se
verificó contra los fuentes de Maven en qué versión entró el arreglo: 1.7.2 y
1.7.3 no tienen las comprobaciones de nulidad, **1.7.4 en adelante sí**. Se elige
1.7.8, la última de la línea 1.7.x, para no abrir el frente de Kotlin 2.0 que
obligaría 1.8.x.

**Verificación:** la prueba que fallaba 6 de 9 veces se repitió **10 veces en la
barra de la fachada y 10 en el campo del relato**: **0 caídas en las veinte**.

**Superficie:** el manifiesto fusionado del release tiene **una sola línea de
diferencia real** frente a `main` —el atributo de H-5— y **cero permisos y cero
componentes nuevos**; el APK crece 804 bytes. La subida no abre superficie de
seguridad. Lo que sí trae es **riesgo de regresión visual**: material3 1.3.1
cambia métricas y colores por defecto de componentes que la app usa —selector de
fecha, casillas de consentimiento y de exportación, interruptor, indicadores de
carga, y los campos del kit, que son justo las superficies que H-7 toca—. Por eso
entra una pasada visual en la lista de corridas manuales (§8 de
`docs/RESULTADOS_PRUEBAS.md`).

### 10.3 H-1 · Vocabulario propio en el DEX

`SecretCodeChecks` → `CodeChecks`, y el mensaje de `SecureNavigation.kt` pasa a
uno sin nombres propios. Sobre el DEX del release reconstruido: «SecretCodeChecks»
0 ocurrencias (antes 1), «LocalSessionManager» 0 (antes 1).

**Guardián nuevo:** la tarea de Gradle `verificarCamuflajeDex`. Corre en cada
construcción de release —`assembleRelease` y `bundleRelease`—. Precisión que pidió
la auditoría: lo que abre es siempre el **APK**, que `bundleRelease` construye al
lado por el `dependsOn`; el `.aab` no se abre, porque su `resources.arsc` va
troceado por configuración. Con minificación los dos artefactos toman el DEX del
mismo R8, así que hoy es equivalente, pero la frase exacta es «se juzga el APK del
release», no «ningún artefacto sale sin pasar por él». Conviene ser exacto sobre cuándo:
el comando corto de `CLAUDE.md` (`assembleDebug lint test`) **no** construye el
release y por tanto no lo ejecuta; el comando de verificación completa de la §1
de `docs/RESULTADOS_PRUEBAS.md`, que termina en `assembleRelease`, sí.

Es una tarea de Gradle y no una prueba de JVM porque el APK release puede no
existir cuando corren las pruebas, y un guardián que se salta en silencio es peor
que ninguno. A cambio, la lógica de juicio no sería ejecutable por un test, así
que **la tarea se autochequea en cada corrida**: juzga cuatro canarios —una
plantilla de `toString()`, una constante de enum, prosa en español y prosa con
tilde— y exige cazar los cuatro, y juzga tres cadenas inocentes de la fachada y
exige no marcar ninguna. Si el guardián deja de funcionar, se sabe entonces.

«En cada corrida» es literal y hay que declarar por qué: la tarea lleva
`outputs.upToDateWhen { false }`. Sin esa línea Gradle la marcaba UP-TO-DATE
cuando el APK y las dos listas no habían cambiado y **se saltaba el cuerpo
completo** —ni autochequeo, ni canarios, ni juicio—, de modo que esta misma
afirmación era falsa. Lo encontró la revisión de arquitectura de la rama. La tarea
tarda segundos: saltarla no compraba nada y costaba la garantía.

**Qué juzga, y esto cambió por completo tras la revisión.** La primera versión
miraba solo las cadenas «atribuibles a Aurora» —las que llevan la ruta del paquete
o la forma `Nombre(campo=`—, y dos de los tres agentes demostraron que ese filtro
era insuficiente y que la cifra que se publicaba era inexacta:

- de las 352 cadenas que la primera versión llamaba «atribuibles a Aurora», solo
  **24** llevaban la ruta del paquete; las otras 328 entraban por la plantilla
  genérica y en su mayoría eran de AndroidX y Compose. Presentar 352 como
  vocabulario propio de Aurora era, simplemente, falso;
- y el filtro dejaba fuera cadenas propias de verdad: `questionnaireRepository`
  está en el DEX, contiene un término vedado y no se juzgaba. **El propio mensaje
  de excepción que H-1 denunció tampoco lo habría cazado**, porque es prosa en
  español sin ruta de paquete.

Ahora **se juzgan TODAS las cadenas**, contra los **49 términos vedados** y
decodificando UTF-8 para que los términos acentuados puedan coincidir — con el
extractor anterior, que tomaba solo ASCII imprimible, una tilde cortaba la cadena
y «víctima», «agresión», «pánico» y «salida rápida» estaban estructuralmente
muertos.

Y no solo del DEX. La primera versión miraba únicamente `classes*.dex`, y
`resources.arsc` es justo donde `CamouflageResourcesTest` **no llega**:
`shrinkResources` y `optimizeResources` reescriben, y los recursos que traen las
librerías no están en las fuentes de Aurora y por tanto nunca se miraban. El
alcance ahora es todo lo que puede llevar texto propio: **DEX, `resources.arsc`,
el manifiesto binario, `res/**`, `META-INF/**`, `assets/**`** salvo las fuentes
que PdfBox trae vendidas, **más los dos archivos de raíz que genera este build** —
**197 de las 322 entradas** del APK. Cada categoría se midió antes de incluirla y
todas añaden **cero** ruido, así que entraron gratis.

Conviene decirlo como lo que es: una **lista de permitidos**, y el criterio es
«todo lo que este build pueda escribir», no «todo lo que no sea una librería». La
diferencia la encontró una auditoría con un `cmp`: la frase anterior decía que
todo lo excluido viajaba byte a byte desde una dependencia, y
`kotlin-tooling-metadata.json` es byte-idéntico al que el plugin de Kotlin genera
en `build/kotlinToolingMetadata/` a partir de la configuración de este proyecto.
Lo que queda fuera —125 entradas— sí viene de una dependencia: `lib/**` (las
librerías nativas de SQLCipher), `google/**` (los `.proto` de Firestore y gRPC),
`kotlin/**`, `okhttp3/**`, las fuentes y listas de glifos de PdfBox bajo
`assets/com/tom_roush/**`, y los `.properties` de metadatos que cada librería deja
en la raíz. Juzgarlas exigiría **43 excepciones más**, varias de ellas basura
binaria que no es estable entre construcciones.

**Cuatro entradas del APK no vienen de ninguna dependencia** y por eso están
DENTRO del alcance, no fuera: `assets/dexopt/baseline.prof` y su `.profm`, que se
derivan de las clases de Aurora; `META-INF/version-control-info.textproto`, que se
deriva del repositorio; y `kotlin-tooling-metadata.json`. Las dos primeras
versiones del filtro las excluían sin decirlo, y hicieron falta dos auditorías
para enumerarlas. Al medirlas resultó que incluirlas no cuesta nada: los perfiles
están comprimidos y no tienen ni un nombre legible, el `textproto` lleva solo la
ruta raíz del proyecto y el hash del commit sin nombre de rama, y el `.json` solo
versiones de Gradle y de Kotlin.

Y porque «se barrió a mano una vez» no es una garantía permanente, el guardián
lleva además una **línea base de las categorías excluidas**: si mañana aparece en
el APK una familia de entradas que nadie juzga —un `assets/` propio con un catálogo
sembrado, por ejemplo, que es verosímil porque el proyecto ya siembra contenido—,
la construcción del release **falla** y obliga a decidir si entra en alcance o se
excluye con motivo. Es el mismo arreglo que el `upToDateWhen { false }`, un nivel
más arriba: lo que no se mira, se declara.

**Medición, atada al artefacto.** Sobre el APK release reconstruido en limpio con
el JDK prescrito (JBR 21.0.4) y sin ningún otro demonio de Gradle activo —
`app-release.apk`, 23 952 390 B, SHA-256
`36bff7a36aaa63d836e3b9d18245ef2f503f3fea4ce36e2e895133fdd31673d2`—: **390 627
cadenas de 3 o más caracteres, 148 364 distintas, en 197 entradas, 0 hallazgos
nuevos** y el autochequeo de canarios en 4/4.

El piso es de **3** caracteres y no de 4 por un motivo concreto: de los 49
términos vedados, «sos» mide 3, así que con el piso anterior una cadena que fuera
exactamente «sos» era estructuralmente inalcanzable — la misma especie de hueco
que el de los términos acentuados.

**Y ese cambio hubo que corregirlo, lo que vale la pena contar.** Se midió sobre
una construcción y se concluyó «cuesta 0 hallazgos»; lo que costaba no era
hallazgos, era **estabilidad**: un término de tres letras casa dentro de casi
cualquier cosa —«sos» vive en «ISOSpeed» y en basura binaria como `\sOSUpPS9`— y
esas cadenas cambian entre construcciones, así que el release empezó a fallar al
azar. La regla de ahora: **un término de tres letras solo cuenta si no está pegado
a otra letra**. Con eso «SOS» suelto se caza, «ISOSpeed» no, y las cuatro
excepciones que hacían falta por esa causa se retiraron — de 62 a **58**. El
precio, dicho claro: un `sosBoton` propio se le escaparía a ESTE guardián, pero no
queda sin vigilar, porque `CamouflageResourcesTest` aplica los dos ámbitos a los
identificadores y literales de las fuentes, que es donde ese nombre nacería.

De paso se afinó el detector de excepciones innecesarias, que medía *presencia* en
vez de *necesidad*: ahora comprueba si, quitándola, el guardián reportaría algo. Sin
ese afinado, las cuatro de `ISOSpeed` habrían seguido contando como vivas.

Las cifras van atadas al SHA-256 a propósito, igual que en §2, y conviene decir
exactamente qué es reproducible y qué no, porque se midió:

- **El APK no es reproducible byte a byte.** Dos construcciones limpias e
  independientes del mismo commit, con el mismo JDK, dan hashes distintos
  (se midieron **ocho** a lo largo de la evaluación, el último `b21ea4…`) con el
  **mismo tamaño** de 23 952 390 B en todas. Es lo esperable
  de R8 y de la firma.
- **Las cifras del guardián sí son estables.** Cinco construcciones limpias e
  independientes —propias, de la auditoría y de la verificación— dieron **los nueve
  números idénticos al dígito** con cinco hashes distintos. Es decir: el recuento es
  propiedad del commit, y el hash no. (Las cifras que publica esta sección son
  mayores porque después se amplió el alcance; con un alcance fijo no se movieron.)
- **Lo que sí movió las cifras fue contaminación del entorno.** Antes de limpiar,
  dos construcciones del mismo commit dieron 167 564 y 168 992 cadenas; la causa
  era un demonio de Gradle bajo otro JDK construyendo en paralelo sobre el mismo
  directorio. Dos demonios sobre el mismo `app/build` también corrompen el estado
  incremental. **Para re-medir: detener los demonios, `clean`, y una sola
  invocación.**

Quien reejecute el comando **sobre el mismo commit** obtendrá otro SHA-256 y las
mismas cifras. Si el código cambia, las cifras se mueven — y eso es lo correcto.

El precio de juzgar todo es absorber el ruido de las librerías en la línea base, y
eso es lo que hace creíble un «0 hallazgos»: **58 cadenas aceptadas, una por una y
con su causa escrita**. De ellas 9 son de Aurora y llevan pendiente (todas de
P-78, y desaparecen con el rename `questionnaire`→`form` y `Consent*`→`Terms*`);
las 49 restantes son de librería —`javax.crypto` para «secret», `ISOSpeed` de EXIF
para «sos», gRPC para «panic», y dos recursos del arsc: una cadena italiana del
selector de fecha de material3 y un estilo de `androidx.credentials`— y no hay
nada que arreglar en código ajeno. El guardián informa además de las excepciones
**que nunca se disparan**: una excepción muerta es o una causa ya arreglada, o el
síntoma de que el guardián dejó de mirar donde esa excepción mira. Hoy son 0, y en
la versión anterior había una.

**Fuente única de términos, de verdad.** La primera versión creó
`camuflaje-terminos-vedados.txt` declarando ser «fuente única para los dos
guardianes», y no lo era: `CamouflageResourcesTest` conservaba su lista embebida.
Las dos **ya habían divergido**: 49 términos en el test, 42 en el archivo. Los
siete que faltaban eran precisamente los más propios del proyecto —entre ellos
«quick exit»—, y de ahí salió el hallazgo H-8 de más abajo. Ahora el test lee el
archivo y nadie tiene copia; los dos ámbitos (`[todo]` y `[recursos]`) van
marcados en el propio archivo. Las dos listas se movieron a
`app/src/test/resources/`, fuera de la vista al abrir el módulo.

**Barrido nuevo en el código fuente.** `CamouflageResourcesTest` gana un tercer
caso que aplica la lista completa a los **identificadores que sobreviven a R8**:
nombres de `data class` y `data object` —cuya plantilla de `toString()` llega
legible al DEX— y constantes de enum, que R8 conserva porque `name` y `valueOf`
son reflexión. Barre 169 identificadores y señala 3, los mismos de P-78 que están
en la línea base del DEX. Los demás nombres de clase no se barren porque R8 sí los
protege, lo que P-05 verificó: `ValidateSecretCodeUseCase` es legítimo.

**Qué congela la línea base, dicho sin rodeos.** De las ~109 plantillas de
`toString()` que H-1 midió, esta rama retiró **una**. El resto sigue legible en el
DEX y la corrección de fondo —el rename de P-78— sigue pendiente. El guardián
impide que el problema crezca; no lo resuelve.

**Lo que el guardián no cubre.** Dos cosas, y la segunda es la mayor exposición
de vocabulario que queda en el artefacto.

La primera: caza vocabulario vedado, no **nombres propios internos**. La segunda
cadena de H-1, `LocalSessionManager sin proveer…`, no la habría detenido, porque
«LocalSessionManager» no es un término de la lista. Ese caso se arregló a mano y la
categoría queda sin guardián automático. Tampoco puede ver un campo de texto sin
envolver, porque R8 borra el envoltorio del artefacto (§10.1).

La segunda: **la prosa de la interfaz del espacio interno, que no contiene ningún
término vedado pero sí es vocabulario del dominio.** Lo encontró la cuarta auditoría
barriendo el APK con un léxico más amplio que los 49 términos: `strings` sobre el
artefacto devuelve «Rutas de atención», «Salud · Protección · Justicia», «Qué pasó»
y las cadenas de «tu clave personal». Ninguna rompe una regla escrita —todas las
afirmaciones de este informe están acotadas a los 49 términos— y no es una
regresión de esta rama, pero en Colombia esa prosa **es** del dominio y nadie la
había registrado. Queda como **P-100**, con la pregunta de producto que abre: si las
etiquetas más específicas deberían venir del catálogo remoto, como ya viene el resto
del contenido de RF-24/25, en vez de vivir en `strings.xml` y viajar en el APK.

### 10.4 H-5 · Configuración de seguridad de red

`res/xml/network_security_config.xml` con `cleartextTrafficPermitted="false"` y
confianza solo en el almacén del sistema, declarado en el manifiesto y fijado por
tres casos de `ManifestMinimizationTest`: la declaración, el contenido, y —añadido
tras la revisión— que **ningún** elemento del archivo permita tráfico en claro,
porque los dos primeros solo miraban `base-config` y un `domain-config` puede
reabrir HTTP para un dominio sin tocarlo.

**Corrección de la justificación.** La primera redacción decía que sin la línea de
`trust-anchors` la app habría permitido leer el tráfico con una autoridad
instalada a mano. **Eso no es cierto para esta app**, y lo señaló la auditora:
desde Android 7 (API 24), una app con `targetSdk >= 24` ya confía solo en el
almacén del sistema, y Aurora es min 26 / target 35. Las dos declaraciones de este
archivo valen lo mismo y por el mismo motivo —hacen explícito un valor por defecto
para que no se pueda perder en silencio al bajar el `targetSdk`—, pero **ninguna
cerró un hueco abierto**. La afirmación incorrecta estaba repetida en cuatro
sitios, incluido el mensaje de aserción de la prueba; se corrigieron los cuatro.

No hace falta declararla para `debug`: **ya aplica**, porque el atributo está en el
manifiesto principal. Verificado en el manifiesto fusionado de debug. Y queda la
nota operativa de que, sin `<debug-overrides>`, una prueba legítima de
interceptación TLS (MASVS-NETWORK-2) exigirá añadir ese bloque de forma temporal;
dejarlo fuera ahora es lo correcto para este modelo de amenaza.

Verificado sobre el APK release decodificado: el manifiesto fusionado lleva el
atributo y el archivo viaja empaquetado. Y comprobado que la red sigue
funcionando: la búsqueda de «Bucaramanga» devuelve resultados reales de
Open-Meteo, con 0 errores de TLS en el registro.

*Nota, porque ilustra que los guardianes del proyecto funcionan:* la primera
versión de ese archivo fue rechazada por `CamouflageResourcesTest`, que detectó
un término vedado en un comentario que había escrito la propia corrección.

### 10.5 H-8 · Hallazgo nuevo · Media · «quick exit» legible en el DEX del release

No es un hallazgo de la evaluación sobre v0.3.0: **apareció al arreglar H-1**, en
cuanto el guardián empezó a comprobar los 49 términos en vez de 42. El DEX del
release contenía `QUICK_EXIT`, `quickExit` y `quickExit()V` —la constante del enum
de pasos del tutorial y el método de `SessionManager`—, legibles pese a R8: R8
conserva los nombres de las constantes de enum porque `name` y `valueOf` son
reflexión.

**Por qué importa más que otras cadenas.** «quick exit» lo vedó por escrito una
auditoría anterior (D3 H-1) por ser *el nombre canónico en inglés de los botones de
escape en las apps de este dominio*. De todo el vocabulario que podía quedar en un
artefacto, es el distinguidor más directo. **Gravedad media**: verlo exige extraer
el APK y pasarle `strings`, pero el modelo de amenaza del proyecto sí contempla a
alguien con acceso al teléfono, y lo que delata es el vocabulario (RNF-05).

**Corrección.** `SessionManager.quickExit()` → `closeNow()` y el paso
`TutorialStep.QUICK_EXIT` → `CLOSE_NOW`; 25 referencias en 12 archivos, 7 de
código y 5 de pruebas. `closeNow` encaja además con `closeSession()`, que ya era
el nombre de la rutina de cierre. Los dos sitios de definición llevan ahora un
KDoc que explica por qué el nombre es deliberadamente soso, para que un refactor
«de limpieza» no lo deshaga; `CodeChecks` recibió el mismo tratamiento.

**Verificación**, sobre el DEX del release reconstruido: `quickExit` **0**
ocurrencias (antes 2), `QUICK_EXIT` **0** (antes 1), y en su lugar `closeNow` (2)
y `CLOSE_NOW` (1), que son vocabulario neutro. El guardián del DEX pasa con 0
hallazgos. El nuevo caso de `CamouflageResourcesTest` sobre identificadores lo
detiene en las formas habituales de declararlo —incluidas la constante con
argumentos de constructor y el enum anidado en una clase, las dos comprobadas—,
pero no en todas: un cuerpo de enum escrito **en una sola línea**
(`enum class Paso { ENTRAR, CLOSE_NOW }`) se le escapa, porque el barrido exige
que la constante empiece una línea, y lo mismo una constante que no vaya en
mayúsculas. Por eso no se puede decir que «impide que vuelva». La red que no
depende de la forma de escribir el código es el guardián del DEX, que mira el
artefacto.

### 10.6 H-9 · Hallazgo nuevo · Alta · Los campos enmascarados sí se podían copiar con el ojo abierto

No es un hallazgo de la evaluación sobre v0.3.0 **y tampoco es una regresión de esta
rama: estaba ahí desde v0.3.0 y la evaluación no lo vio.** Conviene decirlo así,
porque es un resultado sobre el método tanto como sobre la app.

**Qué.** `V2SecretField` —el campo enmascarado del kit, con 13 usos: clave personal,
contraseñas de cuenta y contraseña de exportación— ofrecía «copiar» y «cortar» en su
menú de selección **mientras el ojo de visibilidad estuviera abierto**. Es la vía (a)
de H-7, el portapapeles, sobre los datos que gobiernan el acceso al espacio interno.

**Por qué, medido y no inferido.** Se desensambló
`TextFieldSelectionManager.showSelectionToolbar` de foundation 1.7.8: en el
desplazamiento 36 lee `visualTransformation`, en el 39 hace
`instanceof PasswordVisualTransformation`, y solo si eso es cierto salta a
`aconst_null` para «copiar» (74) y para «cortar» (114). Es decir: Compose no anula
nada por el `KeyboardType`, lo anula **mientras la transformación visual ES de
contraseña**. Y `V2SecretField` la cambia a `VisualTransformation.None` al pulsar el
ojo. Dos comprobaciones colaterales de la misma medición:

- **«pegar» nunca consulta ese booleano** —su rama arranca en `getEditable` y
  `hasText`—, así que conservarlo no depende de nada frágil;
- el método **no consulta el tipo de teclado en ninguna rama**, lo que confirma que
  la mitad del IME sí se sostenía: `keyboardOptions` fija `KeyboardType.Password`
  sin condición.

**Lo que agrava el dato.** El portapapeles de Compose escribe con
`ClipData.newPlainText` **sin `EXTRA_IS_SENSITIVE`**, así que lo copiado entraba sin
marca: queda a la vista en la previsualización de SystemUI de Android 13+ —ventana
que `FLAG_SECURE` no cubre, el mismo argumento de H-7— y en el historial del
teclado.

**Gravedad: Alta**, en la misma escala que H-7 y por los mismos motivos. Alcanzable
con el dedo, sin herramientas ni periféricos; sobre la clave personal, que es la
credencial que protege todo lo demás; y en la contraseña de exportación, copiar era
incluso la acción **esperada**. Atenúa que exige una acción deliberada de la usuaria
y haber pasado la clave, pero eso valía igual para H-7.

**Cómo se encontró, que es la parte que interesa al método.** Verificando una
premisa en vez de creerla. La premisa estaba escrita en el código —«los campos de
contraseña ya estaban cubiertos por `KeyboardType.Password`, que hace las dos cosas
de un golpe»— y de ella colgaba, además, la **exención** con que
`SensitiveFieldsGuardTest` perdonaba del envoltorio al único campo del kit que no lo
tenía. Es la **segunda** vez en esta rama que una medición desmiente un comentario
del código: la primera fue `autoCorrect = false` en §10.1. Y hay una diferencia que
vale anotar: **H-8 apareció porque un guardián se volvió más estricto; H-9 llevaba
ahí desde v0.3.0 y ninguna herramienta lo vio.** Lo vio una persona leyendo
bytecode.

**Corrección.** `V2SecretField` pasa también por `SecureTextEntry`, que anula copiar
y cortar **sin leer ningún estado**, con el ojo abierto o cerrado. El kit queda con
una regla y ninguna excepción. Dos apoyos de que es el mecanismo canónico: el propio
`BasicSecureTextField` de Compose compila a la misma forma
—`showMenu(rect, null, paste, null, selectAll)`— y el menú de Compose no tiene otros
platos que esos cuatro, porque no inyecta «Compartir» ni actividades `PROCESS_TEXT`
como el `EditText` del framework de vistas.

Y el guardián pierde su único perdón calculado. La lección, que trasciende el caso:
**lo calculado puede ser el juicio, nunca la exención** — una exención se escribe
como dato con su causa, para que alguien pueda preguntarle «¿siempre?».

**Coste aceptado, por decisión expresa:** tampoco se podrá copiar la contraseña de
exportación que la usuaria elige. El argumento, suyo: copiarla para enviarla es justo
lo que la pondría en el portapapeles. «Pegar» se conserva en los 13 campos, así que
un gestor de contraseñas sigue pudiendo rellenar. Queda un corolario de usabilidad
registrado en **P-105**: sin «copiar», transcribir una contraseña larga empuja a
elegirla más corta.

**Residual nuevo que el envoltorio NO cubre, y que no estaba declarado:** el **menú
contextual por clic derecho**. `CoreTextField` envuelve el campo en `ContextMenuArea`
y en 1.7.8 eso **ya está cableado en Android** —no es un no-op: se verificó que
`classes.dex` conserva sus cadenas, que R8 habría borrado si fuera inalcanzable—.
Dibuja su propio menú **sin consultar `LocalTextToolbar`**, con el «Copiar» colgando
del mismo `!isPassword`. Solo lo dispara el botón secundario
(`awaitFirstRightClickDown`), así que hace falta un ratón y con el dedo es
inalcanzable; en el teléfono del piloto no aplica. Pero alcanza también al relato,
donde `isPassword` es falso siempre, así que el titular de §10.1 hay que leerlo
acotado al menú táctil. Y conviene saber que **en Compose 1.8 esa vía deja de
necesitar un ratón**, porque el menú contextual pasa a ser el camino principal.

**Medición del `EditorInfo` sobre el campo enmascarado real**, que faltaba —en §10.1
solo estaban medidos el relato y la barra— y que cierra la pregunta de si envolver un
campo de contraseña pisa algo. Hecha sobre el emulador, en «Cambiar tu código de
acceso», con `adb shell dumpsys input_method`:

| Estado del ojo | `inputType` | `imeOptions` | Árbol de accesibilidad |
|---|---|---|---|
| Cerrado | `0x8081` | `0x3000005` | `text="••••••"`, `password=true` |
| **Abierto** | `0x8081` | `0x3000005` | **`text="abc123"`**, `password=false` |

Dos lecturas, las dos importantes:

1. **El envoltorio no pisa nada.** `0x8081` conserva la variación de contraseña
   (`0x80`), e `imeOptions` lleva el `0x01000000` del envoltorio junto a la acción
   `NEXT`. Y es **idéntico con el ojo abierto y cerrado**: la mitad del teclado es
   incondicional, como decía la medición del bytecode.
2. **Y confirma el residual de accesibilidad de forma empírica**, no inferida: al
   abrir el ojo, el árbol pasa de `••••••` a **el texto en claro** y `password` de
   `true` a `false`. Lo que hasta ahora era un argumento sobre dónde se instalan las
   acciones semánticas es ahora una observación: con el ojo abierto, un servicio de
   accesibilidad lee la clave personal. Está en P-95.

**Lo que falta, y solo puede hacerlo una persona:** el paso **§8-S1b**, que comprueba
el **menú** de un campo enmascarado con el ojo abierto. Ninguna prueba automática
puede verlo: la instrumentada compone el envoltorio con una barra falsa, no un campo
real con la máscara apagada, y la medición de arriba mide el `EditorInfo`, no el
menú.

### 10.7 H-10 · Hallazgo nuevo · Alta · El teclado ofrecía la clave personal dentro del propio buscador del clima

Lo encontró **la usuaria**, en la corrida manual §8-S4, y lo trajo con captura: al
escribir «aud» en la barra de búsqueda de la fachada, Gboard ofrecía
`auditoria77` —la clave personal de la cuenta de prueba— en su tira de
sugerencias. Dentro de Aurora. Es el hallazgo más directo de toda la evaluación:
no hace falta descompilar nada ni tener herramientas; basta tomar el teléfono,
abrir la app del clima y empezar a teclear.

**Conviene separar dos cosas que es fácil confundir, porque la corrección de cada
una es distinta:**

| | Aprender | Sugerir |
|---|---|---|
| Qué es | que el teclado incorpore a su diccionario lo que se teclea | que el teclado ofrezca lo que ya tiene en el diccionario |
| Qué lo controla | `IME_FLAG_NO_PERSONALIZED_LEARNING` (H-7) | `TYPE_TEXT_FLAG_NO_SUGGESTIONS` |
| Estado antes de H-10 | **cerrado** desde H-7 | **abierto** |

La propia S-4 lo confirma: las palabras inventadas que la usuaria escribió **ese
día** en el relato y en la barra **no** se aprendieron. Lo que aparecía era una
palabra aprendida **antes**.

**Dónde se aprendió, con fecha.** La clave `auditoria77` se creó la mañana del
2026-10-10, durante la recuperación de la cuenta de prueba tras el incidente de
`connectedDebugAndroidTest`. La corrección de H-7 —la que añade el flag a las dos
barras— entró ese mismo día a las **14:37** (commit `a329245`). En la ventana
intermedia, la barra enviaba al teclado `inputType=0x1` e `imeOptions=0x2000003`:
sin el flag, es decir aprendiendo. Y la barra es, por diseño, el sitio donde la
clave se teclea cada vez que se entra (RF-12). Y el tecleo que la enseñó fue **manual**, no de la
automatización: la §6 de este informe ya había medido que `adb shell input text` no
pasa por el teclado, así que las entradas por `adb` de la evaluación no pudieron
enseñarle nada a Gboard. Lo que sí pasó por el teclado fueron las veces que la
usuaria tecleó la clave a mano, entre ellas la comprobación manual de H-6 —que era,
irónicamente, «comprobar que el teclado no aprende la clave»—. Aquella
comprobación salió bien porque se miró **en otra aplicación**; la palabra ya
estaba aprendida y se ofrecía dentro de Aurora.

**El instrumento que cierra el caso, y que no teníamos.** Gboard escribe en el
registro del sistema su propio veredicto por campo. No hay que deducirlo de las
banderas: lo dice él.

```
EditorInfo{packageName=com.auroraclima.app, inputType=80001, enableLearning=false,
           autoCorrection=false, imeOptions=3000003, actionName=SEARCH}
IncognitoMode = true
```

Con eso se midió **todo campo de texto de la app** sobre el build de hoy, con un
control negativo para demostrar que el veredicto es por campo y no un ajuste
global del emulador:

| Campo | `inputType` | `imeOptions` | Gboard: `enableLearning` / `autoCorrection` | `IncognitoMode` |
|---|---|---|---|---|
| Barra de la fachada | `0x80001` | `0x3000003` | false / false | **true** |
| Barra de práctica del tutorial | `0x80001` | `0x3000003` | false / false | **true** |
| Relato | `0x28001` | `0x43000000` | true / true | **true** |
| Correo (`V2PlainField`) | `0x8021` | — | true / true | **true** |
| Clave y contraseñas (`V2SecretField`) | `0x8081` | `0x3000005` | false / false | **true** |
| *Control: buscador de los Ajustes de Android* | `0x100001` | — | true / true | **false** |

**Ningún campo de Aurora puede enseñarle nada a Gboard**, y lo dice Gboard. El
control de la última fila es el que lo hace una medición y no una creencia.

**Límite declarado de esta medición: vale para Gboard y solo para Gboard.**
`IncognitoMode` es un campo del registro **de Gboard**, no de Android. Lo que
Aurora declara es estándar —`IME_FLAG_NO_PERSONALIZED_LEARNING` y
`TYPE_TEXT_FLAG_NO_SUGGESTIONS` son de la plataforma, no de Google— pero **honrarlo
es decisión de cada teclado**, y la medición solo demuestra que Gboard lo honra. No
se verificaron el teclado de Samsung, SwiftKey ni ningún otro, y en un teléfono del
piloto es perfectamente posible que el teclado de fábrica sea otro. Dos
consecuencias prácticas: un teclado que ignore esos flags podría aprender y sugerir
igual, y eso Aurora no lo puede impedir —ni detectar—; y si el piloto se hace sobre
teléfonos de una marca concreta, conviene repetir §8-S4 y §8-S4b con **su** teclado
antes de entregar. Queda en **P-107**.

**Corrección.** `TYPE_TEXT_FLAG_NO_SUGGESTIONS` en **las dos barras y solo en
ellas**, por la vía que ya existía: el `EditorInfo` se intercepta en
`SecureTextEntry`, que gana un parámetro `hideSuggestions`. Efecto medido: el
`inputType` de la barra pasa de `0x1` a `0x80001` y Gboard pasa de declarar
`enableLearning=true, autoCorrection=true` a `enableLearning=false,
autoCorrection=false`.

Por qué solo en las barras, y no «por coherencia» en todo el envoltorio: en el
relato y en los correos el coste sería perder la autocorrección en textos largos
—el mismo coste que ya se rechazó al descartar `autoCorrect = false` en §10.1— y
el beneficio sería nulo, porque lo que se escribe ahí no es una credencial que
vaya a reaparecer sugerida en la fachada. Lo fija el séptimo caso de
`SensitiveFieldsGuardTest`, que comprueba las dos reglas opuestas: que las barras
lo pidan y que los campos del kit **no** lo pidan.

**Lo que Aurora no puede arreglar, y es importante decirlo.** La palabra ya está
en el diccionario de Gboard. Ninguna bandera la borra, y Gboard la seguirá
ofreciendo en WhatsApp, en el navegador o en cualquier otra app. Lo que esta
corrección consigue es que **deje de aparecer dentro de Aurora**, que es donde
delata: una clave rara sugerida en un buscador de ciudades es un indicio; la misma
palabra sugerida en un chat no significa nada para quien la vea. La mitigación del
resto es operativa y va en P-106: **borrar la palabra aprendida del teclado y
rotar la clave personal**.

**Gravedad: Alta.** Expone la credencial que protege todo el espacio interno, en
la pantalla más expuesta de la app, sin herramientas y sin conocimiento técnico.
Atenúa que el vector de aprendizaje llevaba cerrado desde H-7 y que la corrección
de hoy cierra también el de la sugerencia; pero el hecho ocurrió sobre una clave
real y lo encontró una persona usando la app, no un análisis.

**Y una lección sobre el método, que es la tercera de la misma familia, y la que
más conviene que quede escrita porque afecta a cómo se diseñan las comprobaciones
manuales.** H-6 —«el teclado no aprende la clave personal»— se dio por **cerrado**
el 2026-10-10 con una comprobación manual que consistió en teclear la clave en la
barra y después mirar, **en otra aplicación**, si el teclado la sugería. No la
sugería, y se anotó como pasa.

Era la comprobación correcta para la pregunta equivocada. Medía **si la palabra
había salido de Aurora**; la pregunta que importaba era **si Aurora la mostraba**.
Y la mostraba: cuatro meses de uso después, al escribir «aud» en el buscador del
clima, ahí estaba. El error no fue de ejecución —la comprobación se hizo bien— sino
de **diseño del criterio**: se eligió el sitio de observación más lejano al riesgo.
La regla que deja: **una comprobación de fuga se mira donde el dato haría daño, no
donde sería inocuo.** Para una credencial de Aurora, ese sitio es la fachada de
Aurora, no WhatsApp. Las tres veces que esta
evaluación falló de fondo fue por creer una premisa en vez de medirla —el
`autoCorrect = false` de §10.1, el enmascaramiento de §10.6 y esta—, y las tres
las desmintió una medición: dos de bytecode y una de una persona mirando la
pantalla.

### 10.8 Recuento tras las correcciones

| Resultado | v0.3.0 (medido) | v0.3.1 (tras corregir) |
|---|---|---|
| **Pasa** | 7 | **7** |
| **Pasa con observación** | 9 | **10** — entra STORAGE-2 |
| **Falla** | **1** (STORAGE-2) | **0** |
| **No aplica** | 3 | 3 |

**Ningún control sube a «Pasa» limpio, y esta cifra ya se corrigió dos veces.** La
primera redacción daba 9/8/0/3, poniendo en «Pasa» tanto STORAGE-2 como
PLATFORM-3. La segunda daba 8/9/0/3, corrigiendo STORAGE-2 pero dejando
PLATFORM-3 limpio. Las dos estaban mal, y las dos veces lo demostró la auditora
contra el texto de este mismo informe:

- **MASVS-STORAGE-2** tiene **dos** observaciones vivas según §5.1, y la primera
  es **P-22** —la caché persistente del SDK de Firestore es SQLite plano, sin
  SQLCipher—, que esta rama no toca. Solo la segunda era H-7. Se le suma el
  residual de accesibilidad de §10.1, **H-9** con su propio residual del clic
  derecho (§10.6) y **H-10** con el suyo: una clave aprendida antes sigue en el
  diccionario del teclado y Aurora no puede borrarla (§10.7). Queda en «Pasa con
  observación».
- **MASVS-PLATFORM-3** tampoco queda limpio. Su única observación *registrada* era
  H-7, sí, pero el residual de accesibilidad de §10.1 es materia suya: la lectura
  del árbol de accesibilidad es exposición de la INTERFAZ, que es justo lo que
  este control cubre —capturas, recientes, enmascaramiento— y lo que `FLAG_SECURE`
  no alcanza. Cobrarse el mismo residual en STORAGE-2 y no en PLATFORM-3 era una
  asimetría sin justificación. **H-9 y H-10 caen igualmente en los dos**, por el mismo
  motivo que H-7: son fuga de datos y nacen de una decisión de interfaz. Queda también
  en «Pasa con observación».

**Y una aclaración que el lector va a pedir: H-9 y H-10 son ALTOS y el recuento no se mueve.**
No es contradicción. El recuento cuenta **controles**, no hallazgos; los dos controles
que H-9 toca ya estaban en «Pasa con observación» por observaciones ajenas a él
(P-22, el residual de accesibilidad); y los dos se **corrigen en esta misma rama**, igual
que H-8 en §10.5. Lo que sí cambia es que el 0 en «Falla» cuelga ahora de dos
comprobaciones manuales y no de una.

Con la convención del propio informe —un control con observación viva es «Pasa con
observación»— el recuento de v0.3.1 es **7 · 10 · 0 · 3**. La cifra es menos
lucida que las dos anteriores y es la que se sostiene.

Cuatro observaciones se reducen sin desaparecer: **MASVS-NETWORK-1** pierde la de
la configuración implícita pero conserva la de P-12; **MASVS-CODE-3** pierde el
defecto concreto de Compose pero mantiene que 1.7.8 sigue por detrás de la línea
estable actual y que PdfBox-Android continúa sin mantenimiento; **MASVS-STORAGE-2**
pierde H-7 pero conserva P-22 y el residual de accesibilidad; y **MASVS-PLATFORM-3**
pierde H-7 y conserva ese mismo residual.

**De las comprobaciones manuales, dos ya están hechas y una queda.** Hechas por la
usuaria el 2026-10-13: el menú de selección del relato (§8-S1) y el del campo
enmascarado con el ojo abierto (§8-S1b, los tres tramos) — las dos **pasan**: solo
dejan pegar y seleccionar, nunca copiar ni cortar, ni con el ojo abierto ni con
«Seleccionar todo» aplicado. Y **§8-S4b**, la que H-10 abrió, también
está hecha y **pasa**: ni en la barra del clima ni en la de práctica apareció la
palabra que el teclado había aprendido en otra aplicación. **Con eso el 0 en
«Falla» deja de estar pendiente de ninguna comprobación manual.** Todo lo demás de esta sección se verificó con
medición sobre el dispositivo o sobre el artefacto.

### 10.9 Estado de la suite

**802 pruebas de JVM** por variante en 106 clases, 0 fallos, 0 ignoradas — eran
798 al empezar, y entran el tercer caso de `CamouflageResourcesTest`
(identificadores que sobreviven a R8), el tercero de `ManifestMinimizationTest`
(ningún bloque permite tráfico en claro), el sexto de `SensitiveFieldsGuardTest`
(la aserción de atribución de §10.1) y el séptimo (las dos reglas opuestas de
sugerencias de §10.7). **47 instrumentadas** en 12 clases sobre
el emulador B, 0 fallos — eran 45, y entran los 2 casos de `SecureTextEntryTest`.
ktlint sin violaciones y `lint` con 0 errores. `assembleDebug` y `assembleRelease`
en verde, este último con el guardián del DEX pasando —390 627 cadenas juzgadas,
148 364 distintas, en 197 entradas, 58 excepciones, 0 innecesarias, canarios 4/4, 0
hallazgos— sobre el APK cuyo SHA-256 cita §10.3. Las cifras bajan ligeramente
respecto a la ronda anterior (−260 cadenas) porque H-9 cambió el código, no por
indeterminismo: con el alcance y el código fijos, cinco construcciones
independientes dieron los nueve números idénticos.

La corrida es la del comando completo, sobre árbol limpio y con el JDK prescrito:
`test ktlintCheck lint assembleDebug assembleRelease`. **Procedimiento de
aislamiento, que no es opcional:** detener los demonios de Gradle **y de Kotlin**,
borrar `app/build`, y una **sola** invocación. No es ceremonia: dos construcciones
concurrentes sobre el mismo directorio corrompen el estado incremental —se vio
fallar en `kspReleaseKotlin` y en `processDebugResources`— y fueron la causa de las
cifras irreproducibles que se corrigieron en §10.3. `lint`
da 0 errores y 58 avisos, ninguno sobre los archivos nuevos. El APK release
medido es el del SHA-256 de §10.3.

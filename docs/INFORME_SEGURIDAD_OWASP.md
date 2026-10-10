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
enmascarados. **Observación (MASWE-0030):** la excepción es el campo del relato,
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
no aprenda. El campo del relato no hereda nada de eso. Los campos de correo
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

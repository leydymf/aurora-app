# Aurora — Guía de Arquitectura y Diseño de Código

Reglas obligatorias. Toda implementación debe cumplirlas; los agentes de revisión las auditan.

## 1. Arquitectura general

**MVVM + Clean Architecture en 3 capas, organizado por feature:**

```
app/src/main/java/com/auroraclima/app/
├── core/                    # transversal, sin lógica de negocio de features
│   ├── security/            # Keystore, cifrado, FLAG_SECURE, limpieza de estado
│   ├── database/            # Room + SQLCipher (configuración)
│   ├── network/             # cliente HTTP, manejo de errores de red
│   └── ui/                  # tema, componentes Compose reutilizables
├── weather/                 # fachada (RF-01 a RF-04)
├── access/                  # doble fondo: código secreto, salida rápida (RF-05..16)
├── accounts/                # auth, consentimiento (RF-07, RF-15, RF-17..20)
├── content/                 # tipos de violencia, rutas, líneas (RF-21..26)
├── questionnaire/           # RF-27..31
└── records/                 # RF-32..39 (registro de incidentes; nombre neutro)
```

Cada feature tiene internamente:

```
<feature>/
├── ui/        # pantallas Compose + ViewModel
├── domain/    # modelos, interfaces de repositorio, casos de uso — Kotlin puro
└── data/      # implementaciones de repositorio, DAOs, DTOs, fuentes remotas
```

## 2. Regla de dependencias (innegociable)

- `ui` → `domain` ← `data`. La UI nunca importa de `data`; `domain` no importa de Android (salvo anotaciones), ni de Room, ni de Firebase.
- Las features no se importan entre sí. Si dos features necesitan algo común, va a `core/` o se comunica por navegación.
- Todo acceso a datos pasa por una interfaz de repositorio definida en `domain`. Prohibido llamar Firestore, Room o la API de clima directamente desde un ViewModel.

## 3. Patrones obligatorios

- **Estado de UI:** cada pantalla tiene un `data class XxxUiState` inmutable expuesto como `StateFlow` desde el ViewModel. Eventos de usuario como funciones del ViewModel. Nada de `LiveData` ni estado mutable compartido.
- **Errores:** las operaciones de `data` devuelven `Result<T>` (o sealed class propia). Las excepciones nunca cruzan hacia la UI. Los mensajes de error visibles jamás revelan el doble fondo (un fallo de sync se muestra como error genérico o silencioso).
- **Casos de uso:** obligatorios cuando la operación toca seguridad (validar código secreto, cifrar, limpiar estado) o combina más de un repositorio. Para lecturas simples, el ViewModel puede usar el repositorio directo.
- **DI con Hilt:** todo se inyecta; prohibidos los `object` singletons hechos a mano y las instancias creadas dentro de ViewModels.
- **Inmutabilidad:** `val` por defecto, colecciones inmutables, sin `!!`, sin `lateinit` fuera de tests/DI.

## 4. Seguridad por diseño (mapeo a RNF)

| RNF | Mecanismo | Dónde vive |
|---|---|---|
| RNF-01 cifrado en reposo | SQLCipher con llave en Keystore; nunca llaves en código o SharedPreferences planas | `core/db`, `core/seguridad` |
| RNF-02 capturas/recientes | `FLAG_SECURE` aplicado por un componente único que envuelve toda pantalla del doble fondo | `core/seguridad` |
| RNF-03 cierre automático | temporizador de sesión + limpieza en `onStop` prolongado; un solo `SesionManager` | `core/seguridad` |
| RNF-04 minimización | no pedir permisos ni datos que un RF no exija; revisar manifest en cada PR | manifest + revisión |
| RNF-05 camuflaje | strings, íconos, notificaciones y paquete solo de clima; los recursos del doble fondo usan nombres neutros | recursos + revisión |
| RNF-06 separación | reglas de seguridad de Firestore versionadas en el repo (`firestore.rules`) | raíz del repo |
| RNF-07/08 rendimiento/offline | persistencia Firestore activada, imágenes ligeras, sin librerías pesadas nuevas | gradle + revisión |

Reglas adicionales:

- Prohibido `Log.*`, `println` o crash reporting con datos del doble fondo. En release, logging deshabilitado por completo (proguard/lint lo verifica).
- La validación del código secreto es de tiempo constante (comparar hashes, no strings) y ocurre en un caso de uso, no en la UI.
- Ningún texto sensible hardcodeado en `strings.xml` con nombres reveladores: usar claves neutras (`info_titulo_1`, no `violencia_titulo`).

## 5. Calidad y pruebas

- Cada RF implementado incluye como mínimo: test unitario del ViewModel o caso de uso, y test del repositorio con fuente falsa (fake, no mock de framework cuando sea posible).
- `./gradlew ktlintCheck test assembleDebug` debe pasar antes de cada commit.
- Funciones ≤ 30 líneas como guía; una clase = una responsabilidad; nombres en inglés descriptivos (`ValidateSecretCodeUseCase`, no `Util`).
- Composables sin lógica: solo pintan estado y emiten eventos.

## 6. Facilidad de cambio

- Feature nueva = paquete nuevo copiando la plantilla ui/domain/data. No tocar features existentes salvo por su interfaz pública.
- El contenido administrable (tipos, rutas, cuestionario) se modela con esquemas versionados (`schemaVersion` en los documentos de Firestore) para que el futuro panel web no rompa la app.
- Decisiones de arquitectura que cambien esta guía se registran al final de este archivo con fecha y motivo (mini-ADR).

## Registro de decisiones

- 2026-07-07 — v1 de esta guía, derivada de A2-1/A2-2 y los RNF de la especificación.
- 2026-07-07 — Paquetes renombrados de español a inglés neutro (`clima→weather`, `acceso→access`, `cuentas→accounts`, `contenido→content`, `cuestionario→questionnaire`, `incidentes→records`, `core/{seguridad→security, db→database, red→network}`). Motivo: hallazgo de auditoría de seguridad — los nombres de paquete quedan legibles en el APK y en stack traces (`incidentes`/`cuestionario` podían sugerir el propósito real), y CLAUDE.md exige inglés para nombres de código. `records` se eligió sobre `incidents` por ser el más neutro.
- 2026-07-07 — Firebase BOM fijado en 33.16.0 (no 34.x): la serie 34 se compila con Kotlin 2.3 y el proyecto usa Kotlin 2.0.0. Revaluar si se actualiza el toolchain de Kotlin.
- 2026-07-07 — La caché de clima (RF-02/RF-03) vive en una BD Room propia del feature (`weather/data/local/WeatherDatabase`, sin cifrar) en lugar de la futura BD central de `core/database`. Motivo: (a) ciudad y clima no son datos sensibles y una BD de clima en claro es lo esperable en una app meteorológica (RNF-05); (b) la BD central irá con SQLCipher (RNF-01) y mezclar ambas obligaría a cifrar datos de fachada o a que `core` dependa de entidades del feature, invirtiendo la regla de dependencias. `core/database` queda reservado para la configuración Room+SQLCipher del doble fondo.

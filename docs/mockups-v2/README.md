# Mockups V2 — Índice, brechas y especificación visual

Fuente: `Aurora_A2-6_Diagramas_Alta_Fidelidad V2.docx` (agosto 2026), 30 imágenes
extraídas en orden (`v2-01` … `v2-30`): 22 pantallas móviles + 5 web + 3 láminas de
sistema visual/librería. Este README es el mapa de la Fase 0 del `PLAN_UI_V2.md`.

Convención de numeración (según fases del plan): P01–P03 fachada · P04–P09 primer
uso y acceso · portada de Guía (sin número) + P10–P14 guía · P15–P21 reconocer,
registrar y ajustes · P22–P25 (+P23·b) panel web.

---

## 1. Índice v2-NN → pantalla → RF → implementación actual

| Imagen | Pantalla | Contenido del mockup | RF | Archivo(s) actuales |
|---|---|---|---|---|
| v2-01 | **P01** Clima principal | Ciudad, temperatura grande, franja por horas, humedad/viento, aviso de lluvia; **barra de pestañas Hoy · 5 días · Lugares** | RF-01, RF-03 | `weather/ui/WeatherScreen.kt` |
| v2-02 | **P02** Búsqueda de ciudad | Resultados + recientes; anotado el estado «sin resultados» (lo que ve un tercero si escribe mal el código) | RF-02, RF-12, RF-13 | `weather/ui/WeatherScreen.kt` (búsqueda integrada) |
| v2-03 | **P03** Próximos 5 días | Lista de días con icono, mínima, **barra de temperatura**, máxima; resumen de la semana; pestañas | RF-04 | `weather/ui/ForecastScreen.kt` |
| v2-04 | *Sistema visual (lámina 1)* | Superficies, «pop de iconos», constantes tipográficas y de toque | — | (especificación, ver §3) |
| v2-05 | **P04** Consentimiento | «Antes de empezar, decides tú.», 3 tarjetas con icono (qué guardamos / qué no / derechos), checkbox, botón inactivo, «No aceptar y volver al clima», nube | RF-17, RF-18 | `accounts/ui/ConsentScreen.kt` |
| v2-06 | **P05** Crear cuenta (paso 1 de 3) | Barra de progreso de pasos, correo/contraseña, error inline de correo existente | RF-07 (RF-39 activación) | `accounts/ui/CreateAccountScreen.kt` |
| v2-07 | **P06** Clave personal (paso 2 de 3) | Campo con ojo, reglas que cambian de icono al cumplirse («no es el nombre de una ciudad»), aviso de práctica | RF-08, RF-09 | `access/ui/CodeSetupScreen.kt` (y `ChangeCodeScreen.kt` hereda el estilo) |
| v2-08 | **P07** Tutorial (paso 3 de 3) | Tres momentos (entrar es buscar / salir es un toque / qué ve alguien más) + tarjeta «Practiquemos ahora» | RF-10, RF-11, RF-12 | `access/ui/TutorialScreen.kt` |
| v2-09 | **P08** Inicio de sesión | «Hola de nuevo.», error genérico de credenciales | RF-14, RF-15 | `accounts/ui/LoginScreen.kt` |
| v2-10 | **P09** Inicio (nuevo) | Saludo Marcellus, tarjeta del cuestionario con «Empezar», **accesos rápidos Llamar · Guardar evidencia · Informarme**, pestañas **Inicio · Guía · Mi registro**, icono sol arriba a la derecha, nube | RF-06 (+ atajos a RF-23/32/21) | `access/ui/PanelScreen.kt` (hoy: menú estático) |
| v2-11 | **Portada de Guía** (nueva) | Tres secciones: Entender la violencia / Rutas de atención / Líneas de atención + «Puedes leerlo sin conexión» | RF-21, RF-22, RF-23, RF-26 | *(no existe — hoy Panel enlaza directo a topics/routes/lines)* |
| v2-12 | **P10** Tipos | 5 tipos, **cada uno con icono y matiz propios** | RF-21 | `content/ui/TopicsScreen.kt` |
| v2-13 | **P11** Detalle de tipo | Descripción, «Señales para reconocerla» con vistos, «Cómo se ve en el día a día», botón «Ver a dónde acudir» | RF-21 (→RF-22) | `content/ui/TopicDetailScreen.kt` |
| v2-14 | **P12** Rutas de atención | Tres sectores con icono/color (salud, protegerte, denunciar) y entidades por sector | RF-22 | `content/ui/RoutesScreen.kt` |
| v2-15 | **P13** Detalle de entidad | Qué hace / Cuándo acudir / Dónde, aviso rosa de la llamada, botón «Llamar (número)» | RF-22 (F5: `ACTION_DIAL`) | `content/ui/AgencyDetailScreen.kt` |
| v2-16 | **P14** Líneas de atención | Número grande por tarjeta, botón de teléfono por fila, «Aurora no guarda ninguna llamada» | RF-23, RF-26 | `content/ui/LinesScreen.kt` |
| v2-17 | **P15** Cuestionario · intro | «Reconocer tu situación», 3 garantías (a tu ritmo / si sales se borra / tú decides al final), aviso de orientación, «Ahora no» | RF-27, RF-28 | `questionnaire/ui/GuidanceScreen.kt` (estado intro) |
| v2-18 | **P16** Cuestionario · pregunta | Una pregunta por pantalla, progreso por segmentos, «Salir» siempre visible, opción seleccionada con borde | RF-27, RF-28 | `questionnaire/ui/GuidanceScreen.kt` |
| v2-19 | **P17** Cuestionario · resultado | Nivel + frase, **aviso de orientación visible sin desplazar**, «Qué puedes hacer», «Ver a dónde acudir», Guardar/Descartar | RF-29, RF-30, RF-31 | `questionnaire/ui/GuidanceScreen.kt` (estado resultado) |
| v2-20 | **P18** Mi registro | Chip «Todo guardado», resultado del cuestionario como entrada destacada, entradas con tipo/fecha/nº de fotos, «Anotar algo», pestañas | RF-34 (RF-31, RF-37) | `records/ui/JournalListScreen.kt` |
| v2-21 | **P19** Anotar algo | Fecha, **tipo en chips de color**, texto libre, Guardar | RF-32, RF-33, RF-35 | `records/ui/JournalEditorScreen.kt` |
| v2-22 | **P20/P20·b** Detalle + eliminar | Detalle atenuado detrás de hoja de doble confirmación («Conservar» es la acción principal) | RF-34, RF-36 | `records/ui/JournalDetailScreen.kt` + `core/security/SecureDialog.kt` |
| v2-23 | **P21** Ajustes | Secciones: Tu llave de entrada (cambiar código, tutorial) / Tu privacidad (qué guardamos) / Tu cuenta (cerrar sesión, **eliminar todo al final en rojo**); pie «Prototipo académico» | RF-09, RF-11, RF-19, RF-20 | `access/ui/SettingsScreen.kt`, `accounts/ui/PrivacyScreen.kt`, `DeleteAccountScreen.kt` |
| v2-24 | **P22** Panel · login | Marca Aurora, correo institucional, error de rol sin acceso, «El panel gestiona solo contenido» | RF-40 | `admin/src/auth/LoginPage.tsx` |
| v2-25 | **P23** Panel · contenido | Tabla de tipos con icono/color, señales/ejemplos, **chip de estado de publicación**, versión-borrador, «Publicar cambios» | RF-41, RF-44 | `admin/src/resources/content/ContentPage.tsx` |
| v2-26 | **P24** Panel · rutas y líneas | Entidades por ruta + líneas; **aviso de integridad al eliminar** («aparece en 2 rutas»); nota de actualización sin nueva versión de la app | RF-42, RF-44 | `admin/src/resources/routes/RoutesPage.tsx` (+ tabs) |
| v2-27 | **P25** Panel · cuestionario | Preguntas con peso, niveles orientativos con rangos, **publicar bloqueado con motivo** («falta recomendaciones del nivel extremo»), historial de versiones | RF-43, RF-44 | `admin/src/resources/questionnaire/QuestionnairePage.tsx` |
| v2-28 | **P23·b** Panel · editor de tipo | Nuevo tipo con **biblioteca cerrada de 18 iconos y 6 acentos**, vista previa «como lo verá la usuaria», contraste verificado, checklist «Falta para publicar» | RF-41 | editor dentro de `ContentPage.tsx` |
| v2-29 | *Librería visual (anexo)* | Paleta con pares de contraste, 18 iconos con significado, 5 reglas de uso | — | (especificación, ver §3) |
| v2-30 | *Librería visual (cierre)* | Nota: por qué NO hay selector de apariencia en Configuración; propone «tamaño de letra» en P21 | — | (ver preguntas §4) |

La **nube violeta de salida rápida** aparece en toda pantalla interna (RF-16): hoy la
pinta `SecureScreen` por construcción — solo cambia de apariencia, no de mecanismo.

---

## 2. Inventario de brechas

### RE-ESTILO PURO (misma estructura y navegación; solo tokens/estilo)

- **P02** búsqueda (v2-02) — misma estructura; el estado «sin resultados» sigue
  compartido con el flujo del código (RF-13, test de regresión existente).
- **P04** consentimiento (v2-05) — mismos elementos (checkbox, botón inactivo,
  rechazo que vuelve al clima); se suman iconos por tarjeta.
- **P05** crear cuenta (v2-06) — misma pantalla; se suma el indicador «paso N de 3».
- **P06** clave personal (v2-07) — mismas reglas; el cambio es que el icono de cada
  regla cambia al cumplirse.
- **P07** tutorial (v2-08) — mismos tres momentos + práctica real.
- **P08** login (v2-09) — misma estructura y errores genéricos.
- **P11** detalle de tipo (v2-13), **P12** rutas (v2-14), **P13** entidad (v2-15),
  **P14** líneas (v2-16) — mismas pantallas y navegación; cambian tarjetas, iconos
  de contorno y acentos por significado.
- **P15–P17** cuestionario (v2-17/18/19) — los tres estados ya existen en
  `GuidanceScreen`; re-estilo (progreso por segmentos, aviso sin desplazar).
- **P19** anotar (v2-21) — misma pantalla; el selector de tipo pasa a chips.
- **P20/P20·b** detalle + doble confirmación (v2-22) — mismo flujo con `SecureDialog`;
  cambia a hoja inferior con «Conservar» como acción principal.
- **P21** ajustes (v2-23) — mismas acciones reagrupadas en tres secciones con la
  acción delicada al final (ver pregunta Q6 por «Cambiar contraseña»).
- **P22** login del panel (v2-24), **P23** contenido (v2-25), **P24** rutas y líneas
  (v2-26), **P25** cuestionario (v2-27) — el panel ya tiene chips de estado, aviso de
  integridad y bloqueo de publicación con motivo (RF-41/42/43/44): re-estilo a
  densidad de escritorio.

### CAMBIO ESTRUCTURAL (navegación o datos nuevos)

1. **Fachada con barra de pestañas Hoy · 5 días · Lugares** (v2-01, v2-03). Hoy la
   fachada navega `home → forecast` sin barra inferior; «Lugares» no existe como
   pantalla propia (ver Q4). Afecta el NavHost raíz (solo rutas de fachada, fuera de
   `secureGraph` — sin impacto en camuflaje: identidad 100 % clima).
2. **Navegación interna por pestañas Inicio · Guía · Mi registro** (v2-10/11/14/16/20).
   Hoy `PanelScreen` es un menú y todo se recorre con back. Las pestañas deben vivir
   ÍNTEGRAS dentro de `secureGraph`/`SecureBackStackGuard` (regresión obligatoria de
   RF-16/RNF-02/RNF-03 — ya previsto en S2 del plan).
3. **Inicio nuevo (P09)** (v2-10): saludo, tarjeta del cuestionario con conteo de
   preguntas/duración, accesos rápidos Llamar · Guardar evidencia · Informarme.
   `PanelScreen` hoy no tiene ViewModel ni datos; la tarjeta del cuestionario exige
   un dato que ningún UiState del panel tiene (ver Q8).
4. **Portada de Guía** (v2-11): pantalla nueva que agrupa tipos, rutas y líneas en
   una sola sección con indicador offline. Reorganiza rutas de navegación de
   `content/`.
5. **Mi registro como pestaña raíz** (v2-20): la lista existe (`JournalListScreen`)
   pero pasa de destino secundario a pestaña; además muestra el resultado del
   cuestionario como entrada destacada y el chip global «Todo guardado» (estado de
   sincronización agregado — hoy el estado es por entrada).
6. **Icono y color propios por tipo de violencia** (v2-12, v2-25, v2-28): exige
   campos nuevos (`icon`, `color`) en la colección `topics` (schemaVersion), en el
   panel y en la siembra — toca `domain`/`data`, fuera del alcance «solo capa ui»
   del plan (ver Q2, decisión previa obligatoria).
7. **Editor del panel P23·b** (v2-28): la biblioteca cerrada de iconos/colores con
   vista previa y contraste verificado es UI nueva del editor; depende del punto 6.

---

## 3. Especificación del sistema visual (anexo v2-04 + v2-29 + v2-30) — contrato de la Fase 1

> Regla madre del anexo: cada acento vive en dos versiones — **tinte claro** para el
> fondo del círculo y **versión profunda** para el trazo del icono. Nunca se usa el
> tinte para texto ni el trazo para fondos grandes.

### 3.1 Superficies y texto (con pares de contraste)

| Token | Valor | Contraste |
|---|---|---|
| Fondo de pantalla | `#F3F1F7` (lila muy claro) | — |
| Tarjeta | `#FFFFFF` · radio **28** | — |
| Tinta principal | `#1B2530` | **13.6:1** sobre fondo |
| Texto secundario | `#5A5568` | **6.4:1** sobre fondo |
| Lavanda · acción principal | fondo `#B5AECB` · texto `#231F31` | (par definido por el anexo) |
| Separador | `#F0EEF4` | — |

### 3.2 Acentos — significado fijo (tinte de círculo · trazo de icono)

| Acento | Significado | Tinte | Trazo | Nota |
|---|---|---|---|---|
| Lila | reconocer (cuestionario) | `#E3E0EC` | `#3A3550` | |
| Violeta | salida rápida | `#E7DFEE` | `#4A3358` | exclusivo de la nube |
| Azul | protección | `#D5E3EC` | `#274456` | |
| Verde | salud, llamar, listo | `#DCE7E1` | `#2C4A3E` | |
| Rosa | avisos y cuidado | `#F0E4E8` | `#7A3B4D` | |
| Arena | justicia y tiempo | `#EFE5D9` | `#5A4327` | |
| Neutro | «contiene varias cosas» (tarjetas agrupadoras) | `#EDEBF3` | `#4E4A5E` | contraste **6.9:1** |

*(La lámina v2-04 enuncia otros significados para los mismos matices — ver Q7: se
propone el anexo v2-29 como fuente normativa.)*

### 3.3 Iconografía — librería cerrada de 18

Trazo **1.9**, extremos redondeados, **21 px sobre círculo de 46**:

Física · Psicológica · Sexual · Económica · Digital · Salud · Protección · Justicia ·
Llamar · Guía · Mi registro · Salida rápida · Fecha · Evidencia · Sincronizado ·
En espera · Código · Eliminar

### 3.4 Las cinco reglas de uso

1. **Un color por significado, no por decoración.** El verde siempre es cuidado,
   llamada o algo cumplido; el rosa siempre es aviso o advertencia; el azul es
   protección; el arena es justicia y tiempo. Un mismo color no cambia de sentido
   entre pantallas.
2. **Lavanda solo para la acción principal.** Una por pantalla. Si dos botones
   compiten, el secundario va en blanco con borde.
3. **El color acompaña, nunca informa solo.** Todo estado lleva además texto o
   icono, para quien no distingue colores y para pantallas de gama baja.
4. **Iconos de contorno, nunca rellenos ni ilustrados.** Trazo 1.9, extremos
   redondeados, sin candados, sirenas, puños ni símbolos policiales. «La calma
   también se dibuja.»
5. **El matiz de una tarjeta es el de su acción dominante.** Si la tarjeta agrupa
   varias cosas de colores distintos («Entender la violencia» reúne cinco tipos,
   «Rutas de atención» reúne las tres rutas), su círculo va en el neutro
   `#EDEBF3`/`#4E4A5E` y el color aparece un nivel más adentro.

### 3.5 Constantes (lámina v2-04)

- **Marcellus** solo en el saludo o título de pantalla (espacio interno); **IBM Plex
  Sans** en todo lo demás. La fachada usa únicamente la sans neutra (regla del V2 y
  decisión F1 — camuflaje).
- Texto base **15–16 px**; áreas de toque **≥ 48 dp**.
- **Un solo botón lleno por pantalla**; la nube de salida siempre alcanzable con el
  pulgar.
- Nota de cierre (v2-30): **no** hay selector de colores/apariencia en Configuración
  (elegir color rompería el significado fijo); el anexo propone en su lugar
  «tamaño de letra» en P21 — pendiente de decisión (Q5).

---

## 4. PREGUNTAS PARA LA USUARIA (bloqueantes o de alcance — no se resuelven solas)

- **Q1 · Léxico vedado en textos estáticos.** Los mockups reintroducen literales que
  `CamouflageResourcesTest` veda o que ya se redactaron por camuflaje (ADR
  2026-07-09/2026-07-10): «Tu código secreto» (P06), «Crea tu espacio seguro.» (P05),
  y los títulos estáticos de portada/Guía «Entender la violencia», «Tipos de
  violencia» (v2-11/12/13). Los títulos que vienen de Firestore no son problema; los
  estáticos sí. ¿Confirmas que **el camuflaje prima**: se mantiene la redacción
  neutra aprobada («tu clave personal», «tu espacio», claves `content_*` actuales) y
  la V2 solo cambia el estilo? (Impacta también reflejarlo en A2-6.)
- **Q2 · Icono/color por tipo (dato nuevo).** P10/P23/P23·b muestran icono y matiz
  propios por tipo de violencia. Hoy `topics` no tiene esos campos. Opciones:
  (a) campos `icon`/`color` en Firestore con `schemaVersion` nuevo + panel + siembra
  (toca `domain`/`data` — excede «solo capa ui»); (b) asignación fija local en la app
  (id de topic → icono/color, sin tocar datos, pero el panel no podría elegirlos).
  ¿Cuál autorizas? (Condiciona el punto 6 de brechas y el editor P23·b.)
- **Q3 · Pestañas internas: ¿tres o cuatro?** El plan dice «Inicio · Guía ·
  Mi registro · Ajustes», pero los mockups muestran **tres** pestañas y Ajustes con
  flecha de retorno (v2-23). En P09 hay un icono (¿sol/destello?) arriba a la
  derecha sin rótulo — ¿es la entrada a Ajustes, otra cosa, o decoración? ¿Cómo se
  llega a Ajustes en la V2?
- **Q4 · Pestaña «Lugares» de la fachada.** No hay mockup propio. ¿Es la búsqueda
  actual (P02) montada como pestaña, o una pantalla nueva de ciudades guardadas?
  (Recordar: la barra de búsqueda debe seguir siendo la puerta del código — RF-05/12.)
- **Q5 · Tamaño de letra en Ajustes.** El anexo (v2-30) lo propone explícitamente
  («Dime si lo agrego a P21»). ¿Entra en el alcance de la V2 o queda post-MVP?
- **Q6 · «Cambiar contraseña» ausente en P21.** El mockup de Ajustes no la muestra,
  pero existe hoy (`ChangePasswordScreen`, RF-09/RF-39 — re-envuelve la DEK). ¿Se
  mantiene dentro de «Tu llave de entrada» aunque el mockup no la dibuje?
- **Q7 · Inconsistencia interna del documento V2.** La lámina v2-04 asigna otros
  significados a los matices (rosa=hablar, azul=rutas, salvia=registro,
  arena=seguridad, violeta=cuidado propio) que contradicen el anexo v2-29
  (rosa=avisos, azul=protección, verde=salud/llamar, arena=justicia/tiempo,
  violeta=salida rápida). Propongo tomar **v2-29 (librería) como fuente normativa**
  por ser el anexo final y traer los pares de contraste. ¿Confirmas?
- **Q8 · Conteo del cuestionario en Inicio y P15.** Los mockups dicen «6 preguntas ·
  unos 4 minutos», pero el instrumento vigente es la v2 de **12 preguntas** (ADR
  2026-07-22). El número debería salir del formulario publicado, lo que exige dar
  datos (y ViewModel) al Inicio — `PanelScreen` hoy no tiene ninguno. ¿Tarjeta con
  dato dinámico, o texto sin números («unas pocas preguntas»)?

### Notas (no bloqueantes)

- Los mockups de fachada cumplen el camuflaje: sin Marcellus, sin marca squircle,
  identidad 100 % clima. La marca Aurora con destello aparece solo en el espacio
  interno y el panel — consistente con F1.
- Los contenidos de muestra difieren de producción (5 tipos vs 4 sembrados; niveles
  del cuestionario de ejemplo): son datos de Firestore, no de UI — sin acción.
- El chip «Todo guardado» de P18 agrega el estado de sincronización a nivel de lista
  (hoy es por entrada): revisar en S4 si `JournalListUiState` ya lo permite derivar.

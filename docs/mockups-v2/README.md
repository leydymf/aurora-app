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
  acción delicada al final; conserva «Cambiar contraseña» aunque el mockup la omita
  (D6).
- **P22** login del panel (v2-24), **P23** contenido (v2-25), **P24** rutas y líneas
  (v2-26), **P25** cuestionario (v2-27) — el panel ya tiene chips de estado, aviso de
  integridad y bloqueo de publicación con motivo (RF-41/42/43/44): re-estilo a
  densidad de escritorio.

### CAMBIO ESTRUCTURAL (navegación o datos nuevos)

1. **Fachada con barra de pestañas Hoy · 5 días · Lugares** (v2-01, v2-03). Hoy la
   fachada navega `home → forecast` sin barra inferior; «Lugares» abre la búsqueda/
   recientes actual, la misma P02 (D4). Afecta el NavHost raíz (solo rutas de
   fachada, fuera de `secureGraph` — sin impacto en camuflaje: identidad 100 % clima).
2. **Navegación interna por pestañas Inicio · Guía · Mi registro** (v2-10/11/14/16/20).
   Hoy `PanelScreen` es un menú y todo se recorre con back. Las pestañas deben vivir
   ÍNTEGRAS dentro de `secureGraph`/`SecureBackStackGuard` (regresión obligatoria de
   RF-16/RNF-02/RNF-03 — ya previsto en S2 del plan).
3. **Inicio nuevo (P09)** (v2-10): saludo, tarjeta del cuestionario y accesos
   rápidos Llamar · Guardar evidencia · Informarme. La tarjeta va SIN conteo de
   preguntas ni duración (D8), así que `PanelScreen` puede seguir sin ViewModel;
   Ajustes se abre desde el icono de engranaje arriba a la derecha (D3).
4. **Portada de Guía** (v2-11): pantalla nueva que agrupa tipos, rutas y líneas en
   una sola sección con indicador offline. Reorganiza rutas de navegación de
   `content/`.
5. **Mi registro como pestaña raíz** (v2-20): la lista existe (`JournalListScreen`)
   pero pasa de destino secundario a pestaña; además muestra el resultado del
   cuestionario como entrada destacada y el chip global «Todo guardado» (estado de
   sincronización agregado — hoy el estado es por entrada).
6. **Icono y color propios por tipo de violencia** (v2-12, v2-25, v2-28): resuelto
   con asignación fija local en `core/ui` (id de topic → icono/matiz, fallback al
   neutro) — sin campos nuevos en Firestore ni cambios en `domain`/`data` (D2).
   *Actualización 2026-08-14: la «opción a» se activó — esquema v2 con
   `icon`/`accent` seleccionables; el mapa fijo sobrevive como degradación (ADR
   2026-08-14 en ARQUITECTURA.md).*
7. **Editor del panel P23·b** (v2-28): el selector de iconos/colores se difiere
   post-V2 (D2); el editor conserva checklist y vista previa con el neutro.
   *Cerrado 2026-08-14: selector implementado (`VisualPicker`, solo la biblioteca
   cerrada con su significado) en los editores de tipos y rutas.*

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
| Tinta principal | `#1B2530` | **13.6:1** sobre fondo (calculado real: 13.84:1, ver §3.6) |
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

*(La lámina v2-04 enuncia otros significados para los mismos matices; por decisión
D7, este anexo v2-29 es la fuente normativa y v2-04 solo aporta las constantes.)*

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
  (elegir color rompería el significado fijo); el «tamaño de letra» que el anexo
  proponía para P21 queda fuera de la V2 (D5) — la V2 respeta el tamaño de fuente
  del sistema.

### 3.6 Ratios de contraste verificados (WCAG AA) — Fase 1, 2026-08-11

Calculados con la fórmula de luminancia relativa de WCAG 2.1 sobre los valores del
anexo, al implementar los tokens en `core/ui/v2/Palette.kt`. AA exige ≥ 4.5:1 para
texto normal, ≥ 3:1 para texto grande y elementos no textuales.

| Par | Ratio | AA |
|---|---|---|
| Tinta principal `#1B2530` / fondo `#F3F1F7` | 13.84:1 | ✅ |
| Tinta principal `#1B2530` / tarjeta `#FFFFFF` | 15.51:1 | ✅ |
| Texto secundario `#5A5568` / fondo `#F3F1F7` | 6.39:1 | ✅ |
| Texto secundario `#5A5568` / tarjeta `#FFFFFF` | 7.16:1 | ✅ |
| Texto de acción `#231F31` / lavanda `#B5AECB` | 7.53:1 | ✅ |
| Lila trazo `#3A3550` / tinte `#E3E0EC` | 8.94:1 | ✅ |
| Violeta trazo `#4A3358` / tinte `#E7DFEE` | 8.49:1 | ✅ |
| Azul trazo `#274456` / tinte `#D5E3EC` | 7.83:1 | ✅ |
| Verde trazo `#2C4A3E` / tinte `#DCE7E1` | 7.68:1 | ✅ |
| Rosa trazo `#7A3B4D` / tinte `#F0E4E8` | 6.63:1 | ✅ |
| Arena trazo `#5A4327` / tinte `#EFE5D9` | 7.45:1 | ✅ |
| Neutro trazo `#4E4A5E` / tinte `#EDEBF3` | 7.21:1 | ✅ (anexo decía 6.9:1; el real es mejor) |
| Rosa trazo `#7A3B4D` como texto de aviso / fondo `#F3F1F7` | 7.32:1 | ✅ |

Tokens de la fachada (S1 de la Fase 2, 2026-08-11 — muestreados de v2-01/v2-03):

| Par | Ratio | AA |
|---|---|---|
| Texto blanco / cielo arriba `#474061` | 9.66:1 | ✅ |
| Texto blanco / cielo abajo `#676086` | 5.84:1 | ✅ |
| Tinta principal `#1B2530` / píldora de pestaña `#E3E0EC` | 11.92:1 | ✅ |
| Iconos sol `#A07F44` y lluvia `#897DA8` / tarjeta blanca | 3.74:1 / 3.78:1 | ✅ (no textual ≥ 3:1) |

Dos hallazgos no textuales, resueltos por diseño en la Fase 1:

- **Borde lavanda `#B5AECB` sobre blanco: 2.13:1** — insuficiente como borde único
  (3:1). El botón secundario («blanco con borde», regla 2) lleva por eso el borde en
  el gris del texto secundario `#5A5568` (6.4:1 ✅), no en lavanda.
- **Relleno lavanda de la barra de progreso sobre su riel: < 3:1** — aceptado porque
  la regla 3 obliga a que el progreso vaya SIEMPRE acompañado de texto («Paso N
  de M», segmentos contados); la barra es refuerzo visual, nunca el único indicador.
- **Barra de temperatura de P03 (`#A69FB7`→`#C5A36D`) sobre la tarjeta: < 3:1** —
  aceptado por el mismo criterio: la barra va siempre flanqueada por las cifras de
  mínima y máxima; es refuerzo visual, nunca el único indicador.

---

## 4. Decisiones de la usuaria (2026-08-11) — cierran las preguntas de la Fase 0

Las ocho preguntas quedaron resueltas. Estas decisiones VINCULAN a las Fases 1–3:

- **D1 (Q1) · El camuflaje prima — léxico establecido.** Se mantiene la redacción
  neutra ya aprobada (ADR 2026-07-09/2026-07-10): «tu clave personal» (no «código
  secreto»), «tu espacio»/«Crea tu espacio.» (no «espacio seguro»), y las claves
  `content_*`/`access_panel_*` actuales para los títulos estáticos de Guía. Los
  mockups aportan SOLO el estilo; `CamouflageResourcesTest` sigue siendo el árbitro.
- **D2 (Q2) · Icono/color por tipo: asignación fija local en la app.** Sin campos
  nuevos en Firestore ni cambios en `domain`/`data` (se respeta el alcance «solo
  capa ui»): un mapa cerrado en `core/ui` de id de topic (`t01`…) → icono/matiz de
  la librería, con **fallback al neutro** (`#EDEBF3`/`#4E4A5E` + icono de Guía) para
  topics no mapeados — coherente con la regla 5 del anexo. Consecuencia: el selector
  de icono/color del editor P23·b se **difiere post-V2** (el editor web conserva el
  resto: checklist «falta para publicar», vista previa con el neutro); si algún día
  se quiere elegible desde el panel, será con `schemaVersion` nuevo (opción a).
  *Diferido CERRADO el 2026-08-14: la opción a se activó con el esquema v2
  (`icon`/`accent`, ids camuflados de la biblioteca cerrada) y el mapa fijo pasó a
  ser el contrato de degradación — ADR 2026-08-14 en ARQUITECTURA.md.*
- **D3 (Q3) · Tres pestañas; Ajustes desde el Inicio, arriba a la derecha, con el
  icono de ajustes ya establecido** (engranaje, no el sol del mockup). Barra interna:
  Inicio · Guía · Mi registro; P21 Ajustes se abre desde ese acceso y vuelve con
  flecha de retorno, como en v2-23.
- **D4 (Q4) · «Lugares» = P02.** La pestaña de la fachada abre la búsqueda/recientes
  actual (misma pantalla que tocar la barra en P01). Innegociable: la barra de
  búsqueda sigue siendo la puerta del código (RF-05/12) y el estado «sin resultados»
  sigue compartido (RF-13).
- **D5 (Q5) · Tamaño de letra: fuera de la V2** (post-MVP, anotado en PENDIENTES).
  Para mantener el alcance homogéneo, la V2 se limita a respetar el tamaño de fuente
  del sistema (sp + comprobación con fuente grande), que ya cubre la necesidad de
  accesibilidad sin añadir una preferencia propia.
- **D6 (Q6) · «Cambiar contraseña» se mantiene en P21** (omisión del mockup),
  dentro de «Tu llave de entrada» con el mismo estilo de fila. Regla general
  derivada: toda pantalla existente SIN mockup V2 (`ChangeCodeScreen`,
  `ChangePasswordScreen`, `DeleteAccountScreen`, `PrivacyScreen`,
  `JournalMediaScreen`, tutorial en modo repaso) se re-estiliza **por extensión del
  sistema** — tokens y componentes de la librería, sin inventar elementos nuevos.
- **D7 (Q7) · El anexo v2-29 es la fuente normativa** de significados de color y
  pares de contraste; la lámina v2-04 queda como referencia de constantes
  tipográficas/de toque únicamente. El §3 de este README ya refleja esa jerarquía.
- **D8 (Q8) · Sin conteo de preguntas ni duración.** La tarjeta del cuestionario en
  Inicio (P09) y la intro (P15) pierden el «6 preguntas · unos 4 minutos» /
  «Seis preguntas cortas»: texto genérico sin números (ni estático ni dinámico).
  `PanelScreen` puede seguir sin ViewModel; la pantalla de pregunta (P16) no se ve
  afectada — su progreso ya sale del formulario publicado.

### Decisiones S3 (2026-08-12) — Guía

- **Mapeo id → icono/matiz ratificado** (aplica D2; el esquema `schemaVersion` 1
  no trae el campo): t01 Física → figura + azul (protección) · t02 Psicológica →
  lóbulos + lila (reconocer) · t03 Sexual → círculo con barra + rosa (aviso) ·
  t04 Económica y patrimonial → tarjeta + arena (justicia); cualquier otro id →
  neutro + icono de Guía. Sectores por el anexo: r01 Salud → verde · r02
  Protección → azul · r03 Justicia → arena. Vive en `core/ui/v2/ContentVisuals.kt`.
- **Dos iconos que el anexo omitió y sus mockups usan** (mismo criterio que el
  de la pestaña Inicio): círculo de información («qué hace», v2-15) y pin de
  lugar («dónde», v2-15; portada v2-11). La flecha de retorno y el chevron de
  fila son mobiliario de navegación, fuera de la librería de significados.

### Notas (no bloqueantes)

- Los mockups de fachada cumplen el camuflaje: sin Marcellus, sin marca squircle,
  identidad 100 % clima. La marca Aurora con destello aparece solo en el espacio
  interno y el panel — consistente con F1.
- Los contenidos de muestra difieren de producción (5 tipos vs 4 sembrados; niveles
  del cuestionario de ejemplo): son datos de Firestore, no de UI — sin acción.
- El chip «Todo guardado» de P18 agrega el estado de sincronización a nivel de lista
  (hoy es por entrada): revisar en S4 si `JournalListUiState` ya lo permite derivar.

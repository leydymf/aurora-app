# Aurora — Plan de actualización de interfaz (Diseño V2)

**Fuente de verdad:** `docs/mockups-v2/` (30 imágenes extraídas en orden del documento
`Aurora_A2-6_Diagramas_Alta_Fidelidad V2.docx`, agosto 2026) — 22 pantallas móviles +
5 web + sistema visual y librería.

**Qué es la V2:** rediseño visual completo — sistema lila con acentos de color por
significado, tarjetas de esquina suave, iconografía de contorno, tipografía Marcellus
solo en el espacio interno (la fachada usa tipografía neutra), pestañas de navegación
(Inicio · Guía · Mi registro · Ajustes), portada de Guía, inicio con tres accesos
rápidos (Llamar, Guardar evidencia, Informarme) y la nube violeta de salida rápida.

## Principios de la migración (innegociables)

1. **Solo capa `ui`.** Los ViewModels, casos de uso, repositorios y el motor del
   cuestionario NO se tocan. Si un mockup exige un dato que el UiState no tiene, se
   consulta antes de tocar domain. (§3 ARQUITECTURA.md: Composables sin lógica.)
2. **El camuflaje manda sobre el mockup.** La fachada comparte paleta pero tipografía
   neutra e iconos de clima (regla explícita del documento V2). `CamouflageResourcesTest`
   y la auditora-seguridad validan cada flujo. FLAG_SECURE, secureGraph, SecureDialog
   y la salida rápida no cambian de mecanismo, solo de apariencia.
3. **Tokens primero, pantallas después.** El sistema visual (paleta con pares de
   contraste, formas, iconografía con significado fijo, las cinco reglas del anexo)
   se implementa UNA vez en `core/ui` y las pantallas solo consumen tokens y
   componentes. Prohibido hardcodear colores/formas en pantallas.
4. **Regresión continua.** Toda la suite existente debe seguir en verde al cierre de
   cada sesión; los tests de ViewModel no deberían cambiar (si uno cambia, es señal
   de que se tocó lógica — justificarlo).
5. **Un flujo por sesión de Claude Code**, con los tres agentes al cierre.

## Fase 0 — Línea base (ANTES de tocar UI)

- **Decisión 2026-08:** las corridas manuales se difieren al cierre de la V2 (habría
  que repetir las visuales de todos modos). En su lugar: tag **v0.2.0-rc1** sobre el
  commit actual (candidato sin verificación manual — así se documenta) y rama
  **ui-v2** para todo el rediseño; `main` queda estable para rollback trivial.
- Sesión corta de Claude Code: mapear `docs/mockups-v2/v2-NN.png` ↔ pantallas P01–P25
  (viendo las imágenes) y escribir `docs/mockups-v2/README.md` con el índice, más el
  inventario de brechas: qué pantallas son re-estilo puro y cuáles cambian estructura
  o navegación (pestañas, portada de Guía, inicio con accesos rápidos).

## Fase 1 — Sistema visual en core/ui (1 sesión)

- Tema V2: paleta lila + acentos por significado con sus pares de contraste
  (verificar AA), tipografía (Marcellus solo interna; neutra en fachada), formas.
- Componentes del sistema: tarjeta de esquina suave, chip (tipo/estado), fila de
  lista con icono de contorno, botón primario/secundario, barra de progreso,
  la nube de salida rápida, y la iconografía de significado fijo (librería cerrada).
- Pantalla de muestra interna (debug) o previews de Compose para validar los tokens
  antes de migrar pantallas reales.

## Fase 2 — Migración por flujos (4 sesiones)

- **S1 · Fachada (P01–P03):** clima principal, búsqueda con su «sin resultados» del
  código (RF-13: idéntico para búsqueda fallida y código), cinco días con barra de
  temperatura. Tipografía neutra, sin Marcellus.
- **S2 · Primer uso y acceso (P04–P09):** consentimiento con iconos y botón inactivo,
  cuenta paso 1-de-3, código con reglas que cambian de icono, tutorial de tres
  momentos, login, e Inicio nuevo con los tres accesos rápidos (cambio estructural:
  navegación por pestañas nace aquí).
- **S3 · Guía (portada + P10–P14):** portada de tres secciones, tipos con matices e
  iconos propios, detalle con señales y ejemplos, rutas, entidad, líneas con número
  grande.
- **S4 · Reconocer, registrar, ajustes (P15–P21):** cuestionario (intro, una pregunta
  por pantalla, resultado con aviso visible sin desplazar), registro con estado como
  chip, anotar con tipos en chips y evidencias, detalle con doble confirmación,
  ajustes agrupados con la acción delicada al final.

## Fase 3 — Panel web (1 sesión)

- P22–P25 + editor P23·b: mismo sistema a densidad de escritorio; chips de estado de
  publicación, aviso de integridad al eliminar, publicar bloqueado con motivo,
  biblioteca cerrada de iconos y colores con vista previa. Solo estilos/plantillas —
  la lógica y validaciones del panel no cambian. vitest en verde.

## Fase 4 — Cierre v0.3.0

- Corrida completa (app + panel), pasada de los tres agentes sobre el diff completo,
  verificación visual tuya pantalla por pantalla contra los mockups (tú eres el
  criterio de fidelidad), CP visuales de camuflaje repetidos (recientes, capturas,
  búsqueda fallida vs código), actualización de RESULTADOS_PRUEBAS y tag
  **v0.3.0-trl4**.

## Riesgos y cómo los maneja el plan

- **Cambio estructural disfrazado de re-estilo** (pestañas, inicio nuevo): aislado en
  S2; la navegación interna sigue dentro de secureGraph y SecureBackStackGuard —
  regresión obligatoria de RF-16/RNF-02/RNF-03 en esa sesión.
- **Deriva del sistema visual**: Fase 1 crea la única fuente de tokens; la
  revisora-arquitectura rechaza colores/formas hardcodeados en pantallas.
- **Romper la indistinguibilidad (RF-13)** al re-estilizar la búsqueda: el estado
  «sin resultados» y el flujo del código comparten el mismo composable — test de
  regresión existente + verificación manual en S1.
- **Contraste/accesibilidad**: los pares de contraste del anexo se validan AA en
  Fase 1, no pantalla por pantalla.

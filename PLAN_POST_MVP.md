# Aurora — Plan Post-MVP (después de v0.1.0-trl4)

Criterio de ordenamiento: (1) cerrar deuda de verificación antes de crear deuda nueva;
(2) completar el ciclo de vida del sistema (contenido administrable de punta a punta)
antes de features aisladas; (3) dejar para el final lo que depende de decisiones
externas (directora) o tiene el mayor riesgo de seguridad por unidad de valor (RF-33).

---

## Fase A — Estabilización y decisiones (1 día) — BLOQUEA TODO LO DEMÁS

**A1. Corrida manual de CP pendientes (tú, en emulador, ~2 h):**
P-28, P-29, P-43, P-46, P-27, P-14, P-11. Registrar resultado en
`docs/RESULTADOS_PRUEBAS.md` (columna MANUAL → PASA/FALLA). Los fallos que salgan se
corrigen ANTES de escribir código nuevo: es más barato ahora que con dos features más
encima.

**A2. Documento de decisiones para la directora (1 sesión de Claude Code):**
Generar `docs/DECISIONES_DIRECTORA.md` consolidando TODO lo que espera validación,
agrupado por tema y con recomendación técnica en cada uno:
- Mecanismo de códigos: P-18 (código olvidado), P-20 (activación compartida), P-12.
- UX de reingreso: P-31 (login en cada entrada vs reingreso silencioso post-MVP).
- Contenido: P-23 (textos semilla), P-38 (cuestionario y umbrales), P-32/P-40/P-44.
- Identidad: P-09 (ícono), P-19 (correo de recuperación).
- Política de evidencias (pre-requisito de RF-33, ver Fase D).
Una sola reunión, todas las decisiones. Sin esto, la Fase D no arranca.

**A3. Limpieza:** P-30 (cuenta de prueba en Firebase), P-08/P-17/P-25/P-41/P-45
(actualizar entregables A2-6/xlsx — trabajo tuyo de documentación, no de código).

## Fase B — Panel administrativo (RF-40 a RF-44) + ciclo de contenido (2-3 días)

Va antes que las features restantes de la app porque completa el sistema de punta a
punta (hoy el contenido se siembra a mano — el panel lo vuelve operable por la
directora en el piloto), habilita RF-24/RF-25 casi gratis, y es riesgo bajo: código
separado que no puede romper el camuflaje de la app.

**B1. Fundación del panel (1 sesión):**
- Repo/carpeta nueva `aurora-admin` (fuera del APK, puede vivir junto al repo actual).
- Vite + React + TypeScript + SDK web de Firebase (A2-1). Sin backend propio.
- Autenticación de administradora (RF-40): SIN registro público — la cuenta admin se
  crea por consola/script y se marca con custom claim `admin` (script en tools/).
- Actualizar `firestore.rules`: contenido pasa de `write: false` a
  `write: if request.auth.token.admin == true`. Los datos de usuarias siguen
  inaccesibles para el rol admin (RNF-06 — probar explícitamente).

**B2. CRUD de contenido (1-2 sesiones):**
- RF-41 (contenido informativo), RF-42 (rutas, entidades, líneas), RF-43 (cuestionario
  con pesos y umbrales), respetando el schemaVersion existente.
- Flujo borrador → publicar (RF-44): la app solo lee lo publicado.
- Validaciones duras en el panel (teléfonos, umbrales coherentes) porque la app confía
  en la estructura.

**B3. Cierre del ciclo en la app (1 sesión):**
- RF-24 (actualización reflejada) y RF-25 (tolerancia a fallos de actualización):
  verificar contra contenido editado desde el panel, con esquema corrupto simulado.
- CP de RF-40–44 + regresión de RF-21/22/23/26/27.

## Fase C — Completar la app: cuenta y registros (2 días)

**C1. Ajustes y privacidad (1 sesión):** pantalla de ajustes del doble fondo que
resuelve en un solo lugar: RF-11 (reabrir tutorial), P-16 (releer consentimiento),
RF-09 (cambio de código secreto — reusa CreateSecretCodeUseCase; no recifra la BD,
decisión ya tomada en el ADR de SQLCipher) y RF-19 (cierre de sesión).

**C2. Eliminación de cuenta y datos (1 sesión):** RF-20 — en este modelo de amenaza
es casi crítico: la usuaria debe poder desaparecer por completo (BD local, Keystore,
Firestore, cuenta de Auth) en una sola acción con confirmación. Reusar la preparación
de borrado remoto del ADR de sync.

**C3. Registros completos (1 sesión):** RF-35 (edición) y RF-36 (eliminación con
propagación remota). CRUD completo del diario.

**C4. Fachada más creíble (media sesión):** RF-04 (pronóstico extendido) — barato y
refuerza RNF-05: una app de clima sin pronóstico extendido es rara.

## Fase D — RF-33: Evidencias adjuntas (1-2 días) — AL FINAL, con decisiones de A2

La feature más riesgosa por unidad de valor; requiere la política de evidencias
definida con la directora (Fase A2). Decisiones de diseño de partida:
- Photo Picker del sistema (sin permisos de galería/cámara — un permiso de cámara en
  una app de clima es un distinguidor, RNF-04/05).
- Sin captura de cámara propia en TRL 4 (la cámara del sistema deja rastro en galería;
  se documenta como advertencia a la usuaria).
- Limpieza de EXIF (GPS incrustado) ANTES de cifrar.
- Almacenamiento SOLO local: cifrado con la misma jerarquía de llaves de la BD, en
  almacenamiento interno. Sin sync de binarios en TRL 4 (Firestore no sirve para
  blobs; Storage se evalúa como trabajo futuro — verificar condiciones actuales del
  plan gratuito antes de prometerlo).
- La imagen original NO se borra de la galería de la usuaria (no es nuestra); la app
  advierte que la evidencia también existe fuera del espacio seguro.

## Fase E — Cierre v0.2.0

Repetir el protocolo del Día 7: corrida completa, matriz de CP actualizada, pasada
final de los tres agentes, manifest fusionado (¿Photo Picker agregó algo?), APK
release verificado, tag `v0.2.0-trl4`. Actualizar PENDIENTES.md y la sección de
trabajo futuro (RF-31, RF-38 fino, RF-39, reingreso silencioso P-31c, sync de
evidencias).

---

## Reglas de trabajo (sin cambios)

Un feature por sesión de Claude Code, `/clear` entre sesiones, plan antes de código,
tres agentes antes de cada commit, CP manuales al cierre de cada fase. El panel
(Fase B) tendrá su propio CLAUDE.md y sus propias reglas — pedirlo en la sesión B1.

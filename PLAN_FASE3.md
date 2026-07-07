# Aurora — Plan de Desarrollo Fase 3 (1 semana, app Android)

**Alcance:** solo la app Android. El panel web (RF-40 a RF-44) queda fuera; el contenido administrable se siembra manualmente en Firestore desde la consola de Firebase.

**Estrategia:** priorizar todos los RF de prioridad **Alta** alcanzables sin el panel. Los RF **Media** entran solo si sobra tiempo (lista al final).

---

## Antes de empezar (30 min, sin Claude Code)

1. Crear proyecto en la consola de Firebase (Auth con email/contraseña + Firestore) y descargar `google-services.json`.
2. Crear repositorio Git (privado).
3. Tener Android Studio y un emulador API 26+ listos.

## Día 1 — Fundación + fachada de clima

**RF-01, RF-02, RF-03 · RNF-05, RNF-07**

- Sesión 1: crear proyecto Android (Compose, Hilt, Room, min API 26), estructura de capas, ktlint, copiar `CLAUDE.md` y `docs/` a la raíz del repo. Primer commit.
- Sesión 2: identidad de app de clima (nombre, ícono, splash — usar manual de marca solo para el doble fondo interno, la fachada es 100 % clima), pantalla principal de clima.
- Sesión 3: integración Open-Meteo — búsqueda de ciudad (RF-02) y clima actual (RF-03).
- ✅ Cierre: la app compila, parece una app de clima real, busca ciudades y muestra clima.

## Día 2 — Doble fondo y camuflaje (el corazón del proyecto)

**RF-05, RF-06, RF-12, RF-13, RF-14, RF-16 · RNF-02, RNF-03, RNF-05**

- Sesión 1: activación inicial del doble fondo (RF-05) y detección del código secreto en la barra de búsqueda con respuesta indistinguible (RF-12, RF-13, RF-14).
- Sesión 2: FLAG_SECURE, ocultamiento en recientes, ausencia de indicios (RF-06, RNF-02).
- Sesión 3: salida rápida con limpieza de estado y cierre automático de sesión (RF-16, RNF-03).
- ✅ Cierre: pasar manualmente CP de camuflaje; probar que un código erróneo se ve exactamente como búsqueda fallida.

## Día 3 — Cuentas, consentimiento y código secreto

**RF-07, RF-08, RF-10, RF-15, RF-17, RF-18 · RNF-01 (parcial), RNF-04**

- Sesión 1: consentimiento informado (RF-17, RF-18) y creación de cuenta con Firebase Auth (RF-07).
- Sesión 2: creación del código secreto con protección en Keystore (RF-08) e inicio de sesión (RF-15).
- Sesión 3: tutorial del doble fondo (RF-10). Configurar Room + SQLCipher con llave en Keystore (base de RNF-01).
- ✅ Cierre: flujo completo activación → consentimiento → cuenta → código → tutorial → entrar/salir del doble fondo.

## Día 4 — Contenido informativo y rutas de atención

**RF-21, RF-22, RF-23, RF-26 · RNF-08**

- Sesión 1: modelo de contenido en Firestore + datos semilla reales de Santander (tipos de violencia, rutas, entidades, líneas). Escribir script o JSON de siembra.
- Sesión 2: pantallas de consulta de tipos de violencia (RF-21) y rutas de atención (RF-22).
- Sesión 3: líneas de atención con llamada directa (RF-23) y consulta sin conexión con persistencia de Firestore (RF-26).
- ✅ Cierre: contenido navegable con y sin conexión (probar en modo avión).

## Día 5 — Cuestionario orientador

**RF-27, RF-28, RF-29, RF-30**

- Sesión 1: modelo del cuestionario en Firestore (sembrado manualmente) y pantalla del cuestionario (RF-27).
- Sesión 2: cálculo del nivel orientativo y vínculo a rutas (RF-29), aviso de alcance orientador (RF-30).
- Sesión 3: descarte de respuestas en salida rápida (RF-28) — probar junto con RF-16.
- ✅ Cierre: cuestionario completo de punta a punta, incluida salida rápida a mitad del cuestionario.

## Día 6 — Registro de incidentes y sincronización cifrada

**RF-32, RF-34, RF-37 · RNF-01 completo**

- Sesión 1: registro de incidentes en Room+SQLCipher (RF-32) y listado cronológico (RF-34).
- Sesión 2: sincronización cifrada a Firestore con reglas de seguridad por cuenta (RF-37, RNF-06).
- Sesión 3: reglas de seguridad de Firestore escritas y probadas (usuaria solo lee/escribe lo suyo; contenido es solo lectura).
- ✅ Cierre: incidente creado offline aparece cifrado y sincronizado al volver la conexión.

## Día 7 — Pruebas, hardening y cierre

- Ejecutar los CP del plan de pruebas correspondientes a los RF implementados (`docs/PLAN_PRUEBAS.md`) y registrar resultados en una tabla (evidencia para el informe).
- Revisión de seguridad con Claude Code: pedir una auditoría del código contra las 7 reglas del `CLAUDE.md` (logs, FLAG_SECURE, strings, permisos del manifest).
- Corregir hallazgos, generar APK debug, taggear versión `v0.1.0-trl4`.
- Documentar lo pendiente (RF Media no implementados) como trabajo futuro.

---

## RF Media diferidos (solo si sobra tiempo, en este orden)

RF-04 (pronóstico extendido — refuerza el camuflaje), RF-19 (cierre de sesión), RF-09 (cambio de código), RF-31 (guardar resultado del cuestionario), RF-35/RF-36 (editar/eliminar incidentes), RF-33 (evidencias adjuntas), RF-38/RF-39 (sync diferida y multi-dispositivo), RF-20 (eliminación de cuenta), RF-11.

**Fuera de alcance de esta semana:** panel web (RF-40–44), RF-24/RF-25 (dependen del panel; se sustituyen con siembra manual en Firestore).

---

## Cómo trabajar con Claude Code eficientemente

1. **Una funcionalidad por sesión.** Usa `/clear` entre features; el contexto largo degrada resultados. El `CLAUDE.md` y los `docs/` le dan el contexto permanente.
2. **Planifica antes de codificar.** Inicia cada feature con plan mode (Shift+Tab) o pidiendo: "Lee docs/REQUERIMIENTOS.md, propón un plan para RF-XX y espera mi aprobación".
3. **Cita los RF en cada instrucción.** "Implementa RF-12 y RF-13 cumpliendo sus criterios de aceptación" funciona mucho mejor que describir la funcionalidad de nuevo.
4. **Verificación al cierre de cada feature.** Pide siempre: "Verifica contra los criterios de aceptación de RF-XX y ejecuta ./gradlew test lint". No aceptes "debería funcionar".
5. **Commits pequeños y frecuentes.** Un commit por RF o por sesión; te permite revertir sin dolor si una sesión sale mal.
6. **Tú pruebas en el emulador.** Claude Code no ve la pantalla del emulador; los criterios visuales y de UX del camuflaje los verificas tú tras cada sesión de cierre diario.
7. **Para el camuflaje, sé adversarial.** Pide periódicamente: "Actúa como auditora: busca en el código cualquier string, log, permiso o recurso que delate el propósito real de la app".
8. **Si algo se atasca >30 min, reinicia la sesión** con `/clear` y replantea la instrucción con más contexto en lugar de acumular intentos fallidos.

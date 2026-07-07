# Aurora — Contexto del proyecto


## Qué es Aurora
App Android nativa que se presenta como una app de clima funcional ("fachada"), pero contiene un "doble fondo": un espacio seguro con información, orientación y registro de incidentes para mujeres en situación de violencia. Piloto para Santander, Colombia. Prototipo TRL 4, trabajo de grado, una sola desarrolladora.

**El camuflaje es el requisito más importante.** Nada en la app visible (nombre, ícono, notificaciones, recientes, permisos, strings, nombre de paquete) puede sugerir su propósito real.

## Pila tecnológica (decisiones ya tomadas — no cambiar sin discusión)
- Kotlin nativo + Jetpack Compose, min API 26
- Arquitectura: MVVM + capas (ui / domain / data), inyección con Hilt
- Local: Room + SQLCipher (cifrado en reposo), llaves en Android Keystore
- Nube: Firebase (Auth, Firestore con persistencia offline), capa gratuita
- Clima: API Open-Meteo (sin API key)
- Tests: JUnit + pruebas instrumentadas; ktlint para estilo

## Reglas de seguridad innegociables
1. FLAG_SECURE en todas las pantallas del doble fondo (sin capturas ni vista en recientes) — RNF-02.
2. El acceso al doble fondo es SOLO por código secreto tecleado en la barra de búsqueda de ciudades. Si el código no coincide, la respuesta debe ser indistinguible de una búsqueda fallida de ciudad (RF-12, RF-13).
3. Salida rápida: un gesto/botón regresa inmediatamente a la fachada de clima y limpia estado sensible (RF-16, RNF-03).
4. Nunca registrar en logs, crashlytics ni analytics nada del doble fondo. Sin analytics de terceros.
5. Datos sensibles siempre cifrados en reposo (SQLCipher) y en tránsito; minimización de datos (RNF-01, RNF-04).
6. Strings, paquete, ícono y notificaciones solo con identidad de clima (RNF-05). "Aurora" se usa únicamente como marca de app de clima (decisión F1 del entregable A2-6: fenómeno del cielo, creíble en una app meteorológica; en la fachada solo sans neutra, sin símbolo squircle ni serif Marcellus). El propósito real no aparece en ningún elemento visible.
7. Nada de contenido sensible sin consentimiento informado previo (RF-17, RF-18).

## Documentos de referencia (leer antes de implementar una funcionalidad)
- `docs/ARQUITECTURA.md` — reglas de arquitectura y diseño de código. OBLIGATORIO cumplirlas; toda estructura nueva sigue esta guía.
- `docs/REQUERIMIENTOS.md` — todos los RF/RNF con criterios de aceptación. Cada tarea cita sus RF.
- `docs/PLAN_PRUEBAS.md` — casos de prueba CP-xx que la implementación debe pasar.
- `docs/mockups/` — wireframes y diseños de alta fidelidad (PNG). Ver el que corresponda ANTES de implementar cada pantalla.
- `docs/manual-de-marca.pdf` y `docs/marca/` — identidad visual y logos SVG.
- `PLAN_FASE3.md` — plan de la semana y alcance del MVP.

## Convenciones de trabajo
- Un feature por rama/commit lógico; mensajes de commit en español: `feat(clima): búsqueda de ciudad (RF-02)`.
- Cada RF implementado debe: cumplir sus criterios de aceptación, tener al menos un test, y compilar con `./gradlew assembleDebug lint test`.
- Antes de implementar, proponer plan breve; después de implementar, verificar contra los criterios de aceptación del RF y decir explícitamente cuáles se cumplen.
- Español para UI, comentarios y documentación; inglés para nombres de código (clases, funciones).
- No agregar dependencias nuevas sin justificación contra los criterios de A2-1 (seguridad, peso, gama baja).

## Agentes de revisión (usar proactivamente)

Hay tres subagentes en `.claude/agents/`. Invócalos sin que la usuaria lo pida:

1. **verificadora-rf** — al terminar cada RF, antes del commit: valida criterios de aceptación y ejecuta pruebas.
2. **revisora-arquitectura** — al cerrar cada feature o crear clases/paquetes/dependencias nuevas: audita contra `docs/ARQUITECTURA.md`.
3. **auditora-seguridad** — tras cualquier cambio en doble fondo, auth, cifrado, manifest, strings o recursos: audita camuflaje y seguridad.

Un feature no se considera terminado hasta que los tres den veredicto APROBADO / LISTO PARA COMMIT. Si un agente rechaza, corrige y vuelve a pasarlo.

## Comandos
```bash
./gradlew assembleDebug        # compilar
./gradlew test                 # tests unitarios
./gradlew connectedDebugAndroidTest  # instrumentados (requiere emulador)
./gradlew ktlintCheck          # estilo
```

# Aurora

Aplicación Android que funciona como una app de clima e incluye un espacio privado de orientación y registro para mujeres que enfrentan violencia de género.

Prototipo desarrollado como trabajo de grado de Ingeniería de Sistemas en la Universidad Autónoma de Bucaramanga (UNAB), 2026. Nivel de madurez TRL 4: validado en entorno controlado.

## Descripción

Para muchas mujeres que viven violencia, tener en el teléfono una aplicación que muestre de forma evidente que buscan ayuda puede ser un riesgo. Por eso Aurora se presenta como una aplicación de clima completa: muestra el pronóstico, permite buscar ciudades y no usa nombres, íconos, permisos ni notificaciones que revelen su propósito.

Al escribir una clave personal en la barra de búsqueda de ciudades, la aplicación abre el espacio privado. Si la clave no es correcta, la respuesta es la misma que la de una búsqueda sin resultados.

## Funcionalidades

- **Clima:** pronóstico actual y de cinco días con la API de Open-Meteo, búsqueda de ciudades y directorio local.
- **Guía:** información sobre tipos de violencia, rutas de atención, líneas y entidades de ayuda. El contenido se administra desde un panel web y se puede consultar sin conexión.
- **Registro:** bitácora de hechos con fecha, descripción y adjuntos. Permite editar, eliminar y exportar a un PDF protegido con contraseña. A las imágenes adjuntas se les quitan los metadatos EXIF.
- **Cuestionario:** instrumento de valoración con consentimiento informado y un resultado orientativo que se puede guardar.
- **Cuenta y respaldo:** los registros se respaldan cifrados en la nube y se pueden recuperar en otro teléfono.
- **Panel web:** aplicación en React para crear, revisar y publicar el contenido de la guía sin recompilar la app. No tiene acceso a los datos de las usuarias; esa separación está definida en las reglas de Firestore.

## Seguridad

- La base de datos local está cifrada con SQLCipher y las llaves se guardan en el Android Keystore.
- Los adjuntos se cifran con una llave propia.
- Para recuperar los datos en otro dispositivo, la llave de cifrado se guarda en la nube protegida con una llave derivada de la contraseña de la cuenta (PBKDF2-HMAC-SHA256 con 600 000 iteraciones y AES-256-GCM). El servidor no puede leer los registros.
- Salida rápida hacia la pantalla de clima, cierre de sesión por inactividad y al pasar a segundo plano, y bloqueo de capturas de pantalla en el espacio privado (`FLAG_SECURE`).
- No usa servicios de analítica de terceros.
- Una tarea de Gradle revisa el APK de release en cada compilación y detiene la construcción si encuentra palabras que puedan revelar el propósito de la aplicación, salvo las excepciones documentadas.

## Pruebas y evaluación

- 802 pruebas unitarias y 47 pruebas instrumentadas, sin fallos.
- ktlint y Android Lint sin errores.
- Pruebas manuales según el plan de pruebas del proyecto.
- Evaluación de seguridad con OWASP MASVS v2.1.0 y MASTG v2.0.0 sobre el APK de release. De los 20 controles evaluados, 17 se cumplen (7 sin observaciones y 10 con observaciones) y 3 no aplican. Se registraron 10 hallazgos: 7 corregidos, 1 informativo cerrado y 2 de severidad baja pendientes para antes de un piloto (firmar el APK con un certificado propio y retirar dos actividades exportadas por una librería).

## Arquitectura

La aplicación sigue el patrón MVVM con inyección de dependencias con Hilt. El código está organizado por módulo funcional y cada módulo se divide en las capas `ui`, `domain` y `data`.

```
app/src/main/java/com/auroraclima/app/
├── weather/        pantalla de clima (API Open-Meteo)
├── access/         entrada con clave, tutorial y configuración
├── accounts/       cuenta, sincronización, recuperación y borrado
├── content/        guía de orientación
├── records/        registro de hechos y exportación
├── questionnaire/  cuestionario de valoración
└── core/           seguridad, base de datos, red y sistema de diseño
```

Requiere Android 8.0 (API 26) o superior.

## Tecnologías

- Kotlin 2.0, Jetpack Compose (BOM 2025.02.00) y Material 3
- Hilt y Navigation Compose
- Room con SQLCipher y Android Keystore
- Firebase Authentication y Cloud Firestore
- Retrofit, OkHttp y kotlinx.serialization
- PDFBox-Android para la exportación a PDF
- JUnit, Robolectric y Compose UI Test
- Panel web: React 18, TypeScript, Vite y Vitest
- ktlint y Android Lint

## Compilar

```bash
./gradlew assembleDebug    # compilar la versión de depuración
./gradlew test             # pruebas unitarias
./gradlew ktlintCheck      # estilo de código
./gradlew assembleRelease  # versión de release, con la revisión de palabras
```

Se necesita un archivo `google-services.json` propio, que no se incluye en el repositorio.

## Documentación

La carpeta `docs/` contiene:

- `REQUERIMIENTOS.md`: 46 requisitos funcionales y 11 no funcionales con sus criterios de aceptación.
- `ARQUITECTURA.md`: arquitectura y registro de decisiones (ADR).
- `PLAN_PRUEBAS.md` y `RESULTADOS_PRUEBAS.md`: casos de prueba y resultados.
- `INFORME_SEGURIDAD_OWASP.md`: evaluación de seguridad completa.
- `PENDIENTES.md`: tareas abiertas y riesgos residuales.

## Alcance

Este repositorio corresponde a un prototipo académico y es público como muestra de trabajo. Una implementación para uso con usuarias reales tendría una identidad visual propia (nombre, ícono, nombre de paquete y forma de entrada distintos a los de este repositorio) y requeriría validación con usuarias y con organizaciones de atención.

## Autora

Leydy Yohana Macareo Fuentes · Ingeniería de Sistemas, Universidad Autónoma de Bucaramanga

[leydymacareo08@gmail.com](mailto:leydymacareo08@gmail.com) · [LinkedIn](https://www.linkedin.com/in/leydymacareo/)

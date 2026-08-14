# Aurora — Decisiones para validar con la directora

> **Propósito.** Consolidar en un solo lugar todas las decisiones del proyecto que
> dependen del criterio de la directora, para resolverlas en **una sola reunión**.
> Están agrupadas por tema, no por número de tarea. Cada punto trae un contexto
> breve, las opciones con sus ventajas y riesgos, una recomendación del equipo
> técnico con su porqué, y un espacio para dejar registrada la decisión y la fecha.
>
> **Cómo leer las opciones.** Todas las recomendaciones parten de un mismo modelo de
> amenaza: una app de clima que, para proteger a la usuaria, no puede revelar por
> ningún medio visible que también es un espacio seguro. El agresor típico que
> imaginamos tiene acceso físico al teléfono y revisa la pantalla, pero no
> descompila la aplicación ni es un perito informático. Cuando un riesgo se marca
> como «aceptado», significa que queda por fuera de lo que este prototipo (nivel de
> madurez TRL 4, trabajo de grado) puede resolver, y se documenta para el informe.
>
> **Estado:** ACTA — decisiones tomadas en la reunión con la directora del **2026-07-21**.
> Cada bloque registra la decisión adoptada. Última actualización: 2026-07-21.

---

## 1. Mecanismo de códigos de acceso

El ingreso al espacio seguro no tiene botón ni pantalla propia: la usuaria escribe un
**código personal** en la barra donde normalmente se buscan ciudades. Si el código es
correcto, se abre el espacio seguro; si no, la app responde exactamente como cuando no
encuentra una ciudad. Este mecanismo plantea tres decisiones.

### 1.1 ¿Qué pasa si la usuaria olvida su código? *(P-18)*

**Contexto.** Hoy no existe una forma de recuperar el espacio seguro si la usuaria
olvida su código personal. Por diseño, el código no se guarda en ninguna parte legible,
así que no se puede «recordárselo». Necesitamos definir una ruta de recuperación que no
debilite la seguridad.

**Opciones.**
- **A. Reinstalar y volver a entrar con la cuenta.** La usuaria borra la app (o los
  datos), la reinstala, usa de nuevo el código de activación general, inicia sesión con
  su correo y contraseña, y crea un código nuevo. Sus registros regresan desde la copia
  de la nube. *Ventaja:* no obliga a guardar el código en ningún sitio, así que no abre
  una puerta nueva. *Riesgo:* la recuperación de los registros depende de la copia en la
  nube, que hoy está atada a este mismo teléfono (ver punto 4.4); reinstalar en el mismo
  dispositivo sí los recupera.
- **B. Pregunta de seguridad o pista.** Guardar una pista para recordar el código.
  *Ventaja:* recuperación sin reinstalar. *Riesgo alto:* cualquier pista almacenada es
  un rastro que un tercero podría ver o deducir, y rompe la premisa de que nada en el
  teléfono delata el espacio seguro. **No recomendada.**

**Recomendación técnica:** opción **A**, y describirla como una nota del requisito de
código, no como una función visible dentro de la app. Es la única que no introduce un
rastro nuevo. Conviene explicarla a la usuaria durante el acompañamiento, no dejarla
escrita dentro de la aplicación.

> **Decisión:** APROBADA la **opción A** (reinstalar → código de activación → iniciar
> sesión con la cuenta existente → crear código nuevo; los registros regresan desde la
> copia en la nube al reinstalar en el mismo dispositivo). La ruta se explicará a la
> usuaria durante el acompañamiento, no se documenta dentro de la app. **P-18 cerrado**
> como decisión documentada.  **Fecha:** 2026-07-21

### 1.2 El código de activación es único y compartido *(P-20)*

**Contexto.** Para llegar por primera vez a la pantalla de registro se usa un **código
de activación general** (en la demo, «luna2026»), igual para todas las usuarias. Si ese
código circulara públicamente, cualquier persona podría llegar a la pantalla de registro
—no vería datos de nadie, pero se debilitaría el camuflaje del mecanismo.

**Opciones.**
- **A. Mantener un código único para el piloto.** *Ventaja:* simple, suficiente para un
  piloto acompañado con pocas usuarias. *Riesgo:* si se filtra, revela que «esta app de
  clima esconde algo».
- **B. Códigos por fundación o rotativos.** Un código distinto por organización aliada,
  o que cambia periódicamente. *Ventaja:* una filtración afecta a un grupo pequeño y se
  puede revocar. *Riesgo:* requiere infraestructura de gestión que hoy no existe (queda
  para el panel administrativo, trabajo futuro).

**Recomendación técnica:** para el prototipo TRL 4, **mantener el código único (A)** y
registrarlo como limitación conocida, con **B como trabajo futuro**. La mitigación
práctica en el piloto es entregar el código en persona y no por escrito.

> **Decisión:** APROBADA la **opción A**: código único para el piloto, entregado en
> persona. Los códigos por fundación o rotativos (opción B) quedan como **trabajo
> futuro**. **P-20 cerrado** como limitación documentada.  **Fecha:** 2026-07-21

### 1.3 El texto que se escribe en la barra viaja a internet *(P-12)*

**Contexto.** Como la barra debe comportarse igual que una búsqueda real de ciudades,
cualquier texto que se escriba —incluido un código correcto— se envía al servicio de
clima para buscar la ciudad, igual que cualquier otra búsqueda. Esto es intencional: si
el código *no* viajara, la barra se comportaría distinto al escribirlo y eso mismo lo
delataría.

**Mitigación ya implementada.** Para reducir el riesgo, la app **verifica el código sin
conexión** contra un listado de ciudades guardado dentro del teléfono, y exige que el
código tenga al menos 6 caracteres y **no** sea un nombre de ciudad. Así, en el momento
de *crear* el código, este nunca sale del dispositivo. Lo que sí sale, después, es el
texto tecleado al *usarlo*, como cualquier búsqueda.

**Qué se valida con la directora:** que este compromiso —indistinguibilidad de la barra
a cambio de que el texto viaje como una búsqueda normal— es aceptable para la población
del piloto. No hay una alternativa que conserve el camuflaje y evite que el texto viaje.

**Recomendación técnica:** aceptar el riesgo, documentado, con la mitigación descrita.

> **Decisión:** riesgo **ACEPTADO** y ratificado, con la mitigación descrita
> (verificación offline del código, mínimo 6 caracteres, prohibición de nombres de
> ciudad). **P-12 cerrado** como riesgo ratificado.  **Fecha:** 2026-07-21

---

## 2. Experiencia de reingreso *(P-31)*

**Contexto.** Cada vez que la usuaria sale del espacio seguro (incluida la salida
rápida de emergencia), la app **cierra por completo la sesión**. Al volver a entrar
debe teclear otra vez su código personal **y** su correo y contraseña. Esto es
deliberado: si la sesión quedara abierta, en el teléfono quedarían guardados el correo y
señales de una cuenta activa, y una app de clima «con cuenta iniciada» rompería la
negación plausible. El costo, detectado al usar la app, es que pedir el código y además
las credenciales en cada entrada es exigente y, bajo estrés, puede empujar a la usuaria
a poner contraseñas débiles o a abandonar.

**Opciones.**
- **A. Mantener el ingreso completo en cada entrada (recomendada).** Máxima seguridad y
  coherencia con el modelo de amenaza; medir en el piloto cuánto molesta en la práctica.
- **B. No cerrar la sesión al salir.** Reingreso más cómodo, pero **reintroduce el
  problema**: quedarían en el teléfono el correo y rastros de sesión. **No recomendada**
  sin una mitigación que hoy no tenemos.
- **C. Reingreso silencioso (trabajo futuro).** Guardar las credenciales cifradas de
  forma segura y entrar automáticamente tras validar solo el código; la usuaria solo
  vería el login completo si algo falla. Es lo mejor de ambos mundos, pero requiere
  desarrollo adicional que queda **fuera del prototipo actual**.

**Recomendación técnica:** **A para el piloto TRL 4**, midiendo el costo real de uso, y
dejar **C como la evolución natural** para después del prototipo.

> **Decisión (a / b / c):** **A** para el piloto (ingreso completo en cada entrada,
> midiendo el costo real de uso). El **reingreso silencioso (C)** queda como **trabajo
> futuro priorizado**. **P-31 cerrado** con esta decisión.  **Fecha:** 2026-07-21

---

## 3. Contenido y cuestionario

Todo el texto sensible que ve la usuaria (información sobre tipos de violencia, rutas de
atención, teléfonos, y el cuestionario de orientación) es **contenido semilla marcado
como BORRADOR A VALIDAR**. Requiere revisión experta antes de llegar a usuarias reales.
Como salvaguarda, el sistema **no publica** ningún contenido que aún tenga datos por
confirmar.

### 3.1 Textos, rutas y teléfonos *(P-23)*

**Contexto.** Los textos de información, las rutas de atención, los teléfonos y
direcciones de Bucaramanga, la línea departamental de Santander y los horarios están
cargados como borrador, y varios campos están marcados como **«por confirmar»**.

**Qué se necesita de la directora:**
- Validar que los **tipos de violencia** y su redacción se ajustan a la Ley 1257 y usan
  un **lenguaje no revictimizante**.
- Confirmar, con fuentes oficiales, los **teléfonos, direcciones y horarios**.
- En particular, **la línea departamental no se publicará** hasta tener su número
  verificado.

**Recomendación técnica:** revisar y confirmar cada dato antes del piloto; el sistema ya
impide que cualquier dato «por confirmar» llegue a una usuaria.

> **Decisión / observaciones:** contenido, textos, rutas y teléfonos **VALIDADOS** por
> la directora. La usuaria entregará los datos verificados para cargarlos; la validación
> **ya no bloquea**: queda pendiente **solo la carga** del contenido validado.
> **Fecha:** 2026-07-21

### 3.2 El cuestionario de orientación y sus umbrales de riesgo *(P-38, y P-41 sobre la presentación del nivel)*

**Contexto.** El cuestionario es un instrumento de **elaboración propia** (seis
preguntas), pensado para orientar, no para diagnosticar. Cada respuesta suma un peso, y
la suma ubica a la usuaria en un nivel de riesgo. Los umbrales propuestos son:

| Nivel | A partir de |
|---|---|
| Variable | 0 puntos |
| Moderado | 4 puntos |
| Grave | 10 puntos |
| Extremo | 17 puntos |

Además, ciertas preguntas críticas **elevan por sí solas** el nivel mínimo: daño físico,
daño sexual o miedo intenso llevan al menos a «Grave»; las amenazas, al menos a
«Extremo». Al final se muestra siempre el aviso de que es una **orientación preliminar
que no sustituye la evaluación de un profesional**.

**Por qué es la validación más delicada.** Un umbral mal calibrado que le diga «riesgo
variable» a quien en realidad corre riesgo extremo es **más dañino** que un teléfono sin
verificar. Por eso el instrumento completo está retenido hasta contar con visto bueno
experto.

**Qué se necesita de la directora:** validar **las seis preguntas**, su redacción no
revictimizante, **los pesos**, **los umbrales de cada nivel**, los mínimos que imponen
las preguntas críticas y las **rutas recomendadas** por nivel.

**Sobre cómo se muestra el resultado *(P-41)*.** Por decisión de diseño, el resultado se
comunica **con palabras**, no con semáforos ni colores de alarma: el nombre del nivel es
el protagonista, con un acento de color sobrio que apenas gradúa dentro de una misma
paleta serena (de un azul cielo a un tono ciruela). Se pide a la directora **ratificar
esa gradación** junto con los umbrales. También conviene saber que, en este prototipo, el
resultado **se muestra una sola vez y no se guarda** (guardarlo con consentimiento
expreso queda como trabajo futuro).

**Recomendación técnica:** revisar el instrumento como un todo (preguntas + pesos +
umbrales + rutas). Tras el visto bueno, se retira la marca de borrador y se publica.

> **Decisión / ajustes a umbrales o preguntas:** cuestionario **VALIDADO** en su
> conjunto (seis preguntas, pesos, umbrales, pisos de preguntas críticas y rutas), y la
> gradación por palabras/acento sobrio de P-41 **RATIFICADA**. La usuaria entregará los
> datos verificados para cargarlos; la validación **ya no bloquea**: queda pendiente
> **solo la carga**.  **Fecha:** 2026-07-21

> **Decisión posterior — REVERSIÓN INFORMADA de la presentación de P-41:** la
> directora aprueba usar **colores semafóricos representativos** en el resultado
> (Variable→verde sereno, Moderado→ámbar, Grave→naranja, Extremo→rojo profundo),
> sustituyendo la gradación sobria dentro de una sola paleta ratificada el
> 2026-07-21. Se conserva todo lo demás de aquella decisión: el nombre del nivel
> sigue siendo el protagonista **en palabras** (los textos validados no cambian),
> el color solo refuerza y nunca informa solo, y los tintes mantienen la
> suavidad de la paleta serena (sin rojos de alarma estridentes). En código, la
> escala vive como cuatro tokens semánticos nuevos del tema V2 con nombres
> neutros de avisos meteorológicos (`accentSeverity1..4`, contraste AA
> verificado por test) y el mapeo es por el orden del nivel con degradación
> tolerante. En la misma decisión se aprueba implementar **RF-31 (guardado
> opcional del resultado)**, que deja de ser trabajo futuro: guardado opt-in
> fechado de nivel + tipos detectados (sin respuestas individuales, RNF-04),
> cifrado, sincronizado (RF-39) y con eliminación individual y cascada RF-20.
> Ver ADR 2026-08-14 en `docs/ARQUITECTURA.md`. **Fecha:** 2026-08-14

---

## 4. Identidad y rastros

Este bloque agrupa decisiones sobre lo que la app muestra hacia afuera y sobre riesgos
técnicos que el equipo propone **aceptar de forma explícita** para el prototipo. En los
puntos de «riesgo aceptado», no se pide elegir entre opciones: se pide que la directora
**quede informada y ratifique** que son asumibles para un TRL 4.

### 4.1 El ícono de la aplicación *(P-09)*

**Contexto.** El ícono visible es un amanecer, coherente con una app de clima. Conviene
verificar que ese mismo arte **no circule idéntico** en material institucional de
fundaciones u organizaciones de atención, porque una coincidencia podría asociar el
ícono con esos entornos y despertar sospecha.

**Recomendación técnica:** confirmar con la directora que el arte del ícono es
suficientemente genérico y no coincide con logotipos de entidades del sector antes de
difundir la app.

> **Decisión:** ícono **CONFIRMADO** por la directora (arte suficientemente genérico, sin
> coincidencia con material institucional del sector). **P-09 cerrado.**
> **Fecha:** 2026-07-21

### 4.2 El correo de recuperación de contraseña *(P-19)*

**Contexto.** Si la usuaria pide recuperar su contraseña, el sistema de cuentas envía un
**correo de restablecimiento** a su bandeja de entrada. Ese correo es un rastro que
queda **fuera del camuflaje**: aparece en el correo personal de la usuaria.

**Opciones.**
- **A. Personalizar la plantilla del correo con identidad 100 % de clima.** El mensaje
  luce como notificación de una app meteorológica. *Ventaja:* se conserva la
  recuperación de contraseña sin delatar nada. *Riesgo:* requiere cuidar la redacción de
  esa plantilla.
- **B. Dejar la recuperación de contraseña fuera del prototipo** y documentarlo.
  *Ventaja:* cero rastros por este flujo. *Riesgo:* si la usuaria olvida la contraseña,
  no tiene autoservicio para recuperarla.

**Recomendación técnica:** **A**, personalizar el correo con identidad de clima, que
conserva la función sin romper el camuflaje.

> **Decisión (a / b):** APROBADA la **opción A** (plantilla del correo con identidad
> 100 % de clima). **P-19 queda abierto solo con la tarea operativa**: personalizar la
> plantilla en la consola de Firebase (Authentication → Templates → *Password reset*).
> **Fecha:** 2026-07-21

**Texto sugerido de la plantilla (100 % clima, en español).** Para pegar en la consola
de Firebase (Authentication → Templates → *Password reset*). Firebase inserta el enlace
en el marcador `%LINK%` y el nombre de la app en `%APP_NAME%`.

- **Nombre del remitente:** `Aurora Clima`
- **Asunto:** `Aurora Clima: restablece el acceso a tu cuenta`
- **Cuerpo:**

  > Hola:
  >
  > Recibimos una solicitud para restablecer el acceso a tu cuenta de **Aurora Clima**,
  > tu app del tiempo.
  >
  > Para elegir una nueva contraseña y seguir consultando el pronóstico, abre este
  > enlace:
  >
  > %LINK%
  >
  > Si no pediste este cambio, puedes ignorar este mensaje: tu cuenta seguirá igual y no
  > se hará ninguna modificación.
  >
  > Buen tiempo,
  > El equipo de Aurora Clima

*Nota de camuflaje:* el mensaje no menciona ubicaciones, códigos, claves personales ni
nada del espacio seguro; solo la identidad meteorológica. No añadir logotipos ni enlaces
adicionales que puedan asociarse a entidades del sector.

### 4.3 Redacciones internas legibles solo al descompilar la app *(P-32, P-40, P-44)*

**Contexto.** Algunos textos que la usuaria ve en pantalla (los del tutorial de acceso,
los del cuestionario y las etiquetas del registro de incidentes) quedan **guardados
dentro del archivo de la aplicación**. Ninguno usa palabras prohibidas y, por separado,
cada palabra es neutra; pero un perito informático que **descompile** el archivo podría
leerlas juntas y deducir el propósito real. Esto **no** es visible para quien solo usa o
revisa el teléfono; solo aparece con herramientas técnicas avanzadas.

- **P-32:** los textos del **tutorial** explican cómo se entra al espacio seguro (deben
  hacerlo, para poder enseñarlo).
- **P-40:** dos frases del **cuestionario** describen que las respuestas no se guardan.
- **P-44:** las etiquetas del **registro** («¿de qué tipo fue?», los tipos de violencia)
  leídas juntas sugieren un diario de hechos.

**Por qué se acepta.** El agresor de nuestro modelo de amenaza no descompila la app.
Existe una mitigación futura (servir esos textos desde la nube en vez de incrustarlos),
que queda como mejora posterior.

**Recomendación técnica:** **ratificar como riesgo residual aceptado** para TRL 4, con la
mitigación documentada como trabajo futuro.

> **Ratificado (sí / con reservas):** **RATIFICADO** (sí). P-32, P-40 y P-44 quedan como
> riesgos residuales aceptados para TRL 4, con la mitigación (servir esos textos desde la
> nube) documentada como trabajo futuro. **Cerrados.**  **Fecha:** 2026-07-21

### 4.4 Riesgos técnicos residuales a ratificar *(P-35, P-47, P-48)*

**Contexto.** Tres riesgos técnicos adicionales que el equipo propone aceptar,
explicados en lenguaje sencillo:

- **P-35 y P-47(b): información protegida en la memoria mientras la app está abierta.**
  Mientras Aurora está en uso, la «llave» que abre los datos protegidos y el texto que la
  usuaria escribe viven en la memoria del teléfono. La salida rápida **no borra esa
  memoria**, porque forzar ese borrado podría corromper los datos reales en pleno uso.
  Solo un volcado técnico de memoria del proceso podría acceder a ello, algo que excede
  el modelo de amenaza del prototipo.
- **P-48: la copia en la nube solo se puede leer desde este teléfono.** Los registros que
  se respaldan en la nube van cifrados con una llave atada a **este dispositivo**. Eso
  significa que hoy la nube es un **respaldo de acceso restringido, no una restauración**:
  si se reinstala en el mismo teléfono se recuperan, pero entrar desde **otro
  dispositivo** no los restaura. La recuperación entre dispositivos es trabajo futuro y
  reabrirá la discusión sobre cómo derivar esa llave. *(La usuaria ya aprobó mantener el
  cifrado atado al dispositivo en esta etapa.)*

**Recomendación técnica:** **ratificar los tres como riesgos residuales aceptados** para
TRL 4; todos tienen una vía de mejora documentada para después del prototipo.

> **Ratificado (sí / con reservas):** **P-35 y P-47(b) RATIFICADOS** como riesgos
> residuales aceptados. **P-48 CAMBIA:** por decisión de la directora y la usuaria, la
> **recuperación multi-dispositivo (RF-39) ENTRA al alcance de la entrega final** —se
> reabre como trabajo planificado para la **Fase D2** (exigirá una llave recuperable, lo
> que reabre P-37b). El respaldo ligado al dispositivo se mantiene solo hasta esa fase.
> **Fecha:** 2026-07-21

---

## 5. Política de evidencias *(pre-requisito de RF-33)*

**Contexto.** Está previsto que, más adelante, la usuaria pueda **adjuntar imágenes** a
un registro de incidente (por ejemplo, una foto de una lesión o de un mensaje). Es la
función **más delicada** de todo el proyecto en términos de seguridad, y **no se
construirá hasta acordar su política** con la directora. Se necesitan definiciones de
fondo antes de escribir una sola línea.

**Puntos a decidir.**

1. **¿Qué debe poder adjuntarse en el prototipo?** La propuesta era **solo imágenes
   elegidas desde la galería del teléfono**, usando el selector del propio sistema
   Android. *Por qué:* así la app **no necesita permiso de cámara ni de galería**, y un
   permiso de cámara en una app de clima sería justo la clase de detalle que delata. No
   se incluiría una cámara propia dentro de Aurora en esta etapa.

   > **Ampliación aprobada (2026-07-21).** La política se amplía a tres vías de captura,
   > todas **sin permisos delatores**:
   > - **(i) Imágenes de la galería** vía el selector del sistema (propuesta original).
   > - **(ii) Foto con la cámara DEL SISTEMA** (app de cámara de Android, no una cámara
   >   propia), con **salida directa al almacén cifrado privado** de Aurora — sin pedir
   >   permiso de cámara y **sin pasar por la galería**. Así la evidencia no deja copia
   >   fuera del espacio seguro.
   > - **(iii) Adjuntar archivos existentes** (PDF, documentos, audio) vía el **selector
   >   del sistema**.
   >
   > **Excluida:** la **grabación de audio propia** dentro de Aurora, porque requeriría
   > el permiso de micrófono, que sería un detalle delator. (Sí se puede adjuntar un
   > audio ya existente por la vía (iii).)

2. **¿Es suficiente advertir sobre los rastros en la galería?** La imagen original
   **sigue existiendo en la galería** de la usuaria: no es un archivo de Aurora y la app
   no puede (ni debe) borrarla del teléfono. La propuesta es que Aurora **avise
   claramente** que la evidencia también existe fuera del espacio seguro. *A validar:* si
   esa advertencia es suficiente, o si la política debe pedir a la usuaria pasos
   adicionales (por ejemplo, orientarla sobre cómo manejar esas fotos en su galería).
   Como medida técnica, al guardar la copia protegida se **elimina la ubicación GPS**
   incrustada en la imagen.

3. **¿Las evidencias deben quedarse solo en el dispositivo?** La propuesta es **sí**:
   guardar las evidencias **cifradas y únicamente en el teléfono**, sin subirlas a la
   nube en esta etapa. *Por qué:* el servicio de nube gratuito no es adecuado para
   archivos, y mantener las evidencias solo en el dispositivo reduce la superficie de
   exposición. Sincronizarlas se evaluaría como trabajo futuro.

**Recomendación técnica:** aprobar la política mínima —**galería del sistema, sin permiso
de cámara; solo en el dispositivo y cifradas; con advertencia explícita sobre el rastro
en la galería y borrado de la ubicación GPS**— como base para construir la función.

> **Política aprobada / ajustes:** **APROBADA Y AMPLIADA.** Se conserva la base
> (evidencias **solo en el dispositivo y cifradas**, **sin permiso de cámara ni de
> galería**, con **advertencia explícita** sobre el rastro en la galería y **borrado de
> la ubicación GPS**) y se amplían las vías de captura a tres: (i) imágenes de la
> galería, (ii) **foto con la cámara del sistema con salida directa al almacén cifrado
> privado** (sin permiso de cámara, sin pasar por la galería) y (iii) **adjuntar
> archivos existentes** (PDF, documentos, audio) vía el selector del sistema. Se
> **excluye** la grabación de audio propia (requeriría permiso de micrófono, delator).
> Base aprobada para construir RF-33.  **Fecha:** 2026-07-21

---

## 6. Propuesta de cambio de la política de evidencias: sincronización cifrada *(2026-07-27, pendiente de ratificación)*

**Contexto.** La política aprobada en §5 estableció que las evidencias viven **solo en el
dispositivo**, con dos motivos: el servicio de nube gratuito no es adecuado para archivos
y mantenerlas locales reducía la superficie de exposición. Con la recuperación
multi-dispositivo (RF-39) ya construida, esa política dejaba un hueco que la propia acta
de la reunión anticipó: el criterio de aceptación de RF-39 dice «registros y **evidencias**
íntegros», y con la política vigente las evidencias se perdían al cambiar de teléfono.

**Qué se propone (e implementó de forma reversible).** Extender a las evidencias el mismo
esquema de llave envuelta de los registros:

- Cada adjunto viaja a la nube **cifrado con la llave recuperable** (la misma DEK del
  respaldo de registros, que solo se abre con la contraseña de la cuenta) y **troceado**
  en documentos de ~640 KB — así cabe en la base de datos gratuita (límite de 1 MiB por
  documento) **sin contratar nada ni registrar tarjeta**; se descartó Firebase Storage
  precisamente porque hoy exige plan de pago.
- Los **metadatos** (a qué registro pertenece, tipo, fecha) también viajan **sellados**:
  en la nube no hay nada legible — ni siquiera se distingue una foto de un PDF.
- En el teléfono **nada cambia**: los archivos siguen cifrados con la llave del
  dispositivo, los originales de galería no se tocan y la advertencia del rastro se
  mantiene.
- La eliminación (de un registro, RF-36, o de la cuenta, RF-20) **borra también las
  copias en la nube**, y el caso «contraseña restablecida» las purga junto con los
  registros.

**Contrapartidas a valorar:** (a) las evidencias pasan a existir cifradas fuera del
teléfono — ilegibles sin la contraseña, pero presentes en el proyecto de Firebase; (b) el
respaldo consume cuota gratuita (1 GiB de almacenamiento): con los topes vigentes
(10 adjuntos × 10 MB por registro) el margen es amplio para un piloto, y se documenta
como límite operativo; (c) el mismo residual de fuerza bruta offline de los registros
(P-62) aplica ahora a las evidencias.

> **Ratificación (sí / con reservas / revertir):** PENDIENTE. La usuaria aprobó el cambio
> el 2026-07-27 y la implementación quedó lista y probada; si la directora lo rechaza, se
> desactiva la subida (los adjuntos vuelven a ser solo locales) sin tocar el resto de
> RF-39.  **Fecha:** —

---

## Tabla resumen: decisión → pendiente(s) que cierra → impacto en el código

| # | Decisión a tomar | Pendiente(s) | Impacto en el trabajo técnico |
|---|---|---|---|
| 1.1 | Ruta para código olvidado (reinstalar + reingreso) | P-18 | Define un requisito nuevo o una nota; poco código, sobre todo acompañamiento a la usuaria |
| 1.2 | Código de activación único vs. por fundación | P-20 | Se documenta como limitación; los códigos por fundación son trabajo futuro (panel) |
| 1.3 | Aceptar que el texto tecleado viaje como búsqueda | P-12 | Ninguno nuevo: ratifica lo ya implementado (verificación offline del código) |
| 2 | Modelo de reingreso (a/b/c) | P-31 | «a» no cambia nada; «b» exige mitigación; «c» es desarrollo futuro |
| 3.1 | Validar textos, rutas y teléfonos | P-23 | Desbloquea la publicación del contenido; sin código, es carga de datos verificados |
| 3.2 | Validar preguntas, pesos y umbrales del cuestionario | P-38, P-41 | Desbloquea la publicación del cuestionario; ajustes de umbrales son solo datos |
| 4.1 | Confirmar que el ícono no coincide con material institucional | P-09 | Ninguno o cambio de arte del ícono |
| 4.2 | Correo de recuperación: plantilla de clima vs. excluir | P-19 | «a» = ajustar plantilla en consola; «b» = documentar exclusión |
| 4.3 | Ratificar redacciones internas legibles al descompilar | P-32, P-40, P-44 | Ninguno ahora; mitigación (servir textos desde la nube) es trabajo futuro |
| 4.4 | Ratificar riesgos técnicos residuales | P-35, P-47(b), P-48 | Ninguno ahora; mejoras documentadas para después del prototipo |
| 5 | Aprobar la política de evidencias | pre-requisito **RF-33** | Habilita construir la función de adjuntar imágenes (la más delicada) |

---

## Cobertura de este documento (verificación de barrido)

**Pendientes que requieren validación de la directora, incluidos aquí:** P-09, P-12,
P-18, P-19, P-20, P-23, P-31, P-32, P-35, P-38, P-40, P-41, P-44, P-47(b), P-48.
*(Son todos los que en `PENDIENTES.md` tienen como vía de resolución «validación con la
directora» o piden explícitamente validar con ella.)*

**Tareas de documentación de la usuaria, mencionadas para transparencia pero que NO son
decisiones de la directora** (actualizar entregables o verificar en emulador para
reflejar redacciones de camuflaje ya aplicadas): P-08 y P-17 (ajustes al entregable de
requisitos y a mockups), P-25, P-45 (mockups), P-28-final y P-30 (verificaciones en
emulador y limpieza de la cuenta de prueba). Se listan aquí para dejar constancia del
barrido completo; su resolución corresponde a la usuaria, no a esta reunión.

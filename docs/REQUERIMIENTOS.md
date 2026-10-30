# Aurora — Especificación de Requerimientos (extracto para desarrollo)

> Fuente: `entregables/fase 1/Aurora_Modelado_Requerimientos.xlsx`. No editar aquí; el xlsx es la fuente de verdad.

## RF-01 — Identidad de aplicación de clima
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** -

El sistema debe presentarse con nombre, ícono y pantalla inicial de una aplicación de clima, sin referencia alguna a su propósito real.

**Criterios de aceptación:**
1. El ícono, el nombre y la pantalla inicial corresponden a una app de clima genérica.
2. Ningún texto o recurso visible hace referencia a Aurora o a violencia de género.

*Notas:* Base del mecanismo de camuflaje (HU-01).

> **Refinamiento F1 (A2-6, 2026-07-07):** la decisión de diseño F1 fija «Aurora» como
> nombre de la app de clima (fenómeno del cielo, creíble como marca meteorológica).
> El criterio 2 debe leerse como "ningún texto o recurso visible hace referencia a
> Aurora *como espacio seguro* o a violencia de género". Pendiente reflejar este
> ajuste en el xlsx fuente (ver P-08 en `docs/PENDIENTES.md`).

## RF-02 — Búsqueda y selección de ciudad
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-01

El sistema debe permitir buscar ciudades por nombre y seleccionar una como predeterminada.

**Criterios de aceptación:**
1. Al escribir el nombre de una ciudad se muestran coincidencias.
2. Al seleccionar una ciudad se muestra su clima.
3. La ciudad seleccionada queda recordada como predeterminada al reabrir la app.

*Notas:* La barra de búsqueda es también la puerta del doble fondo (RF-12). **v0.3.2 (evaluación heurística, H-1):** la búsqueda y el clima muestran un indicador de carga y, ante un fallo de red o tras 10 s sin respuesta, un mensaje de error con «Reintentar». Al elegir una ciudad no se muestra el clima guardado de otra: el respaldo local vale solo para la ciudad predeterminada (RF-03).

## RF-03 — Clima actual
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-02

El sistema debe mostrar el clima actual real de la ciudad seleccionada y, sin conexión, los últimos datos con su hora de actualización.

**Criterios de aceptación:**
1. Con conexión se muestran temperatura y condición reales.
2. Sin conexión se muestran los últimos datos con hora de actualización visible.

*Notas:* Sostiene la credibilidad de la fachada.

## RF-04 — Pronóstico extendido
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-02, RF-03

El sistema debe mostrar el pronóstico meteorológico de al menos 5 días.

**Criterios de aceptación:**
1. Desde la vista de una ciudad se accede a la previsión de mínimo 5 días.

*Notas:* Refuerza la funcionalidad genuina de la fachada.

## RF-05 — Activación inicial del doble fondo
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-02, RNF-05

El sistema debe habilitar el primer acceso al doble fondo únicamente mediante un código de activación inicial ingresado en la barra de búsqueda.

**Criterios de aceptación:**
1. El código de activación ingresado en la barra de búsqueda abre el proceso de creación de cuenta.
2. Ninguna otra acción de la interfaz habilita el doble fondo.

*Notas:* El código de activación se difunde por canales seguros (fundaciones, material institucional). Validar mecanismo con la directora. **v0.3.2 (evaluación heurística):** con el código de activación se abre primero una bienvenida (logo, qué es Aurora y «Siguiente») y desde ella el consentimiento (RF-17). El descarte de un registro interrumpido (hallazgo A, RF-39) corre al abrir la bienvenida.

## RF-06 — Ausencia de indicios del doble fondo
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-05, RNF-05

El sistema no debe exponer ningún menú, pantalla o indicio del doble fondo fuera del ingreso por código.

**Criterios de aceptación:**
1. La exploración completa de la interfaz de clima no revela menús, ajustes ni textos asociados al doble fondo.

*Notas:* Verificado con la lista de verificación de camuflaje (RNF-05).

## RF-07 — Creación de cuenta
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-05, RF-17

El sistema debe permitir crear una cuenta con correo y contraseña de mínimo 8 caracteres, validando correos duplicados y contraseñas débiles.

**Criterios de aceptación:**
1. Con correo válido y contraseña de mínimo 8 caracteres la cuenta se crea y continúa a la definición del código secreto.
2. Un correo ya registrado es rechazado sin revelar datos de terceros.
3. Una contraseña débil es rechazada indicando los requisitos.

*Notas:* Solo accesible tras la activación del doble fondo y la aceptación del consentimiento. **v0.3.2 (evaluación heurística):** las reglas de la contraseña de la cuenta se muestran bajo el campo y se marcan en vivo mientras se escribe; cada regla se anuncia a los lectores de pantalla con su texto y su estado («cumplida» o «pendiente»). Todos los textos dicen «contraseña de la cuenta», para distinguirla del código de acceso y de la contraseña del archivo PDF (`TerminologyGuardTest`).

## RF-08 — Creación de código secreto
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-07

El sistema debe permitir a la usuaria definir un código secreto personal de mínimo 6 caracteres, distinto del nombre de una ciudad real, almacenado de forma no legible.

**Criterios de aceptación:**
1. Un código de mínimo 6 caracteres que no sea nombre de ciudad real queda registrado.
2. Nombres de ciudad o códigos de menos de 6 caracteres son rechazados explicando la regla.
3. El código nunca se almacena en texto legible.

*Notas:* Reemplaza al código de activación para los accesos siguientes. **v0.3.2 (evaluación heurística):** la pantalla se titula «Tu código de acceso.» y aclara que el código es distinto de la contraseña de la cuenta y que se escribe en el buscador de ciudades. «Código de acceso» reemplazó en todos los textos a «clave personal» y sus variantes; la terminología de las cuatro credenciales está en `docs/MANUAL_TECNICO.md` §7.1.

## RF-09 — Cambio de código secreto
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-08, RF-15

El sistema debe permitir cambiar el código secreto previa confirmación del código secreto vigente, aplicando las mismas reglas de creación.

**Criterios de aceptación:**
1. El cambio de código exige confirmar el código secreto vigente.
2. El nuevo código cumple las mismas reglas del RF-08.

*Notas:* Disponible desde la configuración dentro del doble fondo. **Refinamiento C1 (2026-07-17, decisión de la usuaria):** la credencial exigida es el **código secreto vigente**, no la contraseña de la cuenta: cada ingreso al doble fondo ya pasa por inicio de sesión con contraseña (P-31, `AuthSessionCleaner`), exigirla de nuevo duplicaría la fricción bajo estrés, y verificar el código vigente mantiene la comprobación 100 % local (misma puerta de RF-12, tiempo constante) y prueba la posesión de la credencial que se reemplaza. Ya reflejado en el xlsx fuente (`Aurora_Modelado_Requerimientos.xlsx`, fila RF-09) y en el pie de P21·b del docx de A2-6 (2026-07-17); queda el arte del mockup P21·b — ver P-52 en `PENDIENTES.md`. **Refinamiento v0.3.2 (evaluación heurística):** el código nuevo se escribe dos veces («Escríbelo otra vez», igual que en la creación de RF-08). Si no coinciden no se guarda nada, el código vigente sigue funcionando y el mensaje («Los códigos de acceso no coinciden.») no repite ninguno de los dos.

## RF-10 — Cómo usar Aurora (antes «Tutorial del doble fondo»)
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-08

El sistema debe presentar, tras la creación del código, una guía del doble fondo («Cómo usar Aurora») que incluya una práctica de ingreso con el código y de salida rápida.

**Criterios de aceptación:**
1. Al finalizar la creación del código inicia la guía (ingreso, recorrido por Inicio y salida rápida).
2. La usuaria puede realizar un ingreso de práctica con su código y una salida rápida antes de finalizar.

*Notas:* Clave para la apropiación segura de la app (HU-08). La redacción de los criterios cambió en v0.3.4 (decisiones D-1 a D-3, abajo; D-1 ya resuelta); la original decía «ingreso, salida rápida y qué ve un tercero» y exigía «al menos un ingreso de práctica y una salida rápida antes de finalizar».

**v0.3.4 — Cómo usar Aurora.** El nombre visible es «Cómo usar Aurora», porque «Guía de Aurora» se confundía con la pestaña «Guía» de la barra inferior (2026-10-28, decisión de la autora); en estas notas «la guía» la abrevia. El tutorial (v0.1.0) y el recorrido guiado por Inicio (v0.3.3) se fundieron en una sola experiencia, porque la autora comprobó en el emulador que eran dos ayudas con dos entradas en Ajustes y nadie sabía la diferencia. Consta de la portada «Conoce Aurora» («Empezar» / «Ahora no»), ocho pasos y la pantalla «¡Listo!». Los ocho pasos van numerados de seguido: 1 Entrar, 2 Guía, 3 Mi registro, 4 Reconocer tu situación, 5 Resultados guardados, 6 Acciones rápidas, 7 Ajustes y 8 Salir. **Hay una sola numeración (2026-10-29, decisión de la autora):** la etiqueta de cada tarjeta es solo «Paso N de 8», con el total tomado del número de pasos, y el título dice qué es («Escribe tu código de acceso», «Guía», «Mi registro»… «Toca la nube»). TalkBack anuncia la etiqueta y el título. Las tres partes de abajo (Entrar, Tu espacio, Salir) agrupan los pasos y se nombran en la portada sin números, para que no compitan con los de los pasos. La portada dice «8 pasos cortos. Unos 2 minutos.».

- *Parte 1, Entrar (paso 1):* bajo la tarjeta («Escribe tu código de acceso») hay una réplica de la pantalla de clima dentro de un marco con esquinas redondeadas: el cielo, la barra de búsqueda con «Buscar ciudad…» y, debajo, ciudad, temperatura y condición con datos fijos de ejemplo («Bucaramanga», «24°», «Parcialmente nublado»), sin red. Usa las mismas piezas que la fachada (`core/ui/v2/FacadeSky.kt`), en su voz y sin nada del interior, y no añade textos de instrucción; un círculo y una flecha con rizo dibujados a mano señalan su barra. La réplica es solo visual: no comparte estado con la barra real ni abre nada. La práctica usa la misma puerta que la fachada (`ValidateSecretCodeUseCase.inspect`, con el hash fuera del hilo principal) y valida 400 ms después de la última tecla, como la barra real, y también **al pulsar «Buscar» en el teclado, en el acto**. Si coincide, la barra muestra el éxito (un contorno y una marca de logrado, que son de la guía y solo existen en la réplica), la frase de la tarjeta pasa a la confirmación («Así se entra a Aurora: escribiendo tu código de acceso en el buscador del clima», anunciada por TalkBack), la pantalla vuelve arriba y a los ~1,5 s la guía pasa sola a Inicio. Si no coincide, justo debajo de la barra aparece «Ese no es tu código de acceso. Inténtalo de nuevo.» en una pastilla blanca, que TalkBack anuncia, se trae a la vista también con el teclado abierto y desaparece al volver a escribir. Con menos de seis caracteres la pausa no evalúa, pero «Buscar» sí avisa; con la barra vacía «Buscar» no hace nada. «Siguiente» sigue habilitado siempre (D-2). Lo tecleado se borra al dejar la parte 1 (CP-06).
- *Parte 2, Tu espacio (pasos 2 a 7):* seis elementos de Inicio, en este orden: Guía, Mi registro, Reconocer tu situación, Resultados guardados, Acciones rápidas y Ajustes. El hueco que ilumina se desliza de uno a otro y, si el elemento no se ve entero, la capa lo desplaza hasta verlo. La tarjeta nunca tapa lo iluminado.
- *Parte 3, Salir (paso 8):* la acción es tocar la nube (la salida rápida de siempre; la guía no la intercepta). La tarjeta «Toca la nube» dice «Al tocar la nube, regresas al clima al instante. Para entrar otra vez, escribe tu código de acceso en el buscador.» (2026-10-30: la anterior, «Vuelves al clima al instante. Escribe tu código de acceso para terminar.», no se entendía). Se ofrece «Después» y «Saltar».
- *«¡Listo!»:* «Saliste y volviste. Así de fácil.» tras salir de verdad con la nube y volver a entrar; con «Saltar» o «Después» aparece con «Para recordar:» (el código de acceso en el buscador, la nube, «Ajustes › Cómo usar Aurora» y «Discreta: Si alguien escribe un código de acceso equivocado, solo verá el clima.»).
- *Controles:* en cada tarjeta, «Atrás» (salvo en la primera), «Siguiente» (salvo en el paso 8) y «Saltar». El gesto atrás equivale a «Atrás»; en «Conoce Aurora», a «Ahora no», y en «¡Listo!», a «Ir a mi espacio». La app de abajo queda sin toques y fuera de TalkBack mientras la guía está a la vista.
- *Marca y reanudación:* al crear el código de acceso se deja una marca sellada con la DEK de la cuenta en el archivo de preferencias `view_meta`, con dos valores: «guía desde el inicio» y «guía por cerrar». La primera se consume antes de abrir la guía. La segunda se arma al llegar a la nube y se desarma si se deja ese paso sin salir; en la siguiente entrada la guía muestra «¡Listo!» una vez. Como la nube también cierra la sesión de la cuenta y sin sesión no hay DEK, la guía espera hasta 10 s a la llave antes de mostrar «¡Listo!»; si el plazo vence (sin red), la marca queda para la siguiente entrada. Sin DEK o con el respaldo reiniciado la guía solo no aparece. La marca muere con la eliminación de la cuenta (RF-20) y con el descarte de una activación sin terminar.
- *Saltable:* «Ahora no» lleva a Inicio sin guía y «Saltar» a «¡Listo!». Aparece por sí sola a lo sumo una vez.
- *Estilo:* las ocho tarjetas, que aparecen sobre pantallas de la app (la réplica del clima e Inicio), tienen el estilo propio de la guía para distinguirla de la app: tarjeta y botón lavanda, título y etiqueta en Patrick Hand (OFL, `docs/licencias/`), círculo y flecha a mano. «Conoce Aurora» y «¡Listo!» no se mezclan con ninguna pantalla de la app y **se ven como el interior** (2026-10-29, decisión de la autora): fondo del interior, título en Marcellus, textos en Plex Sans con los colores de texto del interior, el botón lleno y el botón de texto del interior, y las listas en una tarjeta blanca con filas de icono. Patrick Hand queda solo en las tarjetas. La fachada de clima no se toca. Con «Quitar animaciones» los trazos quedan completos y quietos. Con letra 2.0 la tarjeta se desplaza y los botones quedan al pie; «Conoce Aurora» y «¡Listo!» desplazan su contenido y sus botones quedan fijos abajo, a la vista; ningún botón queda bajo la nube (RF-16).

**Decisiones de v0.3.4 respecto de los criterios originales** (señaladas por la verificadora):

- **D-1 (resuelta el 2026-10-29).** El tutorial de v0.3.3 decía que un código inexacto «solo verás una búsqueda más» y que a los cinco minutos sin uso, o al pasar a otra app, Aurora regresa sola al clima; la primera versión de la guía no conservó ninguno de los dos mensajes. Por decisión de la autora se recuperaron, en frases cortas: en el paso 8 («Toca la nube»), «Si la dejas quieta, también vuelve sola.», y en «¡Listo!» el recordatorio «Discreta»: «Si alguien escribe un código de acceso equivocado, solo verá el clima.» (`access_walk_cloud_idle`, `access_walk_part_discreet`, `access_walk_recall_discreet`). Con letra grande, «Salir» y «¡Listo!» siguen desplazándose y sus botones quedan a la vista (`WalkthroughCloudTest`). El criterio 1 sigue redactado sin «qué ve un tercero» como pantalla propia. **Cambio del 2026-10-30:** la frase pequeña del paso 8 se retiró (`access_walk_cloud_idle`) y el mensaje de la inactividad y del paso a otra app pasó, completo, a «Seguridad en Aurora» (fila «Si dejas de usarla», ver RF-11); «Discreta» sigue en «¡Listo!».
- **D-2.** «Siguiente» está disponible en la parte 1 sin haber escrito el código (decisión de la autora): la práctica es posible, no obligatoria. Por eso el criterio 2 pasa de «realiza al menos» a «puede realizar». Queda por ratificar si ese cambio es aceptable para el criterio.
- **D-3.** El progreso se muestra como «Paso N de 8» en la etiqueta de cada tarjeta; no hay barra de progreso gráfica. (Hasta el 2026-10-29 la etiqueta tenía dos numeraciones, «2/3 · Tu espacio · 1 de 6», que la autora encontró confusas.) Queda por ratificar si hace falta una barra.

D-2 y D-3 quedan para que la directora las ratifique junto con el resto de la v0.3.4 (P-141).

**2026-10-29 — Parte 1 sobre réplica de la fachada.** Al probar la v0.3.4 la autora encontró la parte 1 ambigua: la barra suelta sobre el lavanda no se parecía a la pantalla de clima, «Aún no coincide» se veía poco y «Buscar» solo cerraba el teclado, así que no quedaba claro si el código se había verificado. Se cambió a la réplica descrita arriba, con el aviso nuevo y la validación al pulsar «Buscar». D-2 no cambia.

*Historial.* **2026-08, decisión de la usuaria:** el tutorial pasó a ser saltable («Saltar por ahora», en sus dos flujos de aparición: tras crear el código en cuenta nueva y tras restaurar en dispositivo nuevo por RF-39). **v0.3.2:** el tutorial llevó flecha de volver. **v0.3.3:** la práctica resaltó la barra con un halo, y un recorrido de siete pasos por Inicio reemplazó al aviso de bienvenida de v0.3.2. Todo eso quedó sustituido en v0.3.4 por Cómo usar Aurora.

## RF-11 — Reapertura de Cómo usar Aurora
**Tipo:** Funcional · **Prioridad:** Baja · **Dependencias:** RF-10

El sistema debe permitir reabrir Cómo usar Aurora desde Ajustes dentro del doble fondo.

**Criterios de aceptación:**
1. Cómo usar Aurora puede consultarse nuevamente desde Ajustes.

*Notas:* **v0.3.4:** Ajustes tiene una sola fila, «Ver cómo usar Aurora», que reemplaza a las dos de v0.3.3 («Ver el tutorial de nuevo» y «Ver el recorrido por la app»). Deja una bandera neutra en la entrada de Inicio y la guía arranca en «Conoce Aurora»; funciona aunque ya se hubiera visto, saltado o interrumpido, y si se pide mientras la guía espera la llave de su marca, cancela esa espera. La pantalla y todo texto visible se llaman «Ajustes» (`TerminologyGuardTest`). El paso de la fila de Ajustes a la guía lo cubre `SettingsWalkthroughRowTest`; la corrida de punta a punta va a P-139 (CP-06).

**v0.3.4 (2026-10-30) — «Seguridad en Aurora».** Pedido de la autora: en Ajustes faltaba un lugar que explicara, corto y claro, cómo protege Aurora a la usuaria. En el grupo «Tu acceso», debajo de «Ver cómo usar Aurora», la fila «Seguridad en Aurora» abre una pantalla de solo lectura con el estilo de «Qué guardamos y por qué»: diez filas con icono (La nube, Para entrar, Un código de acceso equivocado, Si dejas de usarla, Capturas de pantalla, Reconocer tu situación, En este teléfono, En tu cuenta, Tus archivos, Si eliminas tu cuenta) y la línea «Más detalle en «Qué guardamos y por qué».». Cada frase se comprobó contra el código antes de escribirla; tres se corrigieron con la autora para que fueran exactas: la espera de hasta 5 minutos al salir a tomar una foto o elegir un archivo (RF-33), que la copia de la cuenta se abre con la contraseña de la cuenta (la llave de los datos viaja envuelta con ella para recuperar en otro teléfono, RF-39) y que algunos celulares guardan su propia copia de lo que se toma con la cámara. En los textos «nube» es solo el botón de salida; el almacenamiento es «tu cuenta». La pantalla vive en el grafo seguro (`FLAG_SECURE`, nube con un toque) y no aparece en la fachada. Pruebas: `SafetyNotesTextTest` (los «5 minutos» atados a `SessionManager`) y `SafetyNotesScreenTest` (la fila la abre, el gesto atrás vuelve a Ajustes, la nube sale con un toque, y con letra 2.0 todo se alcanza desplazando y la última línea queda sobre la nube).

## RF-12 — Acceso por código secreto
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-08

El sistema debe abrir el acceso a Aurora cuando el texto de la barra de búsqueda coincida exactamente con el código secreto de la usuaria.

**Criterios de aceptación:**
1. El código exacto abre Aurora (inicio de sesión, o pantalla principal si hay sesión activa).
2. Cualquier variación del código se trata como búsqueda normal.

*Notas:* Puerta única de entrada al doble fondo. **Refinamiento 2026-08-15 (P-31 opción C, acta §2):** el criterio 1 se amplía — el código correcto abre la pantalla principal también cuando existe la credencial local cifrada (reingreso silencioso): el espacio interno se abre SIEMPRE con los datos locales y la sesión de la cuenta se confirma en segundo plano; el inicio de sesión manual solo aparece sin credencial local o si la confirmación es rechazada. Ver ADR 2026-08-19 en `docs/ARQUITECTURA.md`. **v0.3.2 (evaluación heurística):** al abrir con el código de acceso se muestra unos 700 ms la marca de Aurora antes del Inicio. Esa transición ya está dentro del grafo seguro, así que el bloqueo de capturas y la nube rigen desde su primer cuadro. Solo la apertura por código la muestra: un código que no coincide y la activación no la tocan. Con «Quitar animaciones» del sistema la marca se muestra quieta durante el mismo tiempo (CP-07). **v0.3.4:** al entrar desde la fachada el teclado se cierra antes de abrir el espacio privado (`leaveFacade`): se quita el foco, se cierra el teclado y se espera hasta 500 ms a que baje; el tope existe porque en API 26 el alto del teclado puede ser espurio (P-138). Si la app pasa a segundo plano durante esa espera, la entrada se pierde en lugar de abrirse (el colector solo vive con la app al frente y se comprueba justo antes de navegar), de modo que quien vuelve desde recientes no encuentra el interior sin teclear el código (RF-06). El cálculo del hash del código (HMAC con la llave no exportable del Keystore) corre fuera del hilo principal, en un carril de un solo hilo; la puerta tiene el plazo de la red y, vencido, cuenta como búsqueda sin resultados. La comparación sigue siendo de tiempo constante.

## RF-13 — Respuesta indistinguible de búsqueda
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-02, RF-12

El sistema debe responder a entradas que no sean ciudad ni código con el mensaje estándar de búsqueda sin resultados, sin diferencia observable de comportamiento.

**Criterios de aceptación:**
1. El nombre de una ciudad muestra su clima con comportamiento normal.
2. Un texto que no es ciudad ni código responde 'No se encontraron resultados', idéntico a cualquier búsqueda fallida.

*Notas:* Evita que el doble fondo sea detectable por ensayo y error. **v0.3.2 (H-1):** el indicador de carga arranca con la tecla y depende solo de la longitud del texto. Un código erróneo recorre los mismos estados y tiempos que una ciudad (carga, resultados, sin resultados o error), y un código válido abre aunque la búsqueda falle o expire. **v0.3.4:** el hash del código ya no corre en el hilo principal. La práctica de la guía y la búsqueda de la fachada esperan una pausa de 400 ms tras la última tecla (en la práctica, «Buscar» evalúa en el acto); todo texto de 6 o más caracteres hace un único hash, sea código o no, y las pruebas comparan la línea de tiempo de estados visibles de un código erróneo con la de una ciudad inexistente (`WeatherViewModelTest`).

## RF-14 — Limpieza de la barra de búsqueda
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-12

El sistema debe limpiar la barra de búsqueda al salir del doble fondo y no registrar el código en historial ni autocompletado.

**Criterios de aceptación:**
1. Al regresar al clima la barra está vacía.
2. El código no aparece en historial ni en sugerencias de autocompletado.

*Notas:* **Refinamiento 2026-10-05 (cierre de P-14):** el criterio 2 cubre también la miniatura de recientes — mientras hay texto en la barra la miniatura se oculta (`RecentsThumbnailGuard` en core/security, `setRecentsScreenshotEnabled`, API 33+; el sistema muestra una tarjeta neutra) y se restaura al vaciarla o salir del modo búsqueda; con la búsqueda inactiva la fachada se fotografía normal (no es distinguidor, RF-13/RNF-05). En API < 33 solo queda la limpieza en ON_PAUSE (mejor esfuerzo). ADR 2026-10-05.

## RF-15 — Inicio de sesión
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-07

El sistema debe permitir iniciar sesión con correo y contraseña, mostrando errores genéricos y aplicando bloqueo temporal tras 5 intentos fallidos.

**Criterios de aceptación:**
1. Credenciales correctas dan acceso a la pantalla principal de Aurora.
2. Credenciales incorrectas muestran un error genérico que no indica cuál campo falló.
3. Tras 5 intentos fallidos consecutivos se aplica un bloqueo temporal.

*Notas:* **Refinamiento 2026-08-15 (P-31 opción C, acta §2):** el inicio de sesión puede ser SILENCIOSO — con la credencial local cifrada, la sesión se confirma en segundo plano sin formulario. El silencioso NO cuenta para el bloqueo del criterio 3 (no es superficie de adivinación; su rechazo destruye la credencial local y fuerza el login manual, que sí cuenta). Los criterios 1–3 aplican intactos al formulario manual. Ver ADR 2026-08-19. **v0.3.2 (hallazgo A):** en el flujo de activación el inicio de sesión pide siempre la contraseña de la cuenta (ver RF-39). El aviso de bloqueo nombra el inicio de sesión: «Por tu seguridad, el inicio de sesión queda en pausa unos minutos.»

## RF-16 — Salida rápida
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-12

El sistema debe ofrecer en todas las pantallas de Aurora un botón de salida rápida que retorne a la pantalla del clima en menos de 1 segundo, sin posibilidad de regresar mediante navegación.

**Criterios de aceptación:**
1. El botón está visible en todas las pantallas del doble fondo.
2. Al tocarlo, en menos de 1 segundo se muestra el clima con la barra vacía.
3. El botón atrás y la navegación no permiten regresar a Aurora ni dejan rastro visible de la sesión.

*Notas:* Mecanismo central de protección en uso (HU-11). **v0.3.2 (evaluación heurística):** con el teclado abierto, la nube queda sobre él y la pantalla se ajusta para que el campo y el botón final sigan alcanzables. Un solo toque sale también con una hoja, un diálogo o el selector de fecha abiertos: cada ventana propia lleva la misma nube (`SecureWindow`, P-122). Las barras inferiores reservan el carril de la nube para que nunca tape un botón (H-4). **v0.3.3:** en Inicio y en la portada de la Guía la zona que se desplaza termina antes de la nube (`reserveCloudLane`), así que ninguna tarjeta ni botón pasa por debajo de ella; el recorrido guiado no la deja encima de «Siguiente» en ninguno de sus pasos; y el selector de fecha lee los mismos insets que la nube, de modo que nunca se cruzan (en API 26 la nube subía sobre «Aceptar» en ~3 % de las aperturas; P-138). Un toque en la nube sigue saliendo al clima en todos los casos. **v0.3.4:** Cómo usar Aurora reserva el carril de la nube en todos sus pasos y en ninguno deja la nube encima de un botón, con letra normal y con letra 2.0 (`WalkthroughCloudTest`). La nube dentro de la guía es la salida rápida de siempre y cancela el cálculo en curso en el mismo cuadro; la eliminación y el cierre de sesión cancelan también los cálculos del código que siguieran en marcha en la práctica, en la creación o en el cambio del código.

## RF-17 — Consentimiento informado
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-05

El sistema debe presentar el consentimiento informado antes de la creación de la cuenta, exigir aceptación explícita y permitir su consulta posterior.

**Criterios de aceptación:**
1. No es posible continuar sin aceptación explícita (sin casillas preseleccionadas).
2. El consentimiento puede releerse desde la sección de privacidad.

*Notas:* Conforme a la Ley 1581 de 2012. **v0.3.2 (evaluación heurística):** el consentimiento va después de la bienvenida (RF-05), y la flecha y el gesto atrás vuelven a ella. La descripción de qué es Aurora pasó del consentimiento a la bienvenida, y el texto de lo que se guarda nombra cada credencial por su nombre único.

## RF-18 — No recolección sin consentimiento
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-17

El sistema no debe recolectar dato alguno si el consentimiento es rechazado.

**Criterios de aceptación:**
1. Al rechazar el consentimiento se regresa al clima.
2. Ningún dato queda almacenado local ni remotamente.

*Notas:* -

## RF-19 — Cierre de sesión
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-15

El sistema debe permitir cerrar sesión, exigiendo credenciales para el siguiente acceso.

**Criterios de aceptación:**
1. Tras cerrar sesión, volver a entrar requiere credenciales.
2. La vista regresa a la pantalla del clima.

*Notas:* -

## RF-20 — Eliminación de cuenta y datos
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-15, RNF-01

El sistema debe permitir eliminar la cuenta con doble confirmación, suprimiendo todos los datos de la usuaria del dispositivo y de la nube.

**Criterios de aceptación:**
1. La eliminación exige confirmación en un segundo paso explícito.
2. Se eliminan cuenta, registros y evidencias del dispositivo y de la nube.
3. Iniciar sesión con esas credenciales ya no es posible.

*Notas:* Derecho de supresión, Ley 1581 de 2012. **v0.3.2 (evaluación heurística):** la confirmación final dice el alcance: «Se borrarán tu cuenta y todos tus datos de este teléfono y de la nube. Para volver a usar Aurora tendrás que instalarla de nuevo.»

## RF-21 — Consulta de tipos de violencia
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-12, RF-24

El sistema debe presentar los tipos de violencia de género con descripción, señales y ejemplos en lenguaje accesible.

**Criterios de aceptación:**
1. Se presentan los tipos física, psicológica, sexual y económica/patrimonial.
2. Cada tipo incluye descripción, señales y ejemplos en lenguaje claro.

*Notas:* Contenido gestionado desde el panel de administración. **Refinamiento 2026-10-03 (cierre P-68, ADR 2026-10-03):** los tipos publicados son también el catálogo con el que la usuaria clasifica sus registros (RF-32): mismo orden, icono y acento, sin versión nueva de la app. Se agregan al contenido «Violencia digital» (t05) y «Acoso sexual» (t06), validados por la directora en octubre de 2026 (fuentes: Resolución 020310 de 2026 del Ministerio de Educación; Ley 2365 de 2024); el criterio 1 sigue exigiendo los cuatro tipos de la Ley 1257 como mínimo.

## RF-22 — Consulta de rutas de atención
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-12, RF-24

El sistema debe presentar las rutas de atención de Santander (Salud–Protección–Justicia) con entidades, funciones y datos de contacto.

**Criterios de aceptación:**
1. Se presenta la triada Salud–Protección–Justicia con sus entidades.
2. Cada entidad muestra qué hace, cuándo acudir y cómo contactarla.

*Notas:* Basado en el mapa de rutas de atención (A1.5).

## RF-23 — Líneas de atención y llamada directa
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-22

El sistema debe mostrar las líneas de atención y permitir iniciar una llamada con un toque mediante el marcador del teléfono, sin dejar registro en la app.

**Criterios de aceptación:**
1. Se muestran las líneas de atención vigentes.
2. Al tocar una línea se abre el marcador con el número listo.
3. La acción no queda registrada dentro de Aurora.

*Notas:* Incluye 123, 155, 141, 122 y línea departamental.

## RF-24 — Actualización de contenido
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-44

El sistema debe actualizar el contenido informativo desde la nube cuando exista una nueva versión publicada.

**Criterios de aceptación:**
1. Con conexión y contenido nuevo publicado, al abrir el módulo se muestra la versión actualizada.

*Notas:* -

## RF-25 — Tolerancia a fallos de actualización
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-24, RF-26

El sistema debe conservar y mostrar la última versión del contenido cuando la actualización no sea posible.

**Criterios de aceptación:**
1. Si la actualización falla, se muestra la última versión almacenada sin errores visibles para la usuaria.

*Notas:* -

## RF-26 — Consulta sin conexión
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-24, RNF-08

El sistema debe permitir la consulta completa del contenido informativo sin conexión a internet, desde almacenamiento local protegido.

**Criterios de aceptación:**
1. Sin conexión, todo el contenido (tipos, rutas, líneas) está disponible.
2. El contenido local se almacena de forma protegida.

*Notas:* -

## RF-27 — Cuestionario orientador
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-12, RF-43

El sistema debe presentar el cuestionario orientador permitiendo avanzar, retroceder y abandonar sin persistir respuestas.

**Criterios de aceptación:**
1. La usuaria puede avanzar, retroceder y abandonar en cualquier momento.
2. Al abandonar no se guarda ninguna respuesta.

*Notas:* Preguntas de elaboración propia, lenguaje no revictimizante validado con la directora. No se utiliza la escala Danger Assessment. **v0.3.2 (evaluación heurística):** salir a medias pregunta antes («¿Salir ahora?»; las respuestas se descartan). La nube y el cierre por inactividad nunca preguntan. En las preguntas, la fila de botones va en el carril de la nube (H-4). **v0.3.3:** en Inicio toda la tarjeta del cuestionario lo abre y «Empezar» va dentro, al pie.

## RF-28 — Descarte de respuestas en salida rápida
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-16, RF-27

El sistema debe descartar las respuestas del cuestionario al activarse la salida rápida.

**Criterios de aceptación:**
1. Al usar la salida rápida durante el cuestionario, las respuestas se descartan por completo.

*Notas:* -

## RF-29 — Cálculo de nivel orientativo
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-27, RF-22

El sistema debe calcular y presentar un nivel orientativo con recomendaciones y accesos a las rutas de atención pertinentes.

**Criterios de aceptación:**
1. Al completar el cuestionario se muestra el nivel orientativo.
2. El resultado incluye recomendaciones y accesos directos a las rutas de atención pertinentes.

*Notas:* Niveles de referencia conceptual: Variable, Moderado, Grave, Extremo (INMLCF).

## RF-30 — Aviso de alcance orientador
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-29

El sistema debe mostrar en todo resultado, de forma visible sin desplazamiento, el aviso de que se trata de una orientación preliminar que no sustituye la evaluación profesional.

**Criterios de aceptación:**
1. El aviso es visible en todo resultado sin necesidad de desplazamiento.

*Notas:* Principio de no revictimización y no sustitución de atención profesional.

## RF-31 — Guardado opcional del resultado
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-29, RNF-01

El sistema debe almacenar el resultado del cuestionario únicamente cuando la usuaria lo confirme expresamente.

**Criterios de aceptación:**
1. Descartar o salir no deja registro alguno.
2. Guardar exige confirmación expresa y el resultado queda protegido y consultable.

*Notas:* La usuaria mantiene control total sobre su información. **Implementado (2026-08-14, rama mejoras-v3; ADR y acta §3.2):** botón «Guardar este resultado» con hoja segura de confirmación (criterio 2); se persiste solo fecha + nivel + tipos reconocidos (RNF-04) en el almacén cifrado `guidance_store.db`, viaja sellado con la DEK de RF-39 (`/users/{uid}/outcomes`) y se restaura multi-dispositivo; listado consultable en Inicio con detalle y eliminación individual (doble confirmación, propagación remota como RF-36) y cascada RF-20. Descartar/salida rápida no dejan registro (criterio 1, regresión RF-28 probada). **v0.3.2 (evaluación heurística):** salir del resultado sin guardarlo pregunta antes. «Guardar» pasó a ser la acción principal y «Terminar» la secundaria, una sobre otra y alineadas con la nube (H-4).

## RF-32 — Registro de incidentes
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-15, RNF-01

El sistema debe permitir registrar incidentes con fecha, tipo de violencia y descripción, validando los campos obligatorios sin pérdida de lo diligenciado.

**Criterios de aceptación:**
1. Con los campos obligatorios completos, el incidente se guarda protegido.
2. Si falta un campo obligatorio, se indica sin perder lo ya escrito.

*Notas:* **Refinamiento 2026-10-03 (cierre P-68, ADR 2026-10-03):** el «tipo de violencia» se elige entre los tipos publicados en la Guía (RF-21), que son la única fuente — mismo orden, icono y acento; un tipo nuevo o editado desde el panel aparece en el registro en la siguiente actualización de contenido. Si el catálogo no está disponible (sin conexión y sin caché de contenido), registrar sigue siendo posible con la opción neutra «Sin tipo», y el tipo se elige después al editar (RF-35); el criterio 2 se mantiene: sin tipo elegido no se guarda. Un registro cuyo tipo dejó de publicarse se muestra con su último nombre conocido (o «Sin tipo») y el visual neutro, nunca se pierde. Los registros anteriores migran su tipo al id del tema equivalente (local, nube y restauración en otro dispositivo, RF-37/RF-39); su instantánea de nombre nace vacía, así que sin catálogo disponible se ven como «Sin tipo» hasta que haya contenido o se editen. **v0.3.2 (evaluación heurística):** salir del editor con cambios sin guardar pregunta antes («¿Salir sin guardar?»); sin cambios, sale directo. La flecha y el gesto atrás se comportan igual.

## RF-33 — Evidencias adjuntas
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-32

El sistema debe permitir adjuntar imágenes a un incidente desde galería o cámara, almacenadas protegidas y visibles solo dentro de Aurora.

**Criterios de aceptación:**
1. Se pueden adjuntar imágenes desde galería o cámara.
2. Las evidencias quedan protegidas, asociadas al incidente y visibles solo dentro de Aurora.

*Notas:* Nota provisional (política de evidencias aprobada por la directora el 2026-07-21, `docs/DECISIONES_DIRECTORA.md` §5; pendiente reflejar en el xlsx fuente): el alcance se AMPLÍA a tres vías sin permisos — imágenes de galería, foto con la cámara DEL SISTEMA con salida directa al almacén cifrado (sin pasar por la galería) y archivos existentes (PDF, documentos, audio) —; se EXCLUYE la grabación de audio propia (permiso de micrófono delator). Al importar imágenes se eliminan los metadatos (GPS incluido). Las evidencias son SOLO LOCALES (sin sincronización) en esta etapa (§5.3). Ver ADR 2026-07-24 en `docs/ARQUITECTURA.md`. **v0.3.2 (evaluación heurística):** (H-5) antes de abrir el selector de Galería o de Archivo, la primera vez en cada visita al editor, una hoja explica cómo volver sin elegir nada; cancelar el selector deja a la persona en el editor con lo escrito. (H-6) Cada adjunto se muestra con su ícono y un nombre genérico («Imagen 1 · 12 oct»), nunca con el nombre del archivo original. **Cierre de v0.3.2:** en Android 8.0 y 8.1 el Keystore truncaba sin error el cifrado cuando una sola llamada recibía más de unos 128 KB. La app no se veía afectada, porque escribía en trozos de 8 KB o menos. Aun así, el cifrador entrega ahora al Keystore trozos de 32 KB, y el almacén abre cada adjunto completo después de sellarlo: si no coincide con lo escrito, no se guarda y el editor muestra «No se pudo guardar el adjunto. Inténtalo de nuevo.». El formato de los archivos no cambió (CP-21).

## RF-34 — Listado cronológico de incidentes
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-32

El sistema debe listar los incidentes en orden cronológico inverso mostrando fecha y tipo.

**Criterios de aceptación:**
1. Los incidentes se listan del más reciente al más antiguo.
2. Cada elemento muestra fecha y tipo de violencia.

*Notas:* -

## RF-35 — Edición de incidentes
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-32

El sistema debe permitir editar los incidentes persistiendo los cambios de forma protegida.

**Criterios de aceptación:**
1. Los cambios de un incidente editado se guardan protegidos y se reflejan en la sincronización.

*Notas:* **v0.3.2 (evaluación heurística):** igual que al crear (RF-32), salir de la edición con cambios sin guardar pregunta antes.

## RF-36 — Eliminación de incidentes
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-32, RF-37

El sistema debe permitir eliminar incidentes con confirmación en dos pasos, suprimiendo también sus evidencias del dispositivo y de la nube.

**Criterios de aceptación:**
1. La eliminación exige confirmación en un segundo paso.
2. El incidente y sus evidencias desaparecen del dispositivo y de la nube.

*Notas:* -

## RF-37 — Sincronización cifrada
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-32, RNF-01

El sistema debe sincronizar los registros cifrados con la nube por canal seguro, con acceso restringido exclusivamente a la cuenta de la usuaria.

**Criterios de aceptación:**
1. Los cambios (crear, editar, eliminar) se sincronizan por canal cifrado.
2. Solo la cuenta de la usuaria puede acceder a sus registros.

*Notas:* **v0.3.2 (hallazgo A):** ninguna subida a la nube ocurre mientras no exista código de acceso en el dispositivo, desde ningún punto (crear, editar, guardar o reconciliar). Lo pendiente sube en la primera reconciliación con código.

## RF-38 — Sincronización diferida sin conexión
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-37

El sistema debe encolar los cambios realizados sin conexión y sincronizarlos al recuperar la red.

**Criterios de aceptación:**
1. Los cambios sin conexión quedan en espera.
2. Al recuperar la red se sincronizan automáticamente.

*Notas:* -

## RF-39 — Recuperación multi-dispositivo
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-15, RF-37

El sistema debe restaurar íntegramente los registros y evidencias de la usuaria al iniciar sesión en otro dispositivo.

**Criterios de aceptación:**
1. Al iniciar sesión en un dispositivo nuevo y finalizar la sincronización, se ven todos los incidentes y evidencias íntegros.

*Notas:* **Refinamiento v0.3.2 (evaluación heurística, hallazgo A):** mientras no exista código de acceso en el dispositivo (flujo de activación), una sesión viva del proveedor o unas credenciales guardadas **no** abren el paso: vienen de un registro interrumpido y se descartan. El descarte corre al abrir el consentimiento, antes de crear una cuenta y al abrir el inicio de sesión de activación, y nunca corre si el código de acceso ya existe. Retomar el registro sigue siendo posible, pero escribiendo otra vez la contraseña de la cuenta (con el mismo bloqueo temporal de RF-15). El reingreso con código de acceso ya creado (P-31 opción C) no cambia. **Hallazgo A′:** el mismo descarte borra los datos locales que una cuenta restauró antes de crear el código (se conservan el código de activación, la pausa temporal y la caché de la nube). Con la contraseña correcta, este criterio vuelve a restaurarlos desde la nube. Mientras no exista el código de acceso, la restauración baja pero no sube nada: lo pendiente sube en la primera reconciliación con código.

## RF-40 — Autenticación de administradora
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RNF-06

El panel web debe autenticar a la administradora y denegar el acceso a cuentas sin rol administrativo.

**Criterios de aceptación:**
1. Credenciales válidas con rol de administradora dan acceso al panel.
2. Cuentas sin ese rol reciben acceso denegado.

*Notas:* -

## RF-41 — Gestión de contenido informativo
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-40

El panel web debe permitir crear, actualizar y eliminar el contenido informativo sobre tipos de violencia.

**Criterios de aceptación:**
1. El contenido editado y publicado queda disponible para la app.

*Notas:* -

## RF-42 — Gestión de rutas, entidades y líneas
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-40

El panel web debe permitir gestionar rutas de atención, entidades y líneas de contacto, preservando la integridad de las rutas al eliminar entidades.

**Criterios de aceptación:**
1. Los cambios publicados (por ejemplo, un teléfono actualizado) se reflejan en la app tras la actualización.
2. Eliminar una entidad no rompe las rutas que la referenciaban.

*Notas:* -

## RF-43 — Gestión del cuestionario
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-40

El panel web debe permitir gestionar preguntas, opciones y niveles del cuestionario, impidiendo publicar configuraciones incompletas.

**Criterios de aceptación:**
1. Las modificaciones publicadas rigen el cuestionario que responden las usuarias.
2. Una configuración sin preguntas o sin niveles no puede publicarse.

*Notas:* -

## RF-44 — Publicación reflejada en la app
**Tipo:** Funcional · **Prioridad:** Alta · **Dependencias:** RF-41, RF-42, RF-43

Los cambios publicados desde el panel deben reflejarse en la app tras la siguiente actualización de contenido.

**Criterios de aceptación:**
1. Tras publicar, la siguiente sincronización de la app muestra la versión vigente del contenido o del cuestionario.

*Notas:* Cierra el ciclo de gestión de contenido panel → app.

## RF-45 — Exportación selectiva de registros
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-32, RF-33, RF-34

El sistema debe permitir a la usuaria seleccionar uno o varios registros de incidentes y exportarlos en un único archivo PDF protegido con contraseña, con la opción de incluir o no sus adjuntos (imágenes, documentos y audios), sin exportar el historial completo por defecto.

**Criterios de aceptación:**
1. Desde «Mi registro» la usuaria puede marcar uno o varios registros; ninguno viene marcado por defecto.
2. Antes de exportar se muestra un resumen de lo seleccionado y un aviso de que el archivo quedará fuera del espacio protegido.
3. Por cada registro, el PDF contiene fecha del hecho, tipo y relato, y sus adjuntos solo si la usuaria eligió incluirlos: las imágenes y los documentos PDF como páginas del mismo archivo y los audios como archivos incrustados en él.
4. El PDF se protege con una contraseña que la usuaria define al exportar y tiene un nombre neutro, sin términos que delaten su contenido.
5. La usuaria elige guardar o compartir el archivo mediante el selector del sistema, sin que la app pida permisos nuevos ni conserve copias temporales.
6. Las pantallas de selección y exportación bloquean capturas de pantalla.

*Notas:* Surge de la consulta con el comité de género: los casos se demoran por falta de fechas y pruebas. El archivo exportado sale del cifrado de Aurora; se mitiga con contraseña, nombre neutro y aviso previo (HU-35). Agregado el 2026-10-02; el 2026-10-03 la usuaria amplió el criterio 3 de «imágenes» a todos los adjuntos tras la revisión en emulador: un documento adjunto que solo se menciona no sirve como prueba. **v0.3.2 (evaluación heurística):** la contraseña se llama «contraseña del archivo PDF» en todos los textos, y una frase aclara que solo sirve para abrir ese archivo y que no es la contraseña de la cuenta ni el código de acceso. La regla de longitud (mínimo 10 caracteres) se marca en vivo.

## RF-46 — Recuperación de la contraseña por correo
**Tipo:** Funcional · **Prioridad:** Media · **Dependencias:** RF-15, RF-39

El sistema debe permitir a la usuaria pedir, desde la pantalla de inicio de sesión, un correo para restablecer su contraseña, sin revelar si la dirección tiene una cuenta y advirtiendo antes de enviarlo que el respaldo en la nube podría no recuperarse en otro teléfono.

**Criterios de aceptación:**
1. La pantalla de inicio de sesión ofrece un enlace discreto para recuperar el acceso, que abre un formulario con un solo campo de correo.
2. Antes de enviar se muestra un aviso de que, al restablecer la contraseña, la información guardada en la nube podría no abrirse en otro teléfono, y de que lo guardado en ese dispositivo no se pierde.
3. El mensaje de confirmación es idéntico exista o no una cuenta con ese correo, y también cuando el correo está mal formado. Solo la falta de conexión se informa aparte.
4. Tras varios envíos seguidos se aplica una pausa temporal con el mismo límite del inicio de sesión, con su propio contador: agotarla no impide iniciar sesión.
5. Ningún texto nuevo delata el propósito de la aplicación.

*Notas:* Refinamiento de RF-15. Hasta ahora la contraseña solo podía restablecerse desde la consola del proveedor. La decisión de conservar la recuperación por correo con plantilla de identidad meteorológica es la opción A de la reunión con la directora del 2026-07-21 (`docs/DECISIONES_DIRECTORA.md` §4.2, P-19); el texto de esa plantilla debe personalizarse en la consola ANTES de probar el flujo. El criterio 3 es el mismo de RF-15 criterio 2: la pantalla no puede servir para averiguar quién tiene cuenta. El aviso del criterio 2 es coherente con el ADR 2026-07-26: en un dispositivo sin copia local de la llave, una contraseña restablecida deja el respaldo ilegible y se reinicia. Agregado el 2026-10-06.

## RNF-01 — Cifrado de datos
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** -

Los datos sensibles deben almacenarse cifrados en el dispositivo y transmitirse por canal cifrado, conforme a ISO/IEC 27001 y Ley 1581 de 2012.

**Criterios de aceptación:**
1. El almacenamiento local es ilegible sin la llave de cifrado.
2. El tráfico de red no expone datos en claro.

*Notas:* Verificación por inspección de almacenamiento y tráfico.

## RNF-02 — Bloqueo de capturas y recientes
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** -

Las pantallas de Aurora deben bloquear capturas de pantalla y ocultar su contenido en la vista de aplicaciones recientes.

**Criterios de aceptación:**
1. El intento de captura de pantalla falla en el doble fondo.
2. La vista de recientes no muestra contenido de Aurora.

*Notas:* Protección frente a acceso físico del agresor al dispositivo.

## RNF-03 — Cierre automático de sesión
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** RF-16

La sesión debe cerrarse automáticamente tras 5 minutos de inactividad, retornando a la pantalla del clima.

**Criterios de aceptación:**
1. A los 5 minutos de inactividad la sesión se cierra y se muestra el clima.

*Notas:* Verificación con prueba cronometrada.

## RNF-04 — Minimización de datos
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** -

Solo deben recolectarse los datos definidos en el inventario aprobado (correo, código protegido, incidentes y evidencias, resultados que la usuaria decida guardar).

**Criterios de aceptación:**
1. La auditoría del almacenamiento local y en nube no encuentra datos fuera del inventario aprobado.

*Notas:* Principio de minimización, Ley 1581 de 2012. Inventario de datos como anexo. *Ampliación 2026-10-05 (P-31 opción C, P-70a):* el inventario LOCAL incluye además las credenciales de la cuenta (correo y contraseña) cifradas en reposo bajo `account_meta` para el reingreso silencioso; no se recolecta ningún dato nuevo de la usuaria, solo se conserva cifrado uno que ya aportaba en cada ingreso.

## RNF-05 — Camuflaje integral
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** RF-01, RF-06

Ningún elemento fuera del doble fondo debe revelar el propósito real: notificaciones, nombre en recientes, permisos solicitados, identificador de la app, textos de accesibilidad.

**Criterios de aceptación:**
1. La lista de verificación de camuflaje se cumple ítem por ítem sin fallos.

*Notas:* Incluye notificaciones, recientes, permisos, identificador y accesibilidad.

## RNF-06 — Separación administración / datos de usuarias
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** -

El contenido administrable y los datos de las usuarias deben estar separados de modo que ningún rol administrativo pueda leer datos personales.

**Criterios de aceptación:**
1. Una petición administrativa a datos de usuarias es rechazada por las reglas de acceso.

*Notas:* Refuerza la confianza: ni la administradora puede ver registros personales.

## RNF-07 — Rendimiento general
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** -

La app debe iniciar en 3 segundos o menos y responder a las interacciones locales (navegación, apertura de contenido, guardado local) en 2 segundos o menos.

**Criterios de aceptación:**
1. El arranque tarda ≤ 3 segundos en un dispositivo de gama media.
2. Las interacciones locales responden en ≤ 2 segundos.

*Notas:* Medición instrumentada sobre dispositivo de gama media.

## RNF-08 — Rendimiento y disponibilidad sin conexión
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** RF-26

El módulo de información debe ser 100 % operativo sin conexión, con carga del contenido local en 2 segundos o menos.

**Criterios de aceptación:**
1. En modo avión el módulo de información es completamente operativo.
2. El contenido local carga en ≤ 2 segundos.

*Notas:* Prueba en modo avión con medición de tiempos.

## RNF-09 — Usabilidad
**Tipo:** No Funcional · **Prioridad:** Alta · **Dependencias:** -

Las interfaces deben cumplir las heurísticas de Nielsen y los principios de ISO 9241-210.

**Criterios de aceptación:**
1. La evaluación heurística no deja hallazgos de severidad ≥ 3 sin resolver.

*Notas:* Se evalúa sobre prototipos (Fase 2) y sobre la app (Fase 4). **v0.3.3 (tercer evaluador heurístico):** con letra grande las tres Acciones rápidas de Inicio se apilan a lo ancho en lugar de reducir la letra (WCAG 1.4.4): van en fila si cada etiqueta cabe entera, sin partir palabras y con la letra elegida, y si no, una debajo de otra. Las etiquetas de las pestañas se mantienen en una línea, con la letra elegida si cabe y reducida solo lo necesario si no, comprobando cada tamaño midiendo (`QuickAccessLabelTest`, `QuickAccessRowHeightTest`, `InnerTabBarLabelTest`, `TextFitTest`, `TabLabelFitTest`).

## RNF-10 — Portabilidad
**Tipo:** No Funcional · **Prioridad:** Media · **Dependencias:** -

La app debe operar en la versión mínima de Android que se defina en la fase de diseño (A2.1), garantizando amplia cobertura de dispositivos.

**Criterios de aceptación:**
1. La app se ejecuta correctamente en la versión mínima definida y en un dispositivo reciente.

*Notas:* La versión mínima se fija en la actividad A2.1.

## RNF-11 — Mantenibilidad
**Tipo:** No Funcional · **Prioridad:** Media · **Dependencias:** -

El software debe seguir una arquitectura por capas con componentes documentados, para soportar el manual técnico y la evolución futura.

**Criterios de aceptación:**
1. La estructura del repositorio evidencia la arquitectura por capas.
2. El muestreo de componentes muestra documentación adecuada.

*Notas:* Soporta el manual técnico (entregable de Fase 3).

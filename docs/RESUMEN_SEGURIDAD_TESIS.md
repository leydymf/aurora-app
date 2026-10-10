# Resumen de la evaluación de seguridad (capítulo de Resultados, objetivo 4)

> Una página, en lenguaje llano, para integrar al informe de grado.
> El detalle técnico está en `docs/INFORME_SEGURIDAD_OWASP.md` y las evidencias
> en `docs/evidencias-owasp/`.

## Qué se evaluó y con qué

La seguridad de Aurora se evaluó con el marco **OWASP MAS**, que es el nombre
actual de la guía que el anteproyecto cita como *Mobile Security Testing Guide*.
El marco tiene dos piezas: el estándar **MASVS**, que define los controles que
una aplicación móvil debe cumplir, y la guía **MASTG**, que dice cómo probar cada
uno. Se usaron las versiones vigentes al momento de la prueba: **MASVS 2.1.0** y
**MASTG 2.0.0**, esta última publicada el 30 de junio de 2026.

La prueba se hizo sobre la versión **v0.3.0-trl4**, la misma que cierra el
prototipo, y combinó tres formas de mirar la aplicación. El análisis
**estático**: se descompiló el archivo instalable para leer su manifiesto, su
código y sus recursos tal y como quedan en el teléfono, y se buscaron
contraseñas o llaves escondidas en él. El análisis **dinámico**: se instaló la
aplicación en un emulador con una cuenta de prueba y se inspeccionó lo que
realmente escribe en el teléfono y lo que deja ver en la pantalla mientras se
usa. Y la **reejecución de las pruebas del propio proyecto**: las 791 pruebas
automáticas de lógica y las 45 que necesitan un dispositivo real, todas en
verde. Además se verificaron las reglas de acceso a la base de datos en la nube
levantando un emulador local de Firestore.

De las ocho categorías del estándar se evaluaron siete. La octava, que trata de
defensas contra ingeniería inversa, se dejó fuera de forma deliberada y
justificada: pide detectar teléfonos modificados y estorbar a quien analice la
aplicación, y eso no protege contra la amenaza real de Aurora, que es una persona
agresora con el teléfono en la mano, no un experto con herramientas de análisis.
Añadir esas defensas habría hecho la aplicación más pesada y más llamativa,
justo lo contrario de lo que busca el camuflaje.

**Dos aclaraciones de alcance, para leer bien los resultados.** La inspección de
los archivos internos se hizo sobre la versión de depuración, que es la única
que el emulador disponible permite abrir; el código que guarda y cifra los datos
es idéntico en las dos versiones, y lo único que sí cambia —qué nombres quedan
legibles tras la ofuscación— se midió aparte, directamente sobre el instalable
final. Y la categoría de comunicación en red se cubrió solo por revisión del
código y de la configuración, sin interceptar tráfico real, que era lo acordado
para una cobertura ligera.

## Cuántos controles pasaron

De los **veinte controles evaluados, diecinueve pasaron y uno falló**. Siete
pasaron sin salvedades, nueve pasaron con una observación anotada, tres no
aplican a una aplicación con las características de Aurora —no usa páginas web
embebidas, no tiene servidores propios a los que fijar certificados y no se
distribuye por una tienda donde forzar actualizaciones tenga sentido— y uno,
el que pide evitar la fuga de datos sensibles, no se cumple. Más abajo se
explica por qué y qué lo causa.

Lo más importante es que los pilares del diseño resistieron la comprobación
directa, no solo la revisión del código. Las bases de datos donde viven los
registros y los resultados del cuestionario son, byte a byte, indistinguibles de
ruido aleatorio: se midió su contenido y no aparece **ni una sola palabra**
legible, ni siquiera en los archivos temporales de escritura que suelen quedarse
fuera del cifrado. Las evidencias adjuntas están selladas y ni siquiera conservan
la marca que identifica a una fotografía o a un documento. Las preferencias de la
aplicación solo guardan datos codificados bajo nombres neutros. Los siete
documentos PDF que la aplicación exportó durante las corridas manuales se
midieron uno a uno, y los siete llevan el cifrado más fuerte que admite ese
formato, incluidos los dos que llevan archivos adjuntos dentro. No queda ningún rastro de la sesión abierta en el disco. El sistema
operativo se niega a hacer copia de seguridad de la aplicación cuando se le
pide expresamente, y su propio registro muestra que nunca ha restaurado datos
en ninguna de las veintiuna instalaciones que lleva anotadas. Y en el registro
del sistema, tras ejercitar la aplicación de clima de principio a fin, no
aparece absolutamente nada de lo que se escribió en ella.

El camuflaje de la pantalla también se verificó en vivo y con medida. Con la
protección activa, pedirle al sistema una captura del espacio privado devuelve
una imagen **completamente negra**: cero píxeles visibles de doscientos sesenta y
un mil, incluida la ventana aparte del selector de fecha, que es el caso que más
se escapa en este tipo de aplicaciones. La misma captura sobre la pantalla del
clima devuelve la imagen completa, como debe ser, porque una aplicación de clima
que no se dejara fotografiar llamaría la atención por sí sola. La vista previa
que el teléfono guarda al cambiar de aplicación sale en blanco en los dos casos
críticos: mientras se escribe la clave en la barra de búsqueda, y estando dentro
con los registros en pantalla. El botón de salida rápida devuelve al clima sin
dejar el espacio privado en el historial de navegación. Y dejando el teléfono
quieto, la aplicación volvió sola al clima a los cinco minutos y cuarenta
segundos, cronometrado.

Por último, se ejecutaron por primera vez las pruebas de las reglas de la base de
datos en la nube, que llevaban meses pendientes por falta de herramientas:
pasaron los ocho casos, incluido el más delicado, que confirma que ni siquiera
una cuenta administradora puede leer los datos de una usuaria.

## Qué hallazgos quedaron

Quedaron **siete hallazgos: uno de gravedad alta, dos de gravedad media, tres
bajos y uno que la propia evaluación dejó cerrado**.

**El de gravedad alta es el único que puede exponer datos de una usuaria, y
conviene entenderlo bien.** Mientras se está escribiendo un registro, el texto
del relato se puede seleccionar, copiar y pegar en cualquier otra aplicación del
teléfono; una vez guardado el registro ya no se puede. La causa es sencilla: ese
campo es el único del espacio privado que no está declarado como campo de
contraseña, y esa declaración es precisamente la que, en todos los demás, hace
desaparecer la opción «copiar» del menú de texto y le dice al teclado que no
memorice lo que se escribe. Importa porque es el único camino que la evaluación
encontró por el que el contenido de un registro puede salir del espacio
protegido sin herramientas especiales: a quien tenga el teléfono le bastaría
abrir otra aplicación y pegar. No es un fallo del cifrado ni del camuflaje —los
registros siguen guardados y enviados cifrados—, sino una protección que no se
aplicó a un campo. Se corrige en la versión siguiente.

Esto se descubrió, además, de una manera que vale la pena contar: era una de las
dos comprobaciones que ningún método automático podía hacer, porque escribir por
cable no pasa por el teclado del teléfono y el emulador no deja forzar una copia.
Las hizo una persona a mano. La otra salió bien: **el teclado no aprende la clave
de acceso**, lo que cierra una duda que el proyecto arrastraba desde julio.

Los dos de gravedad media son estos. El primero es que, al revisar el archivo
instalable, se encontró que sobreviven a la ofuscación más nombres internos de
los que la documentación del proyecto tenía contados: ciento nueve etiquetas
generadas automáticamente por el lenguaje, entre ellas una que contiene la
palabra inglesa «Secret» —la misma que toda la convención de nombres del
proyecto evita— y un mensaje de error que nombra un componente interno. No es
un riesgo para la usuaria —quien tenga el teléfono no va a descompilar la
aplicación—, pero sí obliga a matizar una frase del documento de resultados de
pruebas, que afirmaba que no quedaba ningún símbolo legible. El segundo es una
caída de la aplicación que ocurre al escribir en un campo de texto y salir de la
pantalla: se reprodujo seis veces de nueve. La causa no está en el código de
Aurora sino en una versión antigua de una librería de Google, que ya tiene el
error corregido en versiones posteriores. Se midió su efecto real y es menor de
lo que parece: el sistema **no muestra ningún aviso de error**, porque la
aplicación ya estaba en segundo plano, de modo que el camuflaje no se rompe; lo
que se pierde es el texto no guardado.

Los tres restantes son higiene previa al piloto: el instalable todavía se firma
con la clave de pruebas y no con una propia; el manifiesto arrastra dos
componentes expuestos que vienen con la librería de autenticación y que Aurora
no usa; y falta declarar de forma explícita una configuración de red que hoy
funciona por el valor por defecto del sistema.

## Qué significan estos resultados para un prototipo TRL 4

El hallazgo de gravedad alta **sí debe corregirse antes de poner la aplicación
en manos de una usuaria**, y por eso se corrige de inmediato en la versión
siguiente junto con otros tres. No era previsible desde el diseño: el proyecto
había protegido deliberadamente todos los campos sensibles, y este quedó fuera
precisamente por ser el único que necesita escribirse largo y no tiene forma de
contraseña. Encontrarlo es, en sí mismo, el resultado que justifica haber hecho
la evaluación.

Los seis restantes sí son aceptables en este nivel de madurez, por tres razones.
Primero, porque **ninguno compromete la confidencialidad de los datos**: todo lo
sensible sigue cifrado en reposo, aislado en la nube y ausente de los registros
del sistema. Segundo, porque **ninguno rompe el camuflaje frente a la amenaza
modelada**: los nombres que sobreviven en el archivo instalable solo son
visibles para quien lo descompile, y la caída de la aplicación no produce ningún
aviso en pantalla. Y tercero, porque los tres de higiene son tareas de
preparación conocidas, baratas y con una corrección ya definida, que
corresponden al paso del prototipo al piloto con usuarias reales y no a la
validación técnica que TRL 4 exige.

Conviene añadir una precisión sobre lo que la evaluación encontró y lo que no.
**No apareció ningún riesgo nuevo sobre los datos de las usuarias**: todo lo que
toca cifrado, aislamiento o confidencialidad ya estaba previsto y documentado en
el registro de pendientes del proyecto, y lo que esta prueba aportó fue medirlo
—dos de esos riesgos quedaron acotados con cifras exactas que antes solo se
describían en palabras, y uno resultó ser mayor de lo registrado, lo que dio
origen a uno de los hallazgos—. Los otros cinco sí son nuevos y no estaban en
ese registro: cuatro de preparación para el despliegue, y el de gravedad alta,
que nadie había previsto y que apareció solo cuando una persona se sentó a
probar la aplicación a mano. Esa es, quizá, la lección más útil del ejercicio:
la automatización cubrió muchísimo terreno —ochenta y siete pruebas del
estándar, medición de píxeles, entropía, registros del sistema, reglas de la
nube— pero el gesto más cotidiano, seleccionar un texto y copiarlo, quedó fuera
de su alcance.
Esa coincidencia entre lo que el equipo ya sabía sobre sus propios riesgos de
datos y lo que una verificación sistemática encuentra es, en sí misma, un
resultado: indica que el seguimiento de riesgos que se llevó durante el
desarrollo fue acertado, y señala con precisión dónde ese seguimiento no llegaba.

import java.util.Collections
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.auroraclima.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.auroraclima.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // P-05: ofuscación y reducción obligatorias — ningún APK sale del
            // entorno con los nombres del doble fondo legibles en el DEX (RNF-05).
            isMinifyEnabled = true
            isShrinkResources = true
            // TRL 4: se firma con la clave de depuración para poder instalar y
            // probar el release ofuscado en el emulador. La firma de producción
            // con keystore propio queda como trabajo futuro (ver PENDIENTES.md).
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // PdfBox-Android (RF-45) trae ~2,4 MB de CMaps CJK en assets que solo
        // usa la LECTURA de fuentes compuestas; Aurora solo escribe con las 14
        // fuentes estándar. Fuera del APK (A2-1: peso). Los AFM, la fuente de
        // respaldo y las listas de glifos sí se necesitan y se conservan.
        ignoreAssetsPatterns += listOf("<dir>cmap")
    }
    testOptions {
        // Robolectric necesita los assets fusionados (los AFM de Helvetica que
        // trae PdfBox-Android) para probar el escritor del PDF en la JVM (RF-45).
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Navegación fachada ⇄ doble fondo (RF-05, RF-12)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Inyección de dependencias
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Persistencia local
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Cifrado en reposo de la BD central del espacio interno (RNF-01, P-03)
    implementation(libs.sqlcipher.android)

    // Orientación EXIF al sanear imágenes adjuntas (RF-33); la limpieza de
    // metadatos la hace la re-codificación — esta lib solo evita fotos acostadas
    implementation(libs.androidx.exifinterface)

    // Exportación de registros a PDF cifrado con contraseña (RF-45). BouncyCastle
    // solo lo usaría el cifrado por certificado, que Aurora no emplea: se excluye
    // (≈11 MB) y el cifrado estándar AES-256 corre con javax.crypto — ADR RF-45
    implementation(libs.pdfbox.android) {
        exclude(group = "org.bouncycastle")
    }

    // Red (Open-Meteo)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // P-13: pruebas de la capa data real de access en la JVM (no entran al APK)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // La BD de prueba del test de cifrado (core/database) declara sus propios DAOs
    kspAndroidTest(libs.room.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// ─────────────────────────────────────────────────────────────────────────────
// Camuflaje del DEX del release (H-1 de la evaluación OWASP, P-89)
// ─────────────────────────────────────────────────────────────────────────────
// La §2 de docs/RESULTADOS_PRUEBAS.md verificaba el camuflaje del release con
// `apkanalyzer dex packages`, que enumera NOMBRES DE CLASE y no ve los literales
// de cadena. La evaluación OWASP barrió los literales y encontró vocabulario
// propio que R8 no toca: plantillas de `toString()` de data class, nombres de
// constantes de enum y mensajes de excepción. Este guardián cierra ese hueco.
//
// Es una tarea de Gradle y no una prueba de JVM a propósito: el APK release puede
// no existir cuando corren los tests, y un guardián que se salta solo es peor que
// ninguno. Corre en cada construcción de release —`assembleRelease` y
// `bundleRelease`—, así que ningún artefacto sale del entorno sin pasar por él.
// Ojo: el comando de verificación habitual de CLAUDE.md (`assembleDebug lint
// test`) NO construye el release; el que sí lo hace, y por tanto lo ejecuta, es
// el de la §1 de docs/RESULTADOS_PRUEBAS.md, que termina en `assembleRelease`.
//
// Qué juzga: TODAS las cadenas del DEX, sin filtro previo de atribución. La
// primera versión solo miraba las que llevaban la ruta del paquete o forma de
// `toString()`, y por eso dejaba pasar `questionnaireRepository` y el propio
// mensaje de excepción que H-1 denunció. El precio de juzgar todo es tener que
// absorber el ruido de las librerías en la línea base, una por una y con su
// causa escrita, y eso es lo que hace creíble un «0 hallazgos».
val verificarCamuflajeDex by tasks.registering {
    group = "verification"
    description = "Falla si el DEX del APK release contiene términos vedados por el camuflaje (RNF-05)."

    val apkProv = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val terminosProv = layout.projectDirectory.file("src/test/resources/camuflaje-terminos-vedados.txt")
    val excepcionesProv = layout.projectDirectory.file("src/test/resources/camuflaje-dex-excepciones.txt")
    val informeProv = layout.buildDirectory.file("reports/camuflaje/dex-release.txt")
    inputs.file(apkProv).withPropertyName("apkRelease")
    inputs.file(terminosProv).withPropertyName("terminosVedados")
    inputs.file(excepcionesProv).withPropertyName("excepciones")
    outputs.file(informeProv).withPropertyName("informe")
    // Sin esto, Gradle marca la tarea UP-TO-DATE cuando el APK y las dos listas no
    // cambiaron, y se salta el `doLast` COMPLETO: ni autochequeo, ni canarios, ni
    // juicio. El informe afirmaba «autochequeo en cada corrida» y con el UP-TO-DATE
    // eso era literalmente falso (hallazgo A-1 de la revisión de arquitectura). La
    // tarea tarda segundos: saltarla no compra nada y cuesta la garantía.
    outputs.upToDateWhen { false }

    doLast {
        val apk = apkProv.get().asFile
        val informe = informeProv.get().asFile
        fun leerLista(f: java.io.File) = f.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("[") }
        val vedados = leerLista(terminosProv.asFile)
        val aceptadas = leerLista(excepcionesProv.asFile).toSet()
        require(vedados.size >= 40) { "La lista de términos vedados parece incompleta: ${vedados.size}." }

        // El DEX precede cada cadena de su longitud en MUTF-8, y ese byte a veces
        // resulta imprimible y se pega al principio. Se descarta por regla, no a
        // ojo: es prefijo si su valor coincide con la longitud en bytes del resto.
        fun sinPrefijoDeLongitud(cadena: String): String {
            if (cadena.length < 2) return cadena
            val resto = cadena.substring(1)
            return if (cadena[0].code == resto.toByteArray(Charsets.UTF_8).size) resto else cadena
        }

        // La coincidencia con la línea base es exacta —una excepción no debe tapar
        // de rebote una cadena nueva— con una sola tolerancia: se prueba también la
        // forma recortada, porque el archivo de excepciones no puede expresar un
        // espacio al principio (se recortan al leerlo) y en el DEX hay cadenas así.
        fun aceptada(cadena: String) = cadena in aceptadas || cadena.trim() in aceptadas

        // Un término de 3 letras casa por accidente dentro de casi cualquier cosa
        // —«sos» vive en «ISOSpeed» y en basura binaria como «\sOSUpPS9»— y esas
        // cadenas no son estables entre construcciones, así que el release empezaba a
        // fallar al azar. Para los términos cortos se exige que no estén pegados a
        // una letra: así «SOS» suelto se caza y «ISOSpeed» no.
        //
        // El precio, dicho claro: un `sosBoton` propio se le escaparía AQUÍ. No queda
        // sin vigilar, porque `CamouflageResourcesTest` aplica los dos ámbitos a los
        // identificadores y a los literales de las fuentes, que es donde ese nombre
        // nacería. Este guardián existe para el residuo que R8 deja, no para sustituir
        // al de las fuentes.
        fun casa(cadena: String, termino: String): Boolean {
            if (termino.length > 3) return cadena.contains(termino, ignoreCase = true)
            var desde = 0
            while (true) {
                val i = cadena.indexOf(termino, desde, ignoreCase = true)
                if (i < 0) return false
                val antes = cadena.getOrNull(i - 1)
                val despues = cadena.getOrNull(i + termino.length)
                if (antes?.isLetter() != true && despues?.isLetter() != true) return true
                desde = i + 1
            }
        }

        fun juzgar(candidatas: Collection<String>): List<Pair<String, String>> = candidatas.mapNotNull { bruta ->
            val cadena = sinPrefijoDeLongitud(bruta)
            if (aceptada(cadena)) return@mapNotNull null
            vedados.firstOrNull { casa(cadena, it) }?.let { it to cadena }
        }

        // Autochequeo: la lógica de juicio se ejerce en CADA corrida contra cuatro
        // canarios —uno por cada forma en que el vocabulario sobrevive a R8— y
        // contra tres cadenas inocentes. Si el guardián deja de cazar o empieza a
        // ladrar de más, se sabe aquí y no dentro de un año. Los cuatro canarios
        // son, en orden: plantilla de `toString()` (la forma de H-1), constante de
        // enum (la de H-8), prosa en español y prosa con tilde —esta última solo
        // la caza un extractor que decodifique UTF-8—.
        val canarios = listOf(
            "SecretCodeChecks(hasMinLength=",
            "QUICK_EXIT",
            "no se pudo descifrar la evidencia",
            "agresión del día"
        )
        val cazados = juzgar(canarios).map { it.second }.toSet()
        val perdidos = canarios.filterNot { it in cazados }
        if (perdidos.isNotEmpty()) {
            throw GradleException(
                "El guardián del camuflaje dejó de funcionar: no cazó $perdidos. " +
                    "Revisar la lista de términos y la lógica de juicio antes de confiar en el resultado."
            )
        }
        val falsosPositivos = juzgar(listOf("Bucaramanga", "temperatura mínima", "pronóstico de 5 días"))
        require(falsosPositivos.isEmpty()) {
            "El guardián marca cadenas inocentes de la fachada: $falsosPositivos."
        }

        // Alcance: TODO lo que puede llevar texto propio de Aurora. La primera
        // versión miraba solo `classes*.dex`, y `resources.arsc` es justo donde
        // `CamouflageResourcesTest` no llega: `shrinkResources` y `optimizeResources`
        // reescriben, y los recursos de las librerías no se miran nunca en las
        // fuentes (hallazgo F-8 de la auditoría).
        //
        // Alcance: una lista de permitidos, y todo lo demás fuera. El criterio es
        // «todo lo que este build pueda escribir», no «todo lo que no sea una
        // librería», y la diferencia importa: se midió cada categoría antes de
        // incluirla y las que entraron añaden CERO ruido, así que entraron gratis.
        //
        // Cuatro entradas no vienen de ninguna dependencia y por eso están DENTRO:
        // `assets/dexopt/baseline.prof` y su `.profm`, derivados de las clases de
        // Aurora; `META-INF/version-control-info.textproto`, derivado del repositorio;
        // y `kotlin-tooling-metadata.json`, que lo genera el plugin de Kotlin a partir
        // de la configuración de este build (es byte-idéntico al de
        // `build/kotlinToolingMetadata/`). Las tres primeras las señaló una auditoría;
        // la cuarta, la siguiente, al falsificar con un `cmp` la frase «todo lo
        // excluido viaja byte a byte desde una dependencia».
        //
        // Lo que queda fuera sí viaja byte a byte desde una dependencia: `lib/*.so`,
        // los `.proto` de Firestore y gRPC, los builtins de Kotlin, okhttp3, las
        // fuentes de PdfBox y los `.properties` de metadatos de cada librería.
        // Juzgarlos exigiría 43 excepciones más, varias de basura binaria que no es
        // estable entre construcciones.
        val enAlcance = Regex(
            """classes\d*\.dex|resources\.arsc|AndroidManifest\.xml|res/.*|META-INF/.*""" +
                """|assets/(?!com/tom_roush/).*|kotlin-tooling-metadata\.json|DebugProbesKt\.bin"""
        )
        // La exclusión, declarada. «Se barrió a mano una vez» no es una garantía
        // permanente: el día que aparezca en el APK una familia de entradas que nadie
        // juzga —un `assets/` propio con un catálogo sembrado, por ejemplo— esto hace
        // fallar el release y obliga a decidir si entra en alcance o se excluye con
        // motivo. Es el mismo arreglo que `upToDateWhen { false }`, un nivel más
        // arriba: lo que no se mira, se declara (hallazgo M-1 de la auditoría).
        // Son, en orden: las librerías nativas de SQLCipher; los `.proto` de Firestore
        // y gRPC; los builtins y metadatos del compilador de Kotlin; la lista de
        // sufijos públicos de OkHttp; y las fuentes y listas de glifos que PdfBox
        // trae vendidas.
        val fueraDeAlcance = listOf(
            "lib/",
            "google/",
            "kotlin/",
            "okhttp3/",
            "assets/com/tom_roush/"
        )
        // Solo `.properties`: son los metadatos que cada librería deja en la raíz, y
        // crecen con cada dependencia nueva. `.bin` y `.json` salieron de aquí porque
        // sus dos únicos casos los genera este build y ahora están en alcance.
        val extensionesDeRaiz = listOf(".properties")
        var total = 0
        var entradas = 0
        // Cadenas al estilo de `strings`, pero decodificando UTF-8: un byte de tilde
        // no corta la cadena. Con el extractor anterior, solo ASCII, los términos
        // acentuados de la lista no podían coincidir nunca.
        val cadenas = LinkedHashSet<String>()
        ZipFile(apk).use { zip ->
            val juzgables = Collections.list(zip.entries())
                .filter { entrada: ZipEntry -> enAlcance.matches(entrada.name) }
            // Pisos de no vacuidad. Los cuatro canarios ejercen la lógica de JUICIO;
            // nada ejercía la de ALCANCE, que es justo donde creció el guardián al
            // pasar de solo el DEX a arsc + manifiesto + res/. Sin esto, quien tocara
            // `enAlcance` y dejara fuera los recursos vería 4/4 canarios y 0 hallazgos,
            // y el guardián estaría mirando la mitad (hallazgo M-8 de la revisión).
            require(juzgables.any { it.name.endsWith(".dex") }) {
                "No se encontró ningún classes*.dex en el APK: ¿cambió el formato?"
            }
            require(juzgables.any { it.name == "resources.arsc" }) {
                "Falta resources.arsc en el alcance: ahí llegan los recursos de las librerías."
            }
            require(juzgables.any { it.name.startsWith("res/") }) {
                "Falta res/ en el alcance: ahí viven los recursos propios."
            }
            require(juzgables.any { it.name.startsWith("META-INF/") }) {
                "Falta META-INF/ en el alcance."
            }
            val sinDeclarar = Collections.list(zip.entries())
                .map { entrada: ZipEntry -> entrada.name }
                .filterNot { enAlcance.matches(it) }
                .filterNot { nombre -> fueraDeAlcance.any { nombre.startsWith(it) } }
                .filterNot { nombre ->
                    "/" !in nombre && extensionesDeRaiz.any { nombre.endsWith(it) }
                }
            require(sinDeclarar.isEmpty()) {
                "Entradas del APK que nadie juzga y que nadie declaró fuera de alcance: " +
                    "$sinDeclarar. Decidir si entran en el alcance del guardián o se " +
                    "excluyen con motivo escrito (RNF-05)."
            }
            require(juzgables.size >= 100) {
                "Solo ${juzgables.size} entradas en alcance: el filtro se rompió."
            }
            entradas = juzgables.size
            for (entrada in juzgables) {
                val bytes: ByteArray = zip.getInputStream(entrada).readBytes()
                var inicio = -1

                // Piso de 3 y no de 4: de los 49 términos, «sos» mide 3, así que con
                // el piso anterior una cadena que fuera exactamente «sos» era
                // estructuralmente inalcanzable para el guardián — la misma especie de
                // hueco que el de los acentuados. Bajarlo cuesta 0 hallazgos nuevos,
                // medido (hallazgo A-6 de la auditoría).
                fun cerrar(fin: Int) {
                    if (inicio >= 0 && fin - inicio >= 3) {
                        total++
                        cadenas += String(bytes, inicio, fin - inicio, Charsets.UTF_8)
                    }
                    inicio = -1
                }
                for (indice in bytes.indices) {
                    val c = bytes[indice].toInt() and 0xFF
                    if (c in 0x20..0x7E || c >= 0x80) {
                        if (inicio < 0) inicio = indice
                    } else {
                        cerrar(indice)
                    }
                }
                cerrar(bytes.size)
            }
        }
        require(cadenas.isNotEmpty()) { "No se extrajo ninguna cadena: ¿cambió el formato del APK?" }

        // El envoltorio de los campos sensibles NO debe sobrevivir a R8. No es una
        // curiosidad: durante la evaluación se escribió en el código que el guardián
        // del DEX servía de respaldo de `SensitiveFieldsGuardTest`, y era falso
        // precisamente porque R8 borra estos tres nombres, de modo que este guardián
        // no puede ver un campo sin envolver. Esto convierte esa medición en una
        // aserción permanente y avisará el día que R8 deje de borrarlos — que es el
        // día en que habría que revisar aquella frase (mejora M-3 de la revisión).
        val envoltorioEnElArtefacto = listOf("SecureTextEntry", "NoCopyTextToolbar", "NoImeLearning")
            .filter { nombre -> cadenas.any { nombre in it } }
        require(envoltorioEnElArtefacto.isEmpty()) {
            "R8 dejó de borrar del artefacto $envoltorioEnElArtefacto. No es un fallo de " +
                "camuflaje por sí mismo, pero invalida el alcance declarado de " +
                "SensitiveFieldsGuardTest: revisar su comentario y el §10.1 del informe."
        }

        val hallazgos = juzgar(cadenas)

        // Una excepción que no hace falta es, o una causa ya arreglada —y entonces
        // sobra—, o el síntoma de que el guardián dejó de mirar donde mira esa
        // excepción. Las dos cosas hay que verlas; no fallan el build.
        //
        // «No hace falta» es más exigente que «no aparece»: se comprueba si, QUITÁNDOLA,
        // el guardián reportaría algo. Con la medida anterior, las excepciones de
        // `ISOSpeed` seguían contando como vivas porque la cadena sigue en el DEX,
        // aunque la regla de términos cortos ya no la marque.
        val normalizadas = cadenas.map { sinPrefijoDeLongitud(it) }
            .flatMap { listOf(it, it.trim()) }
            .toSet()
        val muertas = aceptadas.filterNot { excepcion ->
            excepcion in normalizadas && vedados.any { casa(excepcion, it) }
        }

        informe.parentFile.mkdirs()
        informe.writeText(
            buildString {
                appendLine("Camuflaje del DEX del release")
                appendLine("APK: ${apk.name} (${apk.length()} bytes)")
                appendLine("Cadenas de 3 o más caracteres: $total (${cadenas.size} distintas)")
                appendLine("Se juzgan TODAS, sin filtro de atribución.")
                appendLine("Entradas del APK en alcance: $entradas (dex, arsc, manifiesto, res/, META-INF/, assets/)")
                appendLine("Términos vedados comprobados: ${vedados.size}")
                appendLine("Autochequeo de canarios: ${canarios.size}/${canarios.size} cazados, 0 falsos positivos")
                appendLine("Excepciones documentadas en la línea base: ${aceptadas.size}")
                appendLine("De ellas, sin disparar (revisar si sobran): ${muertas.size}")
                muertas.forEach { appendLine("  $it") }
                appendLine("Hallazgos nuevos: ${hallazgos.size}")
                hallazgos.forEach { (termino, cadena) -> appendLine("  «$termino» en: ${cadena.take(120)}") }
            }
        )

        if (hallazgos.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "El DEX del release tiene ${hallazgos.size} cadena(s) " +
                            "con términos vedados (RNF-05, H-1/P-89):"
                    )
                    hallazgos.forEach { (termino, cadena) -> appendLine("  «$termino» en: ${cadena.take(120)}") }
                    appendLine("Informe: $informe")
                    appendLine("Arreglar la causa (renombrar el símbolo o neutralizar el mensaje).")
                    appendLine("Aceptarla solo con motivo y pendiente, en la línea base de excepciones.")
                }
            )
        }
        logger.lifecycle(
            "Camuflaje del DEX: $total cadenas (${cadenas.size} distintas), ${vedados.size} términos, " +
                "${aceptadas.size} excepciones documentadas, canarios en verde, 0 hallazgos nuevos."
        )
    }
}

// Encadenado a las DOS formas de construir un release. `bundleRelease` (el AAB)
// no dispara `assembleRelease`, así que sin esta segunda entrada una publicación
// futura se saltaría el guardián. El `dependsOn` hace la tarea autosuficiente
// —`./gradlew verificarCamuflajeDex` construye lo que necesita— y, de paso,
// evita que juzgue un APK viejo: si la construcción falla, el guardián no corre.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    finalizedBy(verificarCamuflajeDex)
}
verificarCamuflajeDex { dependsOn("assembleRelease") }

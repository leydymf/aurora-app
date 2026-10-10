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
// propio que R8 no toca: plantillas de `toString()` de data class y mensajes de
// excepción. Este guardián cierra ese hueco.
//
// Es una tarea de Gradle y no una prueba de JVM a propósito: el APK release
// puede no existir cuando corren los tests, y un guardián que se salta solo es
// peor que ninguno. Aquí corre donde el artefacto existe de verdad, encadenada a
// assembleRelease, así que entra en el comando de verificación habitual sin que
// nadie tenga que acordarse de ella.
val verificarCamuflajeDex by tasks.registering {
    group = "verification"
    description = "Falla si el DEX del APK release contiene términos vedados por el camuflaje (RNF-05)."

    val apkProv = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val terminosProv = layout.projectDirectory.file("camuflaje-terminos-vedados.txt")
    val excepcionesProv = layout.projectDirectory.file("camuflaje-dex-excepciones.txt")
    val informeProv = layout.buildDirectory.file("reports/camuflaje/dex-release.txt")
    inputs.file(apkProv).withPropertyName("apkRelease")
    inputs.file(terminosProv).withPropertyName("terminosVedados")
    inputs.file(excepcionesProv).withPropertyName("excepciones")
    outputs.file(informeProv).withPropertyName("informe")

    doLast {
        val apk = apkProv.get().asFile
        val informe = informeProv.get().asFile
        fun leerLista(f: java.io.File) = f.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
        val vedados = leerLista(terminosProv.asFile)
        val aceptadas = leerLista(excepcionesProv.asFile).toSet()
        require(vedados.isNotEmpty()) { "La lista de términos vedados está vacía." }

        // Cadenas imprimibles del DEX, al estilo de `strings`: es lo que R8 deja
        // legible y lo que un `strings app-release.apk` enseñaría.
        val cadenas = mutableListOf<String>()
        ZipFile(apk).use { zip ->
            val dex = Collections.list(zip.entries())
                .filter { entrada: ZipEntry -> entrada.name.matches(Regex("""classes\d*\.dex""")) }
            for (entrada in dex) {
                val bytes: ByteArray = zip.getInputStream(entrada).readBytes()
                val actual = StringBuilder()
                for (indice in bytes.indices) {
                    val c = bytes[indice].toInt() and 0xFF
                    if (c in 0x20..0x7E) {
                        actual.append(c.toChar())
                    } else {
                        if (actual.length >= 4) cadenas += actual.toString()
                        actual.setLength(0)
                    }
                }
                if (actual.length >= 4) cadenas += actual.toString()
            }
        }
        require(cadenas.isNotEmpty()) { "No se extrajo ninguna cadena del DEX: ¿cambió el formato del APK?" }

        // Solo se juzgan las cadenas ATRIBUIBLES A AURORA. Las librerías usan
        // «secret», «panic» o «hidden» con toda naturalidad (javax.crypto.SecretKey,
        // ISOSpeed de EXIF, panicPickResult de gRPC) y eso no dice nada del
        // camuflaje. Las dos formas en que el vocabulario propio sobrevive a R8,
        // que son las que midió la evaluación OWASP, sí:
        //   a) cadenas con la ruta del paquete, que conserva la reflexión de Room
        //      y los descriptores de kotlinx.serialization;
        //   b) las plantillas «Nombre(campo=» que el compilador genera para el
        //      toString() de las data class.
        val plantillaToString = Regex("""[A-Z][A-Za-z0-9_]{2,}\(\w+=""")
        val propias = cadenas.asSequence()
            .filter { it.contains("auroraclima") || plantillaToString.containsMatchIn(it) }
            .distinct()
            .toList()

        val hallazgos = propias.flatMap { cadena ->
            vedados.filter { cadena.contains(it, ignoreCase = true) }.map { it to cadena }
        }.filterNot { (_, cadena) -> cadena in aceptadas }

        informe.parentFile.mkdirs()
        informe.writeText(
            buildString {
                appendLine("Camuflaje del DEX del release")
                appendLine("APK: ${apk.name} (${apk.length()} bytes)")
                appendLine("Cadenas de 4 o más caracteres en el DEX: ${cadenas.size}")
                appendLine("De ellas, atribuibles a Aurora: ${propias.size}")
                appendLine("Términos vedados comprobados: ${vedados.size}")
                appendLine("Excepciones documentadas en la línea base: ${aceptadas.size}")
                appendLine("Hallazgos nuevos: ${hallazgos.size}")
                hallazgos.forEach { (termino, cadena) -> appendLine("  «$termino» en: ${cadena.take(120)}") }
            }
        )

        if (hallazgos.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "El DEX del release tiene ${hallazgos.size} cadena(s) propia(s) " +
                            "con términos vedados (RNF-05, H-1/P-89):"
                    )
                    hallazgos.forEach { (termino, cadena) -> appendLine("  «$termino» en: ${cadena.take(120)}") }
                    appendLine("Informe: $informe")
                    appendLine("Arreglar la causa (renombrar el símbolo o neutralizar el mensaje).")
                    appendLine("Aceptarla solo con motivo y pendiente, en app/camuflaje-dex-excepciones.txt.")
                }
            )
        }
        logger.lifecycle(
            "Camuflaje del DEX: ${propias.size} cadenas propias de ${cadenas.size}, " +
                "${vedados.size} términos, ${aceptadas.size} excepciones documentadas, 0 hallazgos nuevos."
        )
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verificarCamuflajeDex)
}

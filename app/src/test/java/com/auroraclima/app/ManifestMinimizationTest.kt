package com.auroraclima.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Auditoría del manifest declarado (RNF-04 minimización, RF-06 ausencia de indicios).
 *
 * - Permisos: exactamente los del inventario aprobado (solo INTERNET; esperable en
 *   una app de clima). Cualquier permiso nuevo debe justificarse contra un RF.
 * - Componentes: un único activity exportado (el launcher) y, desde RF-33, un único
 *   provider — el FileProvider NO exportado que recibe la captura de la cámara del
 *   sistema (EXTRA_OUTPUT); sus propiedades se auditan una a una abajo. Ningún
 *   service, receiver ni otro provider: nada que un explorador de ajustes o un
 *   intent externo pueda descubrir.
 * - allowBackup=false: el auto-backup no debe copiar la futura BD interna (P-00/P-03).
 *
 * El manifest FUSIONADO del APK (permisos que agregan las libs) se revisa aparte: P-06.
 */
class ManifestMinimizationTest {
    // INTERNET: consulta del clima. ACCESS_NETWORK_STATE: señal de conectividad
    // del reingreso silencioso (P-31 opción C, acta 2026-08-15) — nivel normal,
    // esperable en una app de clima (RNF-04/RNF-05).
    private val approvedPermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE"
    )

    private val manifest: Element by lazy {
        val file = requireNotNull(
            sequenceOf(
                File("src/main/AndroidManifest.xml"),
                File("app/src/main/AndroidManifest.xml")
            ).firstOrNull { it.exists() }
        ) { "No se encontró AndroidManifest.xml" }
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttr(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)

    @Test
    fun `solo se declaran los permisos del inventario aprobado`() {
        val declared = elements("uses-permission").map { it.androidAttr("name") }.toSet()
        assertEquals(
            "Permisos fuera del inventario aprobado (RNF-04)",
            approvedPermissions,
            declared
        )
    }

    @Test
    fun `no se declaran features ni permisos opcionales`() {
        val extras = listOf("uses-feature", "uses-permission-sdk-23", "queries")
            .flatMap { elements(it) }
        assertTrue(
            "Declaraciones no justificadas en el manifest (RNF-04)",
            extras.isEmpty()
        )
    }

    @Test
    fun `los unicos componentes son el activity launcher y el provider de captura`() {
        val activities = elements("activity")
        val providers = elements("provider")
        val others = listOf("service", "receiver", "activity-alias")
            .flatMap { elements(it) }

        assertEquals("Debe existir exactamente un activity", 1, activities.size)
        assertEquals("Debe existir exactamente un provider (captura RF-33 y PDF RF-45)", 1, providers.size)
        assertTrue(
            "No debe haber services, receivers ni aliases propios (RF-06)",
            others.isEmpty()
        )
    }

    @Test
    fun `el provider de captura es el FileProvider de androidx, no exportado y de rutas acotadas`() {
        val provider = elements("provider").single()

        // La implementación es la de la plataforma androidx — sin clase propia que
        // auditar ni superficie nueva de código exportable.
        assertEquals("androidx.core.content.FileProvider", provider.androidAttr("name"))
        // No exportado: solo el permiso puntual por URI del intent de cámara.
        assertEquals("false", provider.androidAttr("exported"))
        assertEquals("true", provider.androidAttr("grantUriPermissions"))
        // Autoridad neutra derivada del applicationId (RNF-05).
        assertEquals("\${applicationId}.files", provider.androidAttr("authorities"))

        val metadata = provider.getElementsByTagName("meta-data")
        assertEquals("El provider declara solo sus rutas", 1, metadata.length)
        val paths = metadata.item(0) as Element
        assertEquals("android.support.FILE_PROVIDER_PATHS", paths.androidAttr("name"))
        assertEquals("@xml/file_paths", paths.androidAttr("resource"))
    }

    @Test
    fun `el FileProvider expone exactamente los dos temporales de la cache privada`() {
        // RF-33 (captura de cámara) y RF-45 (PDF cifrado para compartir): nada
        // más del almacenamiento privado es alcanzable por URI — en particular
        // el almacén cifrado de adjuntos y la BD quedan fuera por construcción.
        val file = requireNotNull(
            sequenceOf(
                File("src/main/res/xml/file_paths.xml"),
                File("app/src/main/res/xml/file_paths.xml")
            ).firstOrNull { it.exists() }
        ) { "No se encontró file_paths.xml" }
        val paths = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val children = (0 until paths.childNodes.length)
            .map { paths.childNodes.item(it) }
            .filterIsInstance<Element>()

        assertEquals(
            "Solo rutas de la caché privada",
            setOf("cache-path"),
            children.map { it.tagName }.toSet()
        )
        assertEquals(
            mapOf("m" to "media_pending/", "d" to "doc_out/"),
            children.associate { it.getAttribute("name") to it.getAttribute("path") }
        )
    }

    @Test
    fun `solo el launcher esta exportado y con el filtro estandar`() {
        val exported = elements("activity") + elements("service") + elements("receiver") +
            elements("provider")
        val exportedTrue = exported.filter { it.androidAttr("exported") == "true" }

        assertEquals("Solo el activity launcher puede estar exportado", 1, exportedTrue.size)
        val launcher = exportedTrue.single()
        val filters = launcher.getElementsByTagName("intent-filter")
        assertEquals("El launcher debe tener un único intent-filter", 1, filters.length)
        val declaredNames = (listOf("action", "category"))
            .flatMap { tag ->
                val nodes = launcher.getElementsByTagName(tag)
                (0 until nodes.length).map { (nodes.item(it) as Element).androidAttr("name") }
            }
            .toSet()
        assertEquals(
            "El intent-filter debe ser exactamente MAIN/LAUNCHER (RF-06: sin puertas extra)",
            setOf("android.intent.action.MAIN", "android.intent.category.LAUNCHER"),
            declaredNames
        )
    }

    /**
     * `androidx.compose.ui.tooling` (debugImplementation, necesaria para los
     * @Preview) fusiona una `PreviewActivity` EXPORTADA que permitiría abrir
     * por ADB cualquier composable sin parámetros FUERA del grafo seguro: sin
     * FLAG_SECURE, sin clave personal y con entrada propia en recientes. El
     * manifest de debug la retira; este test impide que esa retirada se pierda
     * (auditoría 2026-10-06 H-1). El release nunca la tuvo.
     */
    @Test
    fun `el manifest de debug retira la activity exportada de las previsualizaciones`() {
        val file = requireNotNull(
            sequenceOf(
                File("src/debug/AndroidManifest.xml"),
                File("app/src/debug/AndroidManifest.xml")
            ).firstOrNull { it.exists() }
        ) { "Falta app/src/debug/AndroidManifest.xml: PreviewActivity volvería a quedar exportada" }
        val debugManifest = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement
        val activities = debugManifest.getElementsByTagName("activity")
        val removed = (0 until activities.length)
            .map { activities.item(it) as Element }
            .any { element ->
                element.androidAttr("name") == "androidx.compose.ui.tooling.PreviewActivity" &&
                    element.getAttributeNS("http://schemas.android.com/tools", "node") == "remove"
            }
        assertTrue("El manifest de debug debe retirar androidx.compose.ui.tooling.PreviewActivity", removed)
    }

    @Test
    fun `el backup automatico esta deshabilitado`() {
        val application = elements("application").single()
        assertEquals(
            "allowBackup debe ser false (P-00: el auto-backup copiaría la BD interna)",
            "false",
            application.androidAttr("allowBackup")
        )
    }

    @Test
    fun `la configuracion de seguridad de red esta declarada`() {
        val application = elements("application").single()
        assertEquals(
            "La app debe declarar networkSecurityConfig (H-5, P-93): sin él, el bloqueo " +
                "del tráfico en claro depende del valor por defecto de targetSdk y " +
                "desaparecería en silencio si alguien lo bajara",
            "@xml/network_security_config",
            application.androidAttr("networkSecurityConfig")
        )
    }

    @Test
    fun `la configuracion de red prohibe el trafico en claro y las CA de usuario`() {
        val config = requireNotNull(
            sequenceOf(
                File("src/main/res/xml/network_security_config.xml"),
                File("app/src/main/res/xml/network_security_config.xml")
            ).firstOrNull { it.exists() }
        ) { "Falta res/xml/network_security_config.xml (H-5, P-93)" }
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(config)

        val base = xml.getElementsByTagName("base-config").item(0) as Element
        assertEquals(
            "cleartextTrafficPermitted debe ser false: ningún extremo de Aurora usa HTTP",
            "false",
            base.getAttribute("cleartextTrafficPermitted")
        )

        val anclas = (0 until xml.getElementsByTagName("certificates").length)
            .map { (xml.getElementsByTagName("certificates").item(it) as Element).getAttribute("src") }
        assertEquals(
            "Solo debe confiarse en el almacén del sistema: en el modelo de amenaza de " +
                "Aurora, quien tiene el teléfono podría instalar una CA propia para leer " +
                "el tráfico",
            listOf("system"),
            anclas
        )
    }
}

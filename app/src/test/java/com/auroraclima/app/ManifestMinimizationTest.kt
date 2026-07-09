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
 * - Componentes: un único activity exportado (el launcher). Ningún service, receiver
 *   ni provider propio: nada que un explorador de ajustes o un intent externo pueda
 *   descubrir.
 * - allowBackup=false: el auto-backup no debe copiar la futura BD interna (P-00/P-03).
 *
 * El manifest FUSIONADO del APK (permisos que agregan las libs) se revisa aparte: P-06.
 */
class ManifestMinimizationTest {
    private val approvedPermissions = setOf("android.permission.INTERNET")

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
    fun `el unico componente es el activity launcher`() {
        val activities = elements("activity")
        val others = listOf("service", "receiver", "provider", "activity-alias")
            .flatMap { elements(it) }

        assertEquals("Debe existir exactamente un activity", 1, activities.size)
        assertTrue(
            "No debe haber services, receivers, providers ni aliases propios (RF-06)",
            others.isEmpty()
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

    @Test
    fun `el backup automatico esta deshabilitado`() {
        val application = elements("application").single()
        assertEquals(
            "allowBackup debe ser false (P-00: el auto-backup copiaría la BD interna)",
            "false",
            application.androidAttr("allowBackup")
        )
    }
}

package com.auroraclima.app.core.ui.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Contrato de degradación del esquema v2 (ADR 2026-08-14): `icon`/`accent`
 * SELECCIONAN dentro de la biblioteca cerrada; un valor ausente, corrupto o
 * desconocido degrada POR CAMPO al mapeo automático por id, que conserva su
 * fallback neutro. Un dato corrupto jamás rompe la pantalla (estilo RF-25).
 */
class ContentVisualTest {
    @Test
    fun `sin campos rige el mapeo automatico por id`() {
        assertEquals(ContentVisual(V2Icons.figure, V2Palette.accentProtection), topicVisual("t01"))
        assertEquals(ContentVisual(V2Icons.lobes, V2Palette.accentRecognize), topicVisual("t02", null, null))
        assertEquals(ContentVisual(V2Icons.health, V2Palette.accentCare), routeVisual("r01"))
    }

    @Test
    fun `un id desconocido sin campos cae al neutro`() {
        assertEquals(ContentVisual(V2Icons.guide, V2Palette.accentGroup), topicVisual("t99"))
        assertEquals(ContentVisual(V2Icons.place, V2Palette.accentGroup), routeVisual("r99"))
    }

    @Test
    fun `una seleccion valida de la biblioteca se respeta`() {
        assertEquals(
            ContentVisual(V2Icons.device, V2Palette.accentCare),
            topicVisual("t01", icon = "device", accent = "green")
        )
        assertEquals(
            ContentVisual(V2Icons.place, V2Palette.accentJustice),
            routeVisual("r01", icon = "pin", accent = "sand")
        )
    }

    @Test
    fun `un valor fuera de la biblioteca degrada al automatico`() {
        assertEquals(topicVisual("t03"), topicVisual("t03", icon = "unicornio", accent = "fucsia"))
        assertEquals(routeVisual("r02"), routeVisual("r02", icon = "", accent = " blue"))
    }

    @Test
    fun `la degradacion es por campo independiente`() {
        assertEquals(
            ContentVisual(V2Icons.device, V2Palette.accentProtection),
            topicVisual("t01", icon = "device", accent = "corrupto")
        )
        assertEquals(
            ContentVisual(V2Icons.figure, V2Palette.accentCare),
            topicVisual("t01", icon = "corrupto", accent = "green")
        )
    }

    @Test
    fun `el violeta de la nube no es seleccionable desde datos`() {
        // El acento de la salida rápida es exclusivo (anexo v2-29): pedirlo
        // desde un documento degrada igual que cualquier valor desconocido.
        assertEquals(topicVisual("t01"), topicVisual("t01", accent = "violet"))
        assertEquals(topicVisual("t01"), topicVisual("t01", accent = "cloud"))
        // Fijación ESTRUCTURAL (auditoría 2026-08-14): la exclusión no depende
        // de adivinar ids — el par de la nube no existe en la biblioteca, con
        // ningún nombre.
        assertFalse(selectableAccents.containsValue(V2Palette.accentCloud))
        assertFalse(selectableIcons.containsValue(V2Icons.cloud))
    }

    @Test
    fun `el neutro explicito coincide con el de un id desconocido`() {
        // P-68: un registro con tema retirado o sin tema se pinta exactamente
        // como la Guía pinta un id sin mapear.
        assertEquals(topicVisual("desconocido"), neutralContentVisual())
        assertEquals(ContentVisual(V2Icons.guide, V2Palette.accentGroup), neutralContentVisual())
    }

    @Test
    fun `un documento nuevo con seleccion explicita no depende del id`() {
        // Caso del panel: la administradora crea t05 y elige su visual.
        assertEquals(
            ContentVisual(V2Icons.device, V2Palette.accentRecognize),
            topicVisual("t05", icon = "device", accent = "lilac")
        )
    }
}

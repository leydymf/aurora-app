package com.auroraclima.app.records.data.export

import com.tom_roush.fontbox.FontBoxFont
import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.fontbox.ttf.TrueTypeFont
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.font.CIDFontMapping
import com.tom_roush.pdfbox.pdmodel.font.FontMapper
import com.tom_roush.pdfbox.pdmodel.font.FontMappers
import com.tom_roush.pdfbox.pdmodel.font.FontMapping
import com.tom_roush.pdfbox.pdmodel.font.PDCIDSystemInfo
import com.tom_roush.pdfbox.pdmodel.font.PDFontDescriptor

/**
 * Sustituye el `FontMapper` por defecto de PdfBox-Android (auditoría RF-45
 * H-2): aquel recorre `/system/fonts`, persiste un índice `.pdfbox.cache` en
 * la caché de la app y, si no encuentra una fuente, escribe la traza en
 * `System.err`. Aurora solo escribe con las 14 fuentes estándar (métricas y
 * codificación salen de los AFM), así que basta devolver siempre la fuente de
 * respaldo que ya viaja en los assets de la librería: sin escaneo del
 * sistema, sin archivo residual y sin salida a consola.
 */
internal object BundledFontMapper : FontMapper {
    private val fallback: TrueTypeFont by lazy {
        PDFBoxResourceLoader.getStream(FALLBACK_FONT).use { TTFParser().parse(it) }
    }

    /** Idempotente; debe correr antes del primer uso de `PDType1Font`. */
    fun install() {
        if (FontMappers.instance() !== this) FontMappers.set(this)
    }

    override fun getTrueTypeFont(baseFont: String?, fontDescriptor: PDFontDescriptor?): FontMapping<TrueTypeFont> =
        FontMapping(fallback, true)

    override fun getFontBoxFont(baseFont: String?, fontDescriptor: PDFontDescriptor?): FontMapping<FontBoxFont> =
        FontMapping(fallback, true)

    override fun getCIDFont(
        baseFont: String?,
        fontDescriptor: PDFontDescriptor?,
        cidSystemInfo: PDCIDSystemInfo?
    ): CIDFontMapping = CIDFontMapping(null, fallback, true)

    private const val FALLBACK_FONT = "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"
}

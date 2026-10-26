package com.auroraclima.app.records.ui

import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Nombres genéricos de los adjuntos (H-6, RNF-04): tipo, número por tipo y fecha; nunca el archivo. */
class AttachmentNamesTest {
    private val bogota = ZoneId.of("America/Bogota")

    private fun at(day: String, hour: Int = 10) =
        LocalDate.parse(day).atTime(hour, 0).atZone(bogota).toInstant().toEpochMilli()

    private fun attachment(id: Long, kind: AttachmentKind, millis: Long) =
        JournalAttachment(id = id, kind = kind, createdAtEpochMillis = millis)

    @Test
    fun `el numero cuenta dentro de cada tipo en el orden en que se adjuntaron`() {
        val names = listOf(
            attachment(3, AttachmentKind.IMAGE, at("2026-10-14")),
            attachment(1, AttachmentKind.IMAGE, at("2026-10-12")),
            attachment(2, AttachmentKind.AUDIO, at("2026-10-12", hour = 11)),
            attachment(4, AttachmentKind.DOCUMENT, at("2026-10-15"))
        ).names(bogota)

        assertEquals(AttachmentName(AttachmentKind.IMAGE, 1, LocalDate.parse("2026-10-12")), names[1])
        assertEquals(AttachmentName(AttachmentKind.AUDIO, 1, LocalDate.parse("2026-10-12")), names[2])
        assertEquals(AttachmentName(AttachmentKind.IMAGE, 2, LocalDate.parse("2026-10-14")), names[3])
        assertEquals(AttachmentName(AttachmentKind.DOCUMENT, 1, LocalDate.parse("2026-10-15")), names[4])
    }

    @Test
    fun `a igual instante el orden lo da el id y quitar otro tipo no cambia el numero`() {
        val same = at("2026-10-12")
        val all = listOf(
            attachment(5, AttachmentKind.IMAGE, same),
            attachment(6, AttachmentKind.IMAGE, same),
            attachment(7, AttachmentKind.AUDIO, same)
        )

        assertEquals(1, all.names(bogota).getValue(5).number)
        assertEquals(2, all.names(bogota).getValue(6).number)
        assertEquals(2, all.filter { it.kind == AttachmentKind.IMAGE }.names(bogota).getValue(6).number)
        assertEquals(2, all.minus(all[2]).names(bogota).getValue(6).number)
    }

    @Test
    fun `la fecha es la del dia local y no la del instante UTC`() {
        // 23:30 en Bogotá ya es el día siguiente en UTC: el nombre usa la hora de la persona.
        val lateNight = LocalDate.parse("2026-10-12").atTime(23, 30).atZone(bogota).toInstant().toEpochMilli()

        val name = listOf(attachment(1, AttachmentKind.IMAGE, lateNight)).names(bogota).getValue(1)

        assertEquals(LocalDate.parse("2026-10-12"), name.date)
        assertEquals("12 oct", name.date.toDayMonthText())
    }
}

package com.auroraclima.app.core.security

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contrato UTC de la conversión del selector de fecha (M-1 de la revisión de
 * arquitectura D6 S1): el selector de Material 3 maneja el día como millis a
 * medianoche UTC. Si la conversión usara la zona local, en Colombia (UTC-5)
 * el día elegido se guardaría con un día menos — el bug clásico de «correr un
 * día». Estas funciones son puras y deben ser inversas exactas entre sí.
 */
class SecureDayPickerConversionTest {
    @Test
    fun `medianoche UTC del dia corresponde a sus epoch millis`() {
        // 2026-07-14 -> días desde epoch * milisegundos por día.
        val day = LocalDate.of(2026, 7, 14)
        assertEquals(day.toEpochDay() * MILLIS_PER_DAY, day.toUtcEpochMilli())
    }

    @Test
    fun `millis a medianoche UTC vuelven al mismo dia`() {
        val millis = LocalDate.of(2026, 7, 14).toEpochDay() * MILLIS_PER_DAY
        assertEquals(LocalDate.of(2026, 7, 14), millis.toUtcLocalDate())
    }

    @Test
    fun `ida y vuelta conserva la fecha`() {
        val day = LocalDate.of(2026, 6, 28)
        assertEquals(day, day.toUtcEpochMilli().toUtcLocalDate())
    }

    @Test
    fun `millis dentro del dia UTC se truncan al mismo dia`() {
        // Cualquier instante del día (aquí +23h59m) sigue siendo el mismo día UTC.
        val nearEndOfDay = LocalDate.of(2026, 6, 28).toEpochDay() * MILLIS_PER_DAY + (MILLIS_PER_DAY - 1)
        assertEquals(LocalDate.of(2026, 6, 28), nearEndOfDay.toUtcLocalDate())
    }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

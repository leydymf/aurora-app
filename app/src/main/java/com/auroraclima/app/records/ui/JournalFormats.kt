package com.auroraclima.app.records.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatos de fecha del historial (mockups P18–P20). La app se publica solo en
 * español (UI y strings), así que el locale se fija en español para que las
 * fechas no queden en otro idioma si el sistema está configurado distinto.
 */
private val SPANISH = Locale.forLanguageTag("es")

/** «6 de julio de 2026» — campo de fecha del editor (P19). */
private val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' uuuu", SPANISH)

/** «2 JUL 2026» — filas del listado y encabezado del detalle (P18/P20). */
private val SHORT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu", SPANISH)

internal fun LocalDate.toLongDayText(): String = format(LONG_DAY)

internal fun LocalDate.toShortDayText(): String = format(SHORT_DAY).replace(".", "").uppercase(SPANISH)

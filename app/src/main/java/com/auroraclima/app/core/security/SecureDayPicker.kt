package com.auroraclima.app.core.security

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Selector de fecha del espacio interno (misma razón de ser que SecureDialog,
 * RNF-02/auditoría A1): el diálogo del selector crea una ventana PROPIA que el
 * grafo seguro no cubre, así que la bandera se fuerza aquí con securePolicy.
 * NoTracesGuardTest veda el selector de la plataforma fuera de core/security.
 *
 * El selector trabaja en días UTC (contrato del componente de Material 3):
 * la conversión LocalDate ⇄ millis usa siempre UTC para no correr un día.
 *
 * [maxDay] acota el último día seleccionable (inclusive); null no pone límite.
 * Un registro del historial es un hecho ya ocurrido, así que `records` pasa hoy
 * para vedar fechas futuras (P-47a).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureDayPicker(
    initialDay: LocalDate,
    confirmText: String,
    dismissText: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    maxDay: LocalDate? = null
) {
    val selectableDays = remember(maxDay) { selectableDaysUntil(maxDay) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDay.toUtcEpochMilli(),
        selectableDates = selectableDays
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            securePolicy = SecureFlagPolicy.SecureOn
        ),
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) onConfirm(millis.toUtcLocalDate()) else onDismiss()
                }
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        }
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/**
 * Conversiones LocalDate ⇄ millis SIEMPRE en UTC: el selector de Material 3
 * expone el día como millis a medianoche UTC, así que usar la zona local aquí
 * correría un día en husos al oeste de Greenwich (Colombia, UTC-5). Puras y
 * probadas por unidad (SecureDayPickerConversionTest).
 */
internal fun LocalDate.toUtcEpochMilli(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.toUtcLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Un día (en millis UTC del selector) es válido si no es posterior a [maxDay]. */
internal fun isDayNotAfter(utcTimeMillis: Long, maxDay: LocalDate): Boolean {
    return !utcTimeMillis.toUtcLocalDate().isAfter(maxDay)
}

/** Política de días seleccionables acotada por [maxDay]; sin límite si es null. */
@OptIn(ExperimentalMaterial3Api::class)
private fun selectableDaysUntil(maxDay: LocalDate?): SelectableDates {
    if (maxDay == null) return DatePickerDefaults.AllDates
    return object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean = isDayNotAfter(utcTimeMillis, maxDay)

        override fun isSelectableYear(year: Int): Boolean = year <= maxDay.year
    }
}

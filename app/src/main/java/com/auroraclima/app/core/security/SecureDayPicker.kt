package com.auroraclima.app.core.security

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.auroraclima.app.core.ui.v2.CloudReturnClearance
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
 * Va sobre [SecureWindow] y no sobre el `DatePickerDialog` de Material (v0.3.2): ese
 * diálogo no deja dibujar nada fuera de su tarjeta, y la ventana propia tapaba la nube
 * de salida rápida (RF-16). La tarjeta reproduce la de Material ([DayPickerCard]).
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
    SecureWindow(
        onDismissRequest = onDismiss,
        contentAlignment = Alignment.Center,
        // Centrada en lo que la nube deja libre, como el diálogo: nunca debajo de ella.
        contentModifier = Modifier
            .systemBarsPadding()
            .padding(bottom = CloudReturnClearance),
        // El selector trae su propia cuadrícula de años: alto fijo, sin desplazamiento.
        scrollable = false
    ) {
        DayPickerCard(
            confirmText = confirmText,
            dismissText = dismissText,
            onConfirm = {
                val millis = state.selectedDateMillis
                if (millis != null) onConfirm(millis.toUtcLocalDate()) else onDismiss()
            },
            onDismiss = onDismiss
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

/**
 * La tarjeta del selector, como la del diálogo de Material: mismo ancho, alto máximo,
 * forma y color, y los botones abajo a la derecha. Los botones se miden primero y el
 * calendario se queda con lo que sobra: en una pantalla baja o en horizontal el
 * calendario se recorta, nunca «Aceptar» y «Cancelar» (corrida de la verificadora,
 * 360×640 dp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPickerCard(
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    picker: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .requiredWidth(PICKER_WIDTH)
            .heightIn(max = PICKER_MAX_HEIGHT),
        shape = DatePickerDefaults.shape,
        color = DatePickerDefaults.colors().containerColor,
        tonalElevation = DatePickerDefaults.TonalElevation
    ) {
        Column {
            Box(modifier = Modifier.weight(1f, fill = false)) { picker() }
            Row(modifier = Modifier.align(Alignment.End).padding(end = 6.dp, bottom = 8.dp)) {
                TextButton(onClick = onDismiss) { Text(dismissText) }
                TextButton(onClick = onConfirm) { Text(confirmText) }
            }
        }
    }
}

/** Ancho y alto máximo de la tarjeta del selector de Material 3 (los de su diálogo). */
private val PICKER_WIDTH = 360.dp
private val PICKER_MAX_HEIGHT = 568.dp

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

package com.auroraclima.app.core.security

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureDayPicker(
    initialDay: LocalDate,
    confirmText: String,
    dismissText: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
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
                    if (millis != null) {
                        onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
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

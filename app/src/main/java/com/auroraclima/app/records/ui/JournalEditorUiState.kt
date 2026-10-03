package com.auroraclima.app.records.ui

import androidx.annotation.StringRes
import com.auroraclima.app.records.domain.model.AttachmentPolicy
import com.auroraclima.app.records.domain.model.EntryKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.model.ResolvedKind
import com.auroraclima.app.records.domain.model.UNSPECIFIED_KIND_ID
import com.auroraclima.app.records.domain.model.resolveKind
import com.auroraclima.app.records.domain.model.toResolved
import java.time.LocalDate

/**
 * Estado de la creación (RF-32) y edición (RF-35) de un registro, mockup P19.
 * Los errores de validación son por campo y conviven con lo ya diligenciado:
 * señalar sin perder nada (criterio 2). El error de guardado solo admite un
 * recurso — jamás texto libre de una excepción (P-24, guía §3).
 *
 * Tipos (RF-32 refinado 2026-10-03, cierre P-68): [kinds] son los temas
 * publicados de la Guía; [kindId] el elegido. Si el catálogo no está
 * disponible ([kindsUnavailable]) el selector ofrece una única opción neutra
 * «Sin tipo» para que registrar sea SIEMPRE posible (RNF-08); en edición, un
 * tipo que ya no está publicado se ofrece con su último nombre conocido.
 *
 * Adjuntos (RF-33): la sección «Adjuntos» del mockup P19 con las tres vías de
 * la política (galería, cámara del sistema, archivo). Su error también es solo
 * un recurso genérico; el «máximo alcanzado» es el único mensaje específico —
 * no revela nada y evita un rechazo inexplicable.
 */
data class JournalEditorUiState(
    val occurredOn: LocalDate = LocalDate.now(),
    // Un registro es un hecho ya ocurrido: el selector no permite fechas
    // futuras (P-47a). `today` fija el tope al abrir el editor.
    val today: LocalDate = LocalDate.now(),
    val kinds: List<EntryKind> = emptyList(),
    val isLoadingKinds: Boolean = true,
    val kindsUnavailable: Boolean = false,
    val kindId: String? = null,
    // Nombre del chip elegido al tocarlo (o el guardado, en edición): respaldo
    // de la instantánea si el catálogo aún no había llegado al elegir.
    val kindLabel: String = "",
    // Tipo con el que se guardó el registro en edición: si ya no está
    // publicado, sigue siendo una opción (con su último nombre conocido).
    val storedKindId: String? = null,
    val storedKindLabel: String = "",
    val detail: String = "",
    // Modo edición (RF-35): la pantalla es la misma; cambian el rótulo y el
    // destino del guardado. `isLoading` cubre la carga del registro a editar.
    val isEditing: Boolean = false,
    val isLoading: Boolean = false,
    val isPickingDate: Boolean = false,
    val isSaving: Boolean = false,
    val attachments: List<JournalAttachment> = emptyList(),
    val isImporting: Boolean = false,
    @StringRes val loadErrorRes: Int? = null,
    @StringRes val kindErrorRes: Int? = null,
    @StringRes val detailErrorRes: Int? = null,
    @StringRes val saveErrorRes: Int? = null,
    @StringRes val mediaErrorRes: Int? = null
) {
    /** Botones de adjuntar habilitados: hay cupo y no hay importación en curso. */
    val canAttach: Boolean
        get() = !isImporting && AttachmentPolicy.hasCapacity(attachments.size)

    /**
     * Chips del selector: los publicados en su orden y, al final, el tipo
     * guardado si ya no está publicado o la opción neutra si no hay catálogo.
     */
    val kindOptions: List<ResolvedKind>
        get() {
            val published = kinds.map { it.toResolved() }
            val extra = when {
                storedKindId != null && kinds.none { it.id == storedKindId } ->
                    resolveKind(storedKindId, storedKindLabel, kinds)
                kinds.isEmpty() && kindsUnavailable -> resolveKind(UNSPECIFIED_KIND_ID, "", kinds)
                else -> null
            }
            return published + listOfNotNull(extra)
        }

    /**
     * Instantánea del nombre a guardar: el nombre vivo del catálogo si el tipo
     * está publicado (recoge un cambio de nombre al editar), si no el del chip
     * elegido; `""` para la opción neutra.
     */
    val selectedKindLabel: String
        get() = kinds.firstOrNull { it.id == kindId }?.label ?: kindLabel
}

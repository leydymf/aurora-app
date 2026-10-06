package com.auroraclima.app.questionnaire.domain.repository

import com.auroraclima.app.questionnaire.domain.model.NewSavedOutcome
import com.auroraclima.app.questionnaire.domain.model.SavedOutcome

/**
 * Resultados guardados del cuestionario (RF-31), en el almacén cifrado propio
 * del feature (ADR 2026-08-14). Solo se escribe aquí tras la confirmación
 * expresa de la usuaria (criterio 2): ningún otro camino del flujo persiste.
 * Contrato P-36: toda operación devuelve [Result]; nada cruza como excepción.
 */
interface SavedOutcomeRepository {
    /** Guarda fechado y devuelve el id asignado. */
    suspend fun save(outcome: NewSavedOutcome): Result<Long>

    /** Todos los guardados, del más reciente al más antiguo. */
    suspend fun outcomes(): Result<List<SavedOutcome>>

    suspend fun outcome(id: Long): Result<SavedOutcome?>

    /**
     * Borra el resultado y devuelve su nombre de documento en la nube (vacío si
     * la fila ya no estaba), con el mismo criterio que el historial.
     */
    suspend fun delete(id: Long): Result<String>
}

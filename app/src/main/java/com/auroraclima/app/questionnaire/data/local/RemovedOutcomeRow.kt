package com.auroraclima.app.questionnaire.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Constancia de un resultado que la usuaria eliminó aquí y cuyo borrado remoto
 * puede seguir en la cola durable (v2, ADR 2026-10-06). Mismo papel que la
 * tabla hermana del historial: impide que la reconciliación vuelva a bajar lo
 * que se acaba de eliminar sin conexión.
 */
@Entity(tableName = "removed")
data class RemovedOutcomeRow(
    @PrimaryKey @ColumnInfo(name = "doc_id") val docId: String,
    @ColumnInfo(name = "at") val at: Long
)

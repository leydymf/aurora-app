package com.auroraclima.app.questionnaire.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fila de la tabla `outcomes` del almacén cifrado del cuestionario (RF-31).
 * Tabla y columnas con léxico neutro (RNF-05, mismo criterio que `entries`):
 * Room copia los nombres como literales SQL al DEX.
 *
 * `savedAt` (epochMillis) ordena el listado y desempata con `id`. `rank` es el
 * orden del nivel (elige el par de severidad al pintar), `label` el título
 * validado del nivel y `tags` las etiquetas de tipos reconocidos serializadas
 * como arreglo JSON — snapshots deliberados (RNF-04/ADR 2026-08-14): nunca
 * respuestas ni puntajes.
 *
 * `syncedGeneration` replica la constancia de sync del historial (RF-39):
 * 0 = pendiente de subir; distinto de la generación vigente = re-subir.
 *
 * `docId` (v2, ADR 2026-10-06) es el nombre del documento en la nube, por el
 * mismo motivo que en `entries`: con el id local, dos dispositivos de la misma
 * cuenta podían generar el mismo nombre para resultados distintos. Los
 * guardados anteriores conservan su id numérico, que es con el que ya están
 * publicados.
 */
@Entity(tableName = "outcomes")
data class OutcomeRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
    val rank: Int,
    val label: String,
    val tags: String,
    @ColumnInfo(name = "synced_gen", defaultValue = "0") val syncedGeneration: Long = 0,
    @ColumnInfo(name = "doc_id", defaultValue = "") val docId: String = "",
    /** Generación en que una lectura del servidor lo vio (0 = nunca). */
    @ColumnInfo(name = "cloud_seen", defaultValue = "0") val cloudSeenGeneration: Long = 0
)

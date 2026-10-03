package com.auroraclima.app.records.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fila de la tabla `entries` de la BD cifrada central. Tabla y columnas con
 * léxico neutro de diario (RNF-05, P-36): Room los copia como literales SQL al
 * DEX, donde quedan legibles aun con R8.
 *
 * `day` es el epochDay de la fecha del hecho; `createdAt` desempata el orden
 * cronológico inverso entre registros del mismo día (RF-34 criterio 1).
 *
 * `syncedGeneration` (v3, RF-39) es la generación de la DEK con la que este
 * registro subió por última vez: 0 = pendiente de subir. Un valor distinto de
 * la generación vigente marca el registro para la re-subida del reconciliador
 * — así la migración al sobre nuevo, sus interrupciones y los push perdidos
 * convergen en un único mecanismo. Nombre de columna neutro, como el resto.
 *
 * `kind` (v5, ADR 2026-10-03) guarda el id opaco del tema publicado (`""` =
 * sin tipo) y `label` la instantánea de su nombre al guardar: si el tema deja
 * de publicarse, el registro conserva su último nombre conocido.
 */
@Entity(tableName = "entries")
data class JournalEntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val kind: String,
    val detail: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "synced_gen", defaultValue = "0") val syncedGeneration: Long = 0,
    @ColumnInfo(defaultValue = "") val label: String = ""
)

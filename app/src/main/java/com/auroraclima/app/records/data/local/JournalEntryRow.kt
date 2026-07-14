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
 */
@Entity(tableName = "entries")
data class JournalEntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val kind: String,
    val detail: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

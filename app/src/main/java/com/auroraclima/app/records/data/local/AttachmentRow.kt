package com.auroraclima.app.records.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fila de la tabla `attachments` de la BD cifrada central (RF-33): el mapeo
 * registro ↔ archivo del almacén vive CIFRADO — en el directorio de adjuntos
 * solo se ven blobs con nombres opacos, sin relación visible entre sí ni con
 * registro alguno. Léxico neutro de adjuntos, mismo criterio que `entries`
 * (RNF-05, P-36).
 *
 * `owner` es el id del registro dueño; `0` marca un adjunto EN PREPARACIÓN de
 * un registro aún no guardado (el guardado lo reclama; el barrido de huérfanos
 * limpia los abandonados). Sin FK a `entries` a propósito: ese estado 0 no
 * tiene fila padre. `ref` es el nombre opaco del archivo cifrado y `kind` su
 * tipo MIME.
 */
@Entity(tableName = "attachments", indices = [Index("owner")])
data class AttachmentRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val owner: Long,
    val ref: String,
    val kind: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

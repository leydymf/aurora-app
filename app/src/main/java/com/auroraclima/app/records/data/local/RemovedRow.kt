package com.auroraclima.app.records.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Constancia de algo que la usuaria eliminó aquí y cuyo borrado remoto puede
 * seguir en la cola durable del SDK (v6, ADR 2026-10-06).
 *
 * Sin esta tabla la reconciliación hacia abajo resucitaría lo eliminado sin
 * conexión: el documento aún está en la nube porque el `delete` no ha salido, y
 * la fila local ya no está, así que parecería «creado en otro dispositivo».
 * Mientras exista la constancia, ese documento se ignora al bajar; cuando la
 * nube confirma que ya no está, la constancia se borra.
 *
 * `scope` distingue registros (`e`) de adjuntos (`m`), que comparten esta base
 * de datos. Léxico neutro y de una letra: Room copia tabla y columnas como
 * literales SQL al DEX (RNF-05, mismo criterio que el resto del esquema).
 */
@Entity(tableName = "removed", primaryKeys = ["doc_id", "scope"])
data class RemovedRow(
    @ColumnInfo(name = "doc_id") val docId: String,
    val scope: String,
    @ColumnInfo(name = "at") val at: Long
) {
    companion object {
        const val SCOPE_ENTRY = "e"
        const val SCOPE_MEDIA = "m"
    }
}

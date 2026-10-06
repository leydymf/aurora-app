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
 *
 * `docId` y `updatedAt` (v6, ADR 2026-10-06, reconciliación con la nube):
 * - `docId` es el nombre del documento en la nube. Los registros creados desde
 *   esta versión reciben 32 hex aleatorios ([OpaqueIdSource]); los anteriores
 *   conservan su id numérico como texto, así que nada de lo ya publicado se
 *   migra. Separarlo del id local es lo que evita que dos dispositivos de la
 *   misma cuenta generen el mismo nombre para registros distintos.
 * - `updatedAt` es la última edición, y es lo que decide un empate entre dos
 *   dispositivos. Viaja DENTRO del payload cifrado, nunca como campo legible.
 * - `pending` marca que la fila tiene cambios locales sin subir. Antes esto se
 *   expresaba poniendo `syncedGeneration` en 0, pero entonces un registro
 *   editado aquí era indistinguible de uno que NUNCA subió — y esa diferencia
 *   es justo la que decide el conflicto «eliminado en el otro dispositivo»
 *   contra «editado aquí sin subir». Ahora `syncedGeneration` conserva con qué
 *   generación subió por última vez y `pending` dice si hay algo nuevo que
 *   enviar.
 * - `cloudSeenGeneration` es la generación de la DEK en la que una lectura del
 *   SERVIDOR vio este documento (0 = nunca). Es lo único que autoriza a
 *   borrarlo cuando deja de estar: subir no sirve como prueba porque solo
 *   ENCOLA (RF-38), y una lectura hecha antes de que la cola llegue no vería un
 *   registro recién creado. Lleva la generación y no un simple sí/no porque
 *   reiniciar el respaldo (RF-39) purga el servidor y sube la generación: con
 *   un booleano, la marca vieja habría autorizado a borrar TODO el historial en
 *   el primer reingreso posterior (auditoría 2026-10-06).
 */
@Entity(tableName = "entries")
data class JournalEntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val kind: String,
    val detail: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "synced_gen", defaultValue = "0") val syncedGeneration: Long = 0,
    @ColumnInfo(defaultValue = "") val label: String = "",
    @ColumnInfo(name = "doc_id", defaultValue = "") val docId: String = "",
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(name = "pending", defaultValue = "0") val pending: Boolean = false,
    @ColumnInfo(name = "cloud_seen", defaultValue = "0") val cloudSeenGeneration: Long = 0
)

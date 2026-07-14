package com.auroraclima.app.records.data

import com.auroraclima.app.records.data.local.JournalDatabase

/**
 * Único punto de construcción de la BD del historial. En producción delega en
 * `EncryptedDatabaseFactory` (ver `RecordsDataModule`); [open] PUEDE LANZAR
 * (blob de passphrase corrupto, llave invalidada — ADR D6) y hace I/O, así que
 * solo se invoca perezosamente desde el repositorio, en un dispatcher de I/O y
 * dentro de su traducción a `Result` (P-36). Como interfaz, las pruebas de la
 * JVM inyectan una BD Room plana sin cargar SQLCipher.
 */
fun interface JournalDatabaseSource {
    fun open(): JournalDatabase
}

package com.auroraclima.app.questionnaire.data

import com.auroraclima.app.questionnaire.data.local.GuidanceDatabase

/**
 * Único punto de construcción del almacén del cuestionario (RF-31). En
 * producción delega en `EncryptedDatabaseFactory` (ver `QuestionnaireDataModule`);
 * [open] PUEDE LANZAR y hace I/O, así que solo se invoca perezosamente desde
 * [GuidanceStoreAccess], en un dispatcher de I/O y traducido a `Result` (P-36).
 * Como interfaz, las pruebas de la JVM inyectan una BD Room plana sin SQLCipher.
 */
fun interface GuidanceDatabaseSource {
    fun open(): GuidanceDatabase
}

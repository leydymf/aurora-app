package com.auroraclima.app.records.ui

import com.auroraclima.app.records.domain.model.ExportDocument
import com.auroraclima.app.records.domain.repository.JournalExportRepository

/** Fake de la salida del PDF (RF-45) para el ViewModel de exportación (guía §5). */
class FakeJournalExportRepository : JournalExportRepository {
    val exported = mutableListOf<Triple<String, ExportDocument, String>>()
    val staged = mutableListOf<Pair<ExportDocument, String>>()
    val discarded = mutableListOf<String>()
    var clearCalls = 0
        private set

    var exportResult: Result<Unit> = Result.success(Unit)

    /** Simula un Error (no Exception) a mitad de la escritura, p. ej. OutOfMemoryError. */
    var throwOnExport: Throwable? = null
    var stageResult: Result<String> = Result.success("content://com.auroraclima.app.files/d/documento_2026-10-03.pdf")

    override suspend fun exportTo(targetUri: String, document: ExportDocument, password: String): Result<Unit> {
        throwOnExport?.let { throw it }
        exportResult.onSuccess { exported += Triple(targetUri, document, password) }
        return exportResult
    }

    override suspend fun stage(document: ExportDocument, password: String): Result<String> {
        stageResult.onSuccess { staged += document to password }
        return stageResult
    }

    override suspend fun discard(targetUri: String): Result<Unit> {
        discarded += targetUri
        return Result.success(Unit)
    }

    override suspend fun clearStaged(): Result<Unit> {
        clearCalls++
        return Result.success(Unit)
    }
}

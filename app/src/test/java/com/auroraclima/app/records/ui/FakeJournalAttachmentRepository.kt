package com.auroraclima.app.records.ui

import com.auroraclima.app.records.domain.model.AttachmentBytes
import com.auroraclima.app.records.domain.model.AttachmentContent
import com.auroraclima.app.records.domain.model.AttachmentKind
import com.auroraclima.app.records.domain.model.JournalAttachment
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository

/** Fake de adjuntos (RF-33) para ViewModels y casos de uso (guía §5). */
open class FakeJournalAttachmentRepository : JournalAttachmentRepository {
    val imported = mutableListOf<Pair<String, Long>>()
    val capturesImported = mutableListOf<Long>()
    val claimed = mutableListOf<Pair<List<Long>, Long>>()
    val deleted = mutableListOf<Long>()
    val deletedOwners = mutableListOf<Long>()
    var sweepCalls = 0
        private set
    var discardCalls = 0
        private set

    var importResult: Result<JournalAttachment>? = null
    var beginCaptureResult: Result<String> = Result.success("content://capture/1")
    var attachmentsResult: Result<List<JournalAttachment>> = Result.success(emptyList())
    var countsResult: Result<Map<Long, Int>> = Result.success(emptyMap())
    var contentResult: Result<AttachmentContent?> = Result.success(null)

    /** Lo que devuelve `exportable` por id; sin entrada → null (adjunto inexistente). */
    val exportableById = mutableMapOf<Long, AttachmentBytes>()
    var exportableResult: Result<AttachmentBytes?>? = null
    val exportableIds = mutableListOf<Long>()
    var claimResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var deleteForOwnerResult: Result<Unit> = Result.success(Unit)
    private var nextId = 1L

    override suspend fun importImage(uri: String, owner: Long): Result<JournalAttachment> {
        importResult?.let { return it }
        imported += uri to owner
        return Result.success(JournalAttachment(nextId++, AttachmentKind.IMAGE))
    }

    override suspend fun importDocument(uri: String, owner: Long): Result<JournalAttachment> {
        importResult?.let { return it }
        imported += uri to owner
        return Result.success(JournalAttachment(nextId++, AttachmentKind.DOCUMENT))
    }

    override suspend fun beginCapture(): Result<String> = beginCaptureResult

    override suspend fun importCapture(owner: Long): Result<JournalAttachment> {
        importResult?.let { return it }
        capturesImported += owner
        return Result.success(JournalAttachment(nextId++, AttachmentKind.IMAGE))
    }

    override suspend fun discardCapture() {
        discardCalls++
    }

    override suspend fun attachments(owner: Long): Result<List<JournalAttachment>> = attachmentsResult

    override suspend fun counts(): Result<Map<Long, Int>> = countsResult

    open override suspend fun content(id: Long): Result<AttachmentContent?> = contentResult

    override suspend fun exportable(id: Long): Result<AttachmentBytes?> {
        exportableIds += id
        exportableResult?.let { return it }
        return Result.success(exportableById[id])
    }

    override suspend fun claim(ids: List<Long>, owner: Long): Result<Unit> {
        claimResult.onSuccess { claimed += ids to owner }
        return claimResult
    }

    override suspend fun delete(id: Long): Result<Unit> {
        deleteResult.onSuccess { deleted += id }
        return deleteResult
    }

    override suspend fun deleteForOwner(owner: Long): Result<Unit> {
        deleteForOwnerResult.onSuccess { deletedOwners += owner }
        return deleteForOwnerResult
    }

    override suspend fun sweepOrphans(): Result<Unit> {
        sweepCalls++
        return Result.success(Unit)
    }
}

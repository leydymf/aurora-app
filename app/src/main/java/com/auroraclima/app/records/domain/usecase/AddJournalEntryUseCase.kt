package com.auroraclima.app.records.domain.usecase

import com.auroraclima.app.records.domain.model.NewJournalEntry
import com.auroraclima.app.records.domain.repository.JournalAttachmentRepository
import com.auroraclima.app.records.domain.repository.JournalMediaSyncRepository
import com.auroraclima.app.records.domain.repository.JournalRepository
import com.auroraclima.app.records.domain.repository.JournalSyncRepository
import javax.inject.Inject

/**
 * Guarda un registro (RF-32) y lo sincroniza cifrado con la nube (RF-37).
 *
 * Caso de uso obligatorio por combinar dos repositorios (§3). La fuente de verdad
 * es SIEMPRE el guardado local: solo su [Result] llega a la UI. La subida es de
 * mejor esfuerzo y su resultado se descarta a propósito — un fallo de red no
 * revierte lo guardado ni produce un error visible (§3, camuflaje). La escritura
 * queda en la cola durable de Firestore y sube sola al reconectar (RF-38).
 *
 * RF-33: los adjuntos importados durante la creación quedan en preparación y el
 * guardado los reclama con el id recién asignado. El reclamo es de mejor
 * esfuerzo, como la sync: si fallara (improbable — la BD acaba de escribir),
 * los adjuntos quedan huérfanos y el barrido los limpia; el registro guardado
 * jamás se revierte por ellos. RF-39 (cambio de política D0 §5.3): las
 * evidencias reclamadas también suben, cifradas con la DEK y troceadas — mejor
 * esfuerzo igual que el registro; lo que no suba lo retoma el reconciliador.
 */
class AddJournalEntryUseCase @Inject constructor(
    private val journalRepository: JournalRepository,
    private val attachmentRepository: JournalAttachmentRepository,
    private val syncRepository: JournalSyncRepository,
    private val mediaSyncRepository: JournalMediaSyncRepository
) {
    suspend operator fun invoke(entry: NewJournalEntry, attachmentIds: List<Long> = emptyList()): Result<Unit> {
        val id = journalRepository.add(entry).getOrElse { return Result.failure(it) }
        if (attachmentIds.isNotEmpty()) attachmentRepository.claim(attachmentIds, id)
        // Mejor esfuerzo: el registro ya está a salvo localmente. Su fallo se
        // ignora — la fila queda pendiente y el reconciliador la retoma (RF-39).
        syncRepository.push(id)
        attachmentIds.forEach { attachmentId -> mediaSyncRepository.push(attachmentId) }
        return Result.success(Unit)
    }
}

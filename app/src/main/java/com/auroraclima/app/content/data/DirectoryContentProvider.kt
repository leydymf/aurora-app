package com.auroraclima.app.content.data

import com.auroraclima.app.content.domain.model.Agency
import com.auroraclima.app.content.domain.model.ContactLine
import com.auroraclima.app.content.domain.repository.ContentRepository
import com.auroraclima.app.core.directory.DirectoryAccess
import com.auroraclima.app.core.directory.DirectoryEntity
import com.auroraclima.app.core.directory.DirectoryLine
import com.auroraclima.app.core.directory.DirectoryProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [DirectoryProvider] con los datos de content: es content,
 * dueño de líneas y entidades, quien los expone al cuestionario a través del
 * contrato de `core` (§2). Resuelve cada id contra las colecciones vivas y
 * omite los que no estén publicados (tolerancia RF-42) — así una entidad aún
 * retenida (a06, a26) no aparece hasta confirmarse. Todo fallo se traduce a
 * [Result] sin que la excepción cruce a la UI (§3).
 */
@Singleton
class DirectoryContentProvider @Inject constructor(
    private val repository: ContentRepository
) : DirectoryProvider {
    override suspend fun resolve(lineIds: List<String>, entityIds: List<String>): Result<DirectoryAccess> {
        val linesById = repository.getContactLines().getOrElse { return Result.failure(it) }.associateBy { it.id }
        val entitiesById = repository.getRoutes().getOrElse { return Result.failure(it) }
            .flatMap { it.agencies }
            .associateBy { it.id }
        return Result.success(
            DirectoryAccess(
                lines = lineIds.mapNotNull { linesById[it]?.toDirectoryLine() },
                entities = entityIds.mapNotNull { entitiesById[it]?.toDirectoryEntity() }
            )
        )
    }
}

private fun ContactLine.toDirectoryLine() = DirectoryLine(id, name, number, description, hours)

private fun Agency.toDirectoryEntity() = DirectoryEntity(id, name, role, whenToUse, phones, address, hours)

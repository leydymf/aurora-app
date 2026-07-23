package com.auroraclima.app.core.directory

/**
 * Contrato para resolver los accesos directos del resultado del cuestionario
 * (RF-29, documento validado §6): líneas de atención y entidades por id.
 *
 * `questionnaire` solo conoce esta interfaz; la implementación vive en
 * `content`, dueño de los datos (inversión de dependencias: las features no se
 * importan entre sí, §2 de ARQUITECTURA.md — mismo patrón que SearchQueryGate).
 * Los ids sin resolver se omiten (tolerancia RF-42): una entidad aún no
 * publicada simplemente no aparece. Nombres neutros de directorio de contactos
 * (RNF-05).
 */
interface DirectoryProvider {
    /**
     * Resuelve [lineIds] y [entityIds] a su información de contacto, cada lista
     * en el orden pedido; los ids sin resolver se descartan. Devuelve [Result];
     * la excepción no cruza a la UI (§3).
     */
    suspend fun resolve(lineIds: List<String>, entityIds: List<String>): Result<DirectoryAccess>
}

data class DirectoryAccess(
    val lines: List<DirectoryLine>,
    val entities: List<DirectoryEntity>
)

data class DirectoryLine(
    val id: String,
    val name: String,
    val number: String,
    val description: String,
    val hours: String?
)

data class DirectoryEntity(
    val id: String,
    val name: String,
    val role: String,
    val whenToUse: String,
    val phones: List<String>,
    val address: String?,
    val hours: String?
)

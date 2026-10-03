package com.auroraclima.app.records.domain.repository

import com.auroraclima.app.records.domain.model.ExportDocument
import java.io.OutputStream

/**
 * Convierte un [ExportDocument] en un PDF protegido con [password] y lo vierte
 * en [sink] (RF-45 criterios 3 y 4). Contrato de la librería de PDF para que
 * el repositorio y los casos de uso se prueben en la JVM con un escritor
 * falso; la implementación real vive en data (ADR RF-45).
 */
fun interface ExportDocumentWriter {
    fun write(document: ExportDocument, password: String, sink: OutputStream)
}

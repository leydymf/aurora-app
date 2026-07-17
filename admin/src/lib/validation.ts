// Validaciones compartidas del panel. La regla general: guardar un BORRADOR
// es libre (es su naturaleza estar incompleto); PUBLICAR exige pasar la
// validación dura del recurso, porque la app confía en la estructura.

/** Un problema que impide publicar; `field` ubica el campo en el editor. */
export interface ValidationIssue {
    readonly field: string
    readonly message: string
}

/**
 * Compuerta heredada de la siembra (tools/seed/seed.mjs, auditoría D4 M-2):
 * un teléfono o dirección sin verificar es daño potencial real para una
 * usuaria, así que nada que contenga el marcador puede publicarse.
 */
export const DRAFT_MARKER = /\[POR CONFIRMAR/i

/** true si algún texto del documento contiene el marcador [POR CONFIRMAR…]. */
export function containsDraftMarker(data: unknown): boolean {
    return DRAFT_MARKER.test(JSON.stringify(data))
}

/** true si el texto no tiene contenido real (vacío o solo espacios). */
export function isBlank(value: string): boolean {
    return value.trim().length === 0
}

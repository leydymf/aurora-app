// Mensajes de error para la administradora: prefijo en español + el detalle
// técnico del SDK (útil para diagnosticar reglas o red, sin registrar nada).

export function describeError(prefix: string, error: unknown): string {
    const detail = error instanceof Error && error.message ? ` (${error.message})` : ''
    return `${prefix}${detail}`
}

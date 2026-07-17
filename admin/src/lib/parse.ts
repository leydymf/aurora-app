// Lectura tolerante de documentos de Firestore: el SDK entrega mapas sin tipar
// y aquí se estrechan a los tipos del esquema con valores por defecto seguros,
// espejo del patrón de DTOs tolerantes de la app Android (ContentDtos.kt).
// La validación DURA ocurre al publicar, nunca al leer.

export function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function asRecord(value: unknown): Record<string, unknown> {
    return isRecord(value) ? value : {}
}

export function str(record: Record<string, unknown>, key: string): string {
    const value = record[key]
    return typeof value === 'string' ? value : ''
}

export function strOrNull(record: Record<string, unknown>, key: string): string | null {
    const value = record[key]
    return typeof value === 'string' ? value : null
}

export function num(record: Record<string, unknown>, key: string, fallback = 0): number {
    const value = record[key]
    return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

export function strList(record: Record<string, unknown>, key: string): string[] {
    const value = record[key]
    if (!Array.isArray(value)) return []
    return value.filter((item): item is string => typeof item === 'string')
}

export function recordList(
    record: Record<string, unknown>,
    key: string,
): Record<string, unknown>[] {
    const value = record[key]
    if (!Array.isArray(value)) return []
    return value.map(asRecord)
}

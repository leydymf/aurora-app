// Ids de documento opacos (t01, a09, …): no describen el contenido porque
// viajan en el tráfico y quedan en la caché local del dispositivo (auditoría
// D4 H-1, RNF-05). El panel los genera y son inmutables después de crear.

/** Devuelve el siguiente id opaco libre: número más alto existente + 1. */
export function nextOpaqueId(prefix: string, existingIds: Iterable<string>, padLength = 2): string {
    const pattern = new RegExp(`^${prefix}(\\d+)$`)
    let highest = 0
    for (const id of existingIds) {
        const digits = pattern.exec(id)?.[1]
        if (digits === undefined) continue
        const value = Number.parseInt(digits, 10)
        if (value > highest) highest = value
    }
    return `${prefix}${String(highest + 1).padStart(padLength, '0')}`
}

// Estado local de un recurso: carga combinada vivo + borradores y recarga
// tras cada acción. No es estado global (regla de admin/CLAUDE.md): cada
// página monta el suyo.
import { useCallback, useEffect, useState } from 'react'
import { loadEntries, type ResourceCollection, type ResourceEntry } from './drafts'
import { describeError } from './errors'

/**
 * `fromRaw` debe ser una función de módulo (referencia estable): es
 * dependencia del efecto de carga y una función inline recargaría en bucle.
 */
export function useEntries<T>(
    resource: ResourceCollection,
    fromRaw: (raw: Record<string, unknown>) => T,
) {
    const [entries, setEntries] = useState<ResourceEntry<T>[] | null>(null)
    const [error, setError] = useState<string | null>(null)

    const reload = useCallback(async () => {
        try {
            setEntries(await loadEntries(resource, fromRaw))
            setError(null)
        } catch (err) {
            setError(describeError('No se pudo cargar el recurso.', err))
        }
    }, [resource, fromRaw])

    useEffect(() => {
        void reload()
    }, [reload])

    return { entries, error, setError, reload }
}

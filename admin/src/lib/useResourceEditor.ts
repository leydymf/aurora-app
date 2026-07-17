// Máquina de estados del editor de un recurso: selección, copia de trabajo,
// guardar borrador, publicar y eliminar en dos pasos. La VALIDACIÓN es de cada
// recurso: publicar exige recibir la lista de problemas y se niega si no está
// vacía (la app confía en la estructura publicada).
import { useState } from 'react'
import {
    deleteEntry,
    publishEntry,
    saveDraft,
    type ResourceCollection,
    type ResourceEntry,
} from './drafts'
import { describeError } from './errors'
import type { ValidationIssue } from './validation'

interface UseResourceEditorOptions<T extends Record<string, unknown>> {
    readonly resource: ResourceCollection
    readonly entries: ResourceEntry<T>[] | null
    readonly reload: () => Promise<void>
    readonly setError: (message: string | null) => void
    /** Limpieza previa a guardar (recortar espacios, '' → null, …). */
    readonly normalize: (data: T) => T
}

export function useResourceEditor<T extends Record<string, unknown>>({
    resource,
    entries,
    reload,
    setError,
    normalize,
}: UseResourceEditorOptions<T>) {
    const [selectedId, setSelectedId] = useState<string | null>(null)
    const [editing, setEditing] = useState<T | null>(null)
    const [isNew, setIsNew] = useState(false)
    const [busy, setBusy] = useState(false)
    const [confirmingDelete, setConfirmingDelete] = useState(false)

    const selected = isNew ? null : (entries?.find((entry) => entry.docId === selectedId) ?? null)
    const isDirty =
        editing !== null && (isNew || JSON.stringify(editing) !== JSON.stringify(selected?.data))

    function select(entry: ResourceEntry<T>) {
        setSelectedId(entry.docId)
        setEditing(structuredClone(entry.data))
        setIsNew(false)
        setConfirmingDelete(false)
    }

    function startNew(docId: string, data: T) {
        setSelectedId(docId)
        setEditing(data)
        setIsNew(true)
        setConfirmingDelete(false)
    }

    function cancelNew() {
        setSelectedId(null)
        setEditing(null)
        setIsNew(false)
    }

    async function save() {
        if (!editing || selectedId === null) return
        setBusy(true)
        try {
            const normalized = normalize(editing)
            await saveDraft(resource, selectedId, normalized, selected?.publishedAt ?? null)
            setEditing(normalized)
            setIsNew(false)
            await reload()
        } catch (err) {
            setError(describeError('No se pudo guardar el borrador.', err))
        } finally {
            setBusy(false)
        }
    }

    async function publish(issues: readonly ValidationIssue[]) {
        if (!selected || selected.status !== 'borrador' || isDirty || issues.length > 0) return
        setBusy(true)
        try {
            await publishEntry(resource, selected.docId, selected.data)
            await reload()
        } catch (err) {
            setError(describeError('No se pudo publicar.', err))
        } finally {
            setBusy(false)
        }
    }

    /** Dos pasos: el primer clic pide confirmación, el segundo elimina. */
    async function remove() {
        if (!selected) return
        if (!confirmingDelete) {
            setConfirmingDelete(true)
            return
        }
        setBusy(true)
        try {
            await deleteEntry(resource, selected.docId)
            setSelectedId(null)
            setEditing(null)
            setConfirmingDelete(false)
            await reload()
        } catch (err) {
            setError(describeError('No se pudo eliminar.', err))
        } finally {
            setBusy(false)
        }
    }

    return {
        selectedId,
        selected,
        editing,
        setEditing,
        isNew,
        isDirty,
        busy,
        confirmingDelete,
        select,
        startNew,
        cancelNew,
        save,
        publish,
        remove,
    }
}

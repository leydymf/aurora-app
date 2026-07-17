// Botonera estándar del editor de un recurso: guardar borrador, publicar,
// eliminar en dos pasos (o cancelar un alta nueva) y sus pistas de estado.
interface EditorActionsProps {
    readonly busy: boolean
    readonly isDirty: boolean
    readonly canPublish: boolean
    readonly isNew: boolean
    readonly confirmingDelete: boolean
    /** Si viene un motivo, eliminar queda deshabilitado y se muestra por qué. */
    readonly deleteBlockedReason?: string | null
    readonly onSave: () => void
    readonly onPublish: () => void
    readonly onDelete: () => void
    readonly onCancelNew: () => void
}

export function EditorActions({
    busy,
    isDirty,
    canPublish,
    isNew,
    confirmingDelete,
    deleteBlockedReason = null,
    onSave,
    onPublish,
    onDelete,
    onCancelNew,
}: EditorActionsProps) {
    return (
        <>
            <div className="editor__actions">
                <button type="button" disabled={busy || !isDirty} onClick={onSave}>
                    Guardar borrador
                </button>
                <button
                    type="button"
                    disabled={busy || !canPublish}
                    title={isDirty ? 'Guarda el borrador antes de publicar.' : undefined}
                    onClick={onPublish}
                >
                    Publicar
                </button>
                {isNew ? (
                    <button
                        type="button"
                        className="btn--ghost"
                        disabled={busy}
                        onClick={onCancelNew}
                    >
                        Cancelar
                    </button>
                ) : (
                    <button
                        type="button"
                        className="btn--danger"
                        disabled={busy || deleteBlockedReason !== null}
                        onClick={onDelete}
                    >
                        {confirmingDelete ? '¿Eliminar definitivamente?' : 'Eliminar'}
                    </button>
                )}
            </div>
            {deleteBlockedReason !== null && <p className="muted">{deleteBlockedReason}</p>}
        </>
    )
}

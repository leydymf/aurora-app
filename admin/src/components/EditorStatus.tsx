// Estado del recurso seleccionado: publicado/borrador, fechas y la lista de
// problemas que impiden publicar (la validación dura de cada recurso).
import type { ResourceEntry } from '../lib/drafts'
import type { ValidationIssue } from '../lib/validation'

interface EditorStatusProps {
    readonly selected: ResourceEntry<unknown> | null
    readonly isDirty: boolean
    readonly issues: readonly ValidationIssue[]
}

export function EditorStatus({ selected, isDirty, issues }: EditorStatusProps) {
    return (
        <>
            {selected && (
                <p className="editor__meta">
                    {selected.status === 'publicado'
                        ? 'Publicado en la app'
                        : 'Borrador (la app no lo ve todavía)'}
                    {selected.publishedAt &&
                        ` · última publicación: ${selected.publishedAt.toLocaleString('es-CO')}`}
                    {selected.updatedAt &&
                        ` · guardado: ${selected.updatedAt.toLocaleString('es-CO')}`}
                </p>
            )}
            {isDirty && <p className="muted">Hay cambios sin guardar.</p>}
            {selected !== null &&
                selected.status === 'borrador' &&
                !isDirty &&
                issues.length > 0 && (
                    <div className="issues">
                        <p className="issues__title">Para publicar falta:</p>
                        <ul>
                            {issues.map((issue, index) => (
                                <li key={index}>{issue.message}</li>
                            ))}
                        </ul>
                    </div>
                )}
        </>
    )
}

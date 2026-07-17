// Chip de estado del flujo borrador → publicado (RF-44).
import type { DraftStatus } from '../lib/drafts'

export function StatusBadge({ status }: { readonly status: DraftStatus }) {
    return (
        <span className={`badge badge--${status}`}>
            {status === 'publicado' ? 'Publicado' : 'Borrador'}
        </span>
    )
}

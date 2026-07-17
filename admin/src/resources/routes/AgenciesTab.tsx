// Pestaña de entidades (RF-42). Eliminar una entidad queda BLOQUEADO mientras
// una ruta (viva o en borrador) la referencie: aunque la app tolera
// referencias huérfanas (las omite, ContentMappers.kt), el panel no las
// produce — criterio 2 de RF-42.
import { useMemo } from 'react'
import { EditorActions } from '../../components/EditorActions'
import { EditorStatus } from '../../components/EditorStatus'
import { StatusBadge } from '../../components/StatusBadge'
import { StringListField } from '../../components/StringListField'
import { nextOpaqueId } from '../../lib/ids'
import { isValidColombianPhone } from '../../lib/phone'
import { useEntries } from '../../lib/useEntries'
import { useResourceEditor } from '../../lib/useResourceEditor'
import {
    agencyFromRaw,
    emptyAgency,
    normalizeAgency,
    routeFromRaw,
    validateAgency,
} from './schemas'

export function AgenciesTab() {
    const agencies = useEntries('agencies', agencyFromRaw)
    const routes = useEntries('routes', routeFromRaw)
    const editor = useResourceEditor({
        resource: 'agencies',
        entries: agencies.entries,
        reload: agencies.reload,
        setError: agencies.setError,
        normalize: normalizeAgency,
    })
    const { editing, selected, selectedId } = editor
    const error = agencies.error ?? routes.error

    const sorted = useMemo(
        () =>
            agencies.entries
                ? [...agencies.entries].sort((a, b) => a.docId.localeCompare(b.docId))
                : [],
        [agencies.entries],
    )

    const publishIssues = useMemo(
        () =>
            selected !== null && selected.status === 'borrador'
                ? validateAgency(selected.data)
                : [],
        [selected],
    )
    const canPublish =
        selected !== null &&
        selected.status === 'borrador' &&
        !editor.isDirty &&
        publishIssues.length === 0

    // Bloqueo de eliminación: rutas que referencian la entidad seleccionada,
    // tanto en su borrador como en su versión publicada.
    const referencingRoutes = useMemo(() => {
        if (!selected) return []
        return (routes.entries ?? []).filter(
            (route) =>
                route.data.agencyIds.includes(selected.docId) ||
                (route.liveData?.agencyIds.includes(selected.docId) ?? false),
        )
    }, [routes.entries, selected])

    // Mientras las rutas cargan NO se permite eliminar: hacerlo con la lista
    // incompleta podría romper una referencia sin detectarla.
    const deleteBlockedReason =
        routes.entries === null
            ? 'Verificando qué rutas referencian esta entidad…'
            : referencingRoutes.length > 0
              ? `No se puede eliminar: la referencian ${referencingRoutes
                    .map((route) => `«${route.data.title || route.docId}»`)
                    .join(', ')}. Quítala primero de esas rutas.`
              : null

    const hasInvalidPhone =
        editing !== null &&
        editing.phones.some((phone) => phone.trim() !== '' && !isValidColombianPhone(phone))

    function startNew() {
        if (!agencies.entries) return
        const docId = nextOpaqueId(
            'a',
            agencies.entries.map((entry) => entry.docId),
        )
        editor.startNew(docId, emptyAgency())
    }

    return (
        <>
            {error !== null && (
                <p className="alert" role="alert">
                    {error}
                </p>
            )}
            <div className="manager">
                <aside className="panel list">
                    <button
                        type="button"
                        disabled={!agencies.entries || editor.busy}
                        onClick={() => {
                            startNew()
                        }}
                    >
                        + Nueva entidad
                    </button>
                    {!agencies.entries && <p className="muted">Cargando…</p>}
                    {agencies.entries && sorted.length === 0 && !editor.isNew && (
                        <p className="muted">Sin entidades todavía.</p>
                    )}
                    {sorted.map((entry) => (
                        <button
                            key={entry.docId}
                            type="button"
                            className={
                                'list__item' +
                                (entry.docId === selectedId && !editor.isNew
                                    ? ' list__item--active'
                                    : '')
                            }
                            onClick={() => {
                                editor.select(entry)
                            }}
                        >
                            <span className="list__title">{entry.data.name || entry.docId}</span>
                            <StatusBadge status={entry.status} />
                        </button>
                    ))}
                    {editor.isNew && editing && (
                        <div className="list__item list__item--active">
                            <span className="list__title">{editing.name || '(nueva)'}</span>
                            <StatusBadge status="borrador" />
                        </div>
                    )}
                </aside>

                {editing ? (
                    <div className="panel editor">
                        <div className="editor__grid">
                            <label className="field">
                                <span>Id (fijo)</span>
                                <input value={selectedId ?? ''} readOnly disabled />
                            </label>
                        </div>
                        <label className="field">
                            <span>Nombre</span>
                            <input
                                value={editing.name}
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, name: e.target.value })
                                }}
                            />
                        </label>
                        <label className="field">
                            <span>Qué hace</span>
                            <textarea
                                rows={4}
                                value={editing.role}
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, role: e.target.value })
                                }}
                            />
                        </label>
                        <label className="field">
                            <span>Cuándo acudir</span>
                            <textarea
                                rows={3}
                                value={editing.whenToUse}
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, whenToUse: e.target.value })
                                }}
                            />
                        </label>
                        <StringListField
                            label="Teléfonos"
                            values={editing.phones}
                            addLabel="Agregar teléfono"
                            placeholder="155 · 300 123 4567 · (607) 633 7000 · 018000…"
                            onChange={(phones) => {
                                editor.setEditing({ ...editing, phones })
                            }}
                        />
                        {hasInvalidPhone && (
                            <p className="fieldhint">
                                Hay teléfonos con formato no marcable; no podrás publicar hasta
                                corregirlos.
                            </p>
                        )}
                        <label className="field">
                            <span>Dirección (opcional)</span>
                            <input
                                value={editing.address ?? ''}
                                onChange={(e) => {
                                    editor.setEditing({
                                        ...editing,
                                        address: e.target.value === '' ? null : e.target.value,
                                    })
                                }}
                            />
                        </label>
                        <label className="field">
                            <span>Horario (opcional)</span>
                            <input
                                value={editing.hours ?? ''}
                                onChange={(e) => {
                                    editor.setEditing({
                                        ...editing,
                                        hours: e.target.value === '' ? null : e.target.value,
                                    })
                                }}
                            />
                        </label>

                        <EditorActions
                            busy={editor.busy}
                            isDirty={editor.isDirty}
                            canPublish={canPublish}
                            isNew={editor.isNew}
                            confirmingDelete={editor.confirmingDelete}
                            deleteBlockedReason={deleteBlockedReason}
                            onSave={() => {
                                void editor.save()
                            }}
                            onPublish={() => {
                                void editor.publish(publishIssues)
                            }}
                            onDelete={() => {
                                void editor.remove()
                            }}
                            onCancelNew={() => {
                                editor.cancelNew()
                            }}
                        />
                        <EditorStatus
                            selected={selected}
                            isDirty={editor.isDirty}
                            issues={publishIssues}
                        />
                    </div>
                ) : (
                    <div className="panel">
                        <p className="muted">
                            Selecciona una entidad de la lista o crea una nueva.
                        </p>
                    </div>
                )}
            </div>
        </>
    )
}

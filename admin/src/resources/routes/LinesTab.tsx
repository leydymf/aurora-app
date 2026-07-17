// Pestaña de líneas de atención (RF-42). El número guardado es exactamente
// lo que la app pasa al marcador (ACTION_DIAL), por eso la validación dura
// de formato telefónico colombiano.
import { useMemo } from 'react'
import { EditorActions } from '../../components/EditorActions'
import { EditorStatus } from '../../components/EditorStatus'
import { StatusBadge } from '../../components/StatusBadge'
import { buildOrderIndex } from '../../lib/drafts'
import { nextOpaqueId } from '../../lib/ids'
import { isValidColombianPhone } from '../../lib/phone'
import { useEntries } from '../../lib/useEntries'
import { useResourceEditor } from '../../lib/useResourceEditor'
import { emptyLine, lineFromRaw, normalizeLine, validateLine } from './schemas'

export function LinesTab() {
    const lines = useEntries('lines', lineFromRaw)
    const editor = useResourceEditor({
        resource: 'lines',
        entries: lines.entries,
        reload: lines.reload,
        setError: lines.setError,
        normalize: normalizeLine,
    })
    const { editing, selected, selectedId } = editor

    const sorted = useMemo(
        () => (lines.entries ? [...lines.entries].sort((a, b) => a.data.order - b.data.order) : []),
        [lines.entries],
    )

    const orderTakenBy = useMemo(
        () => buildOrderIndex(lines.entries, selectedId, (data) => data.name),
        [lines.entries, selectedId],
    )

    const publishIssues = useMemo(
        () =>
            selected !== null && selected.status === 'borrador'
                ? validateLine(selected.data, orderTakenBy)
                : [],
        [selected, orderTakenBy],
    )
    const canPublish =
        selected !== null &&
        selected.status === 'borrador' &&
        !editor.isDirty &&
        publishIssues.length === 0

    const numberInvalid =
        editing !== null && editing.number.trim() !== '' && !isValidColombianPhone(editing.number)

    function startNew() {
        if (!lines.entries) return
        const docId = nextOpaqueId(
            'l',
            lines.entries.map((entry) => entry.docId),
        )
        const maxOrder = lines.entries.reduce((max, entry) => Math.max(max, entry.data.order), 0)
        editor.startNew(docId, emptyLine(maxOrder + 1))
    }

    return (
        <>
            {lines.error !== null && (
                <p className="alert" role="alert">
                    {lines.error}
                </p>
            )}
            <div className="manager">
                <aside className="panel list">
                    <button
                        type="button"
                        disabled={!lines.entries || editor.busy}
                        onClick={() => {
                            startNew()
                        }}
                    >
                        + Nueva línea
                    </button>
                    {!lines.entries && <p className="muted">Cargando…</p>}
                    {lines.entries && sorted.length === 0 && !editor.isNew && (
                        <p className="muted">Sin líneas todavía.</p>
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
                            <label className="field">
                                <span>Orden</span>
                                <input
                                    type="number"
                                    min={1}
                                    value={editing.order}
                                    onChange={(e) => {
                                        editor.setEditing({
                                            ...editing,
                                            order: Number.parseInt(e.target.value, 10) || 0,
                                        })
                                    }}
                                />
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
                            <span>Número (lo que marca la app)</span>
                            <input
                                value={editing.number}
                                placeholder="155 · 018000… · (607) 633 7000"
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, number: e.target.value })
                                }}
                            />
                        </label>
                        {numberInvalid && (
                            <p className="fieldhint">
                                Número con formato no marcable; no podrás publicar hasta corregirlo.
                            </p>
                        )}
                        <label className="field">
                            <span>Descripción</span>
                            <textarea
                                rows={3}
                                value={editing.description}
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, description: e.target.value })
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
                        <p className="muted">Selecciona una línea de la lista o crea una nueva.</p>
                    </div>
                )}
            </div>
        </>
    )
}

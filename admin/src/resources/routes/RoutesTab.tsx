// Pestaña de rutas de atención (RF-42). La ruta apunta a entidades por id;
// NO se publica apuntando a una entidad sin publicar: la app resuelve
// `agencyIds` contra la colección viva y omite en silencio las referencias
// sin resolver (ContentMappers.kt), así que el panel garantiza que lo
// publicado siempre resuelve completo.
import { useMemo } from 'react'
import { EditorActions } from '../../components/EditorActions'
import { EditorStatus } from '../../components/EditorStatus'
import { RowControls } from '../../components/RowControls'
import { StatusBadge } from '../../components/StatusBadge'
import { routeVisual } from '../../components/contentVisuals'
import { IconBadge } from '../../components/icons'
import { buildOrderIndex } from '../../lib/drafts'
import { nextOpaqueId } from '../../lib/ids'
import { useEntries } from '../../lib/useEntries'
import { useResourceEditor } from '../../lib/useResourceEditor'
import { agencyFromRaw, emptyRoute, normalizeRoute, routeFromRaw, validateRoute } from './schemas'

export function RoutesTab() {
    const routes = useEntries('routes', routeFromRaw)
    const agencies = useEntries('agencies', agencyFromRaw)
    const editor = useResourceEditor({
        resource: 'routes',
        entries: routes.entries,
        reload: routes.reload,
        setError: routes.setError,
        normalize: normalizeRoute,
    })
    const { editing, selected, selectedId } = editor
    const error = routes.error ?? agencies.error

    const sorted = useMemo(
        () =>
            routes.entries ? [...routes.entries].sort((a, b) => a.data.order - b.data.order) : [],
        [routes.entries],
    )

    const orderTakenBy = useMemo(
        () => buildOrderIndex(routes.entries, selectedId, (data) => data.title),
        [routes.entries, selectedId],
    )

    const publishedAgencyIds = useMemo(
        () =>
            new Set(
                (agencies.entries ?? [])
                    .filter((entry) => entry.liveData !== null)
                    .map((entry) => entry.docId),
            ),
        [agencies.entries],
    )

    const agencyNames = useMemo(
        () =>
            new Map(
                (agencies.entries ?? []).map((entry) => [
                    entry.docId,
                    entry.data.name || entry.docId,
                ]),
            ),
        [agencies.entries],
    )

    const publishIssues = useMemo(
        () =>
            selected !== null && selected.status === 'borrador'
                ? validateRoute(selected.data, orderTakenBy, publishedAgencyIds, agencyNames)
                : [],
        [selected, orderTakenBy, publishedAgencyIds, agencyNames],
    )
    const canPublish =
        selected !== null &&
        selected.status === 'borrador' &&
        !editor.isDirty &&
        publishIssues.length === 0

    // El cuestionario validado v2 ya no recomienda rutas por id (sus accesos
    // referencian líneas y entidades, no rutas): eliminar una ruta no deja
    // referencias rotas en `forms`, así que no hay bloqueo por ese motivo.

    function startNew() {
        if (!routes.entries) return
        const docId = nextOpaqueId(
            'r',
            routes.entries.map((entry) => entry.docId),
        )
        const maxOrder = routes.entries.reduce((max, entry) => Math.max(max, entry.data.order), 0)
        editor.startNew(docId, emptyRoute(maxOrder + 1))
    }

    function addAgency(agencyId: string) {
        if (!editing || agencyId === '') return
        editor.setEditing({ ...editing, agencyIds: [...editing.agencyIds, agencyId] })
    }

    function removeAgency(index: number) {
        if (!editing) return
        editor.setEditing({
            ...editing,
            agencyIds: editing.agencyIds.filter((_, i) => i !== index),
        })
    }

    function moveAgency(index: number, delta: number) {
        if (!editing) return
        const target = index + delta
        if (target < 0 || target >= editing.agencyIds.length) return
        const ids = [...editing.agencyIds]
        const [moved] = ids.splice(index, 1)
        if (moved === undefined) return
        ids.splice(target, 0, moved)
        editor.setEditing({ ...editing, agencyIds: ids })
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
                        disabled={!routes.entries || editor.busy}
                        onClick={() => {
                            startNew()
                        }}
                    >
                        + Nueva ruta
                    </button>
                    {!routes.entries && <p className="muted">Cargando…</p>}
                    {routes.entries && sorted.length === 0 && !editor.isNew && (
                        <p className="muted">Sin rutas todavía.</p>
                    )}
                    {sorted.map((entry) => {
                        const visual = routeVisual(entry.docId)
                        return (
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
                                <span className="list__lead">
                                    <IconBadge name={visual.icon} accent={visual.accent} />
                                    <span className="list__title">
                                        {entry.data.title || entry.docId}
                                    </span>
                                </span>
                                <StatusBadge status={entry.status} />
                            </button>
                        )
                    })}
                    {editor.isNew && editing && (
                        <div className="list__item list__item--active">
                            <span className="list__lead">
                                <IconBadge name="place" accent="group" />
                                <span className="list__title">{editing.title || '(nueva)'}</span>
                            </span>
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
                            <span>Título (sector)</span>
                            <input
                                value={editing.title}
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, title: e.target.value })
                                }}
                            />
                        </label>
                        <label className="field">
                            <span>Resumen</span>
                            <textarea
                                rows={5}
                                value={editing.summary}
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, summary: e.target.value })
                                }}
                            />
                        </label>

                        <div className="field">
                            <span>Entidades de la ruta (en orden)</span>
                            {editing.agencyIds.length === 0 && (
                                <p className="muted">
                                    Sin entidades: la app mostrará la ruta vacía.
                                </p>
                            )}
                            {editing.agencyIds.map((agencyId, index) => {
                                const name = agencyNames.get(agencyId) ?? agencyId
                                const unpublished = !publishedAgencyIds.has(agencyId)
                                return (
                                    <div className="picker__row" key={agencyId}>
                                        <span className="picker__name">
                                            {name}
                                            {unpublished && (
                                                <span className="picker__warn">
                                                    {' '}
                                                    · sin publicar
                                                </span>
                                            )}
                                        </span>
                                        <RowControls
                                            label={name}
                                            index={index}
                                            count={editing.agencyIds.length}
                                            onMove={(delta) => {
                                                moveAgency(index, delta)
                                            }}
                                            onRemove={() => {
                                                removeAgency(index)
                                            }}
                                        />
                                    </div>
                                )
                            })}
                            <select
                                className="picker__add"
                                value=""
                                onChange={(e) => {
                                    addAgency(e.target.value)
                                }}
                            >
                                <option value="">Agregar entidad…</option>
                                {(agencies.entries ?? [])
                                    .filter((entry) => !editing.agencyIds.includes(entry.docId))
                                    .map((entry) => (
                                        <option key={entry.docId} value={entry.docId}>
                                            {(entry.data.name || entry.docId) +
                                                (entry.liveData === null ? ' (sin publicar)' : '')}
                                        </option>
                                    ))}
                            </select>
                        </div>

                        <EditorActions
                            busy={editor.busy}
                            isDirty={editor.isDirty}
                            canPublish={canPublish}
                            isNew={editor.isNew}
                            confirmingDelete={editor.confirmingDelete}
                            deleteBlockedReason={null}
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
                        <p className="muted">Selecciona una ruta de la lista o crea una nueva.</p>
                    </div>
                )}
            </div>
        </>
    )
}

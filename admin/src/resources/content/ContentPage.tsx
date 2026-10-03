// Gestión del contenido informativo: tipos de violencia (RF-41).
//
// Listado + editor con vista previa fiel a las secciones que la app pinta en
// TopicDetailScreen (título, resumen, SEÑALES, EJEMPLOS). Guardar escribe el
// BORRADOR (colección drafts); la app solo cambia al publicar (RF-44).
//
// Estilo V2 (v2-25 y v2-28): desde el esquema v2 (ADR 2026-08-14) cada tipo
// trae `icon`/`accent` elegidos en el selector de la biblioteca cerrada
// (VisualPicker); un documento sin selección pinta el mapeo automático por id
// (espejo de ContentVisual.kt, decisión D2, hoy contrato de degradación) y la
// vista previa muestra «como lo verá la usuaria».
import { useMemo } from 'react'
import { EditorActions } from '../../components/EditorActions'
import { EditorStatus } from '../../components/EditorStatus'
import { StatusBadge } from '../../components/StatusBadge'
import { StringListField } from '../../components/StringListField'
import { VisualPicker } from '../../components/VisualPicker'
import {
    isContentAccentId,
    isContentIconId,
    topicVisual,
    topicVisualIds,
} from '../../components/contentVisuals'
import { IconBadge, V2Icon } from '../../components/icons'
import { buildOrderIndex } from '../../lib/drafts'
import { nextOpaqueId } from '../../lib/ids'
import { useEntries } from '../../lib/useEntries'
import { useResourceEditor } from '../../lib/useResourceEditor'
import { emptyTopic, normalizeTopic, topicFromRaw, validateTopic } from './topicSchema'

export function ContentPage() {
    const topics = useEntries('topics', topicFromRaw)
    const editor = useResourceEditor({
        resource: 'topics',
        entries: topics.entries,
        reload: topics.reload,
        setError: topics.setError,
        normalize: normalizeTopic,
    })
    const { editing, selected, selectedId } = editor

    const sorted = useMemo(
        () =>
            topics.entries ? [...topics.entries].sort((a, b) => a.data.order - b.data.order) : [],
        [topics.entries],
    )

    const orderTakenBy = useMemo(
        () => buildOrderIndex(topics.entries, selectedId, (data) => data.title),
        [topics.entries, selectedId],
    )

    const publishIssues = useMemo(
        () =>
            selected !== null && selected.status === 'borrador'
                ? validateTopic(selected.data, orderTakenBy)
                : [],
        [selected, orderTakenBy],
    )
    const canPublish =
        selected !== null &&
        selected.status === 'borrador' &&
        !editor.isDirty &&
        publishIssues.length === 0

    // Visual vigente de lo que se edita (explícito o degradado al automático).
    const editingVisual = topicVisual(selectedId ?? '', editing?.icon ?? '', editing?.accent ?? '')

    function startNew() {
        if (!topics.entries) return
        const docId = nextOpaqueId(
            't',
            topics.entries.map((entry) => entry.docId),
        )
        const maxOrder = topics.entries.reduce((max, entry) => Math.max(max, entry.data.order), 0)
        editor.startNew(docId, emptyTopic(maxOrder + 1))
    }

    return (
        <section className="resource">
            <header className="resource__header">
                <h1>Contenido informativo</h1>
                <span className="resource__rf">RF-41</span>
            </header>
            <p className="resource__desc">
                Tipos de violencia que la app muestra en «Información». Los cambios se guardan como
                borrador y solo llegan a la app al publicar (RF-44). Los tipos publicados son también las
                opciones de «Qué tipo fue» en el registro de la usuaria, en este mismo orden y con este
                visual; retirar uno no rompe sus registros (conservan el último nombre publicado).
            </p>

            {topics.error !== null && (
                <p className="alert" role="alert">
                    {topics.error}
                </p>
            )}

            <div className="manager manager--preview">
                <aside className="panel list">
                    <button
                        type="button"
                        disabled={!topics.entries || editor.busy}
                        onClick={() => {
                            startNew()
                        }}
                    >
                        + Nuevo tipo
                    </button>
                    {!topics.entries && <p className="muted">Cargando…</p>}
                    {topics.entries && sorted.length === 0 && !editor.isNew && (
                        <p className="muted">Sin contenido todavía.</p>
                    )}
                    {sorted.map((entry) => {
                        const visual = topicVisual(entry.docId, entry.data.icon, entry.data.accent)
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
                                <IconBadge
                                    name={editingVisual.icon}
                                    accent={editingVisual.accent}
                                />
                                <span className="list__title">{editing.title || '(nuevo)'}</span>
                            </span>
                            <StatusBadge status="borrador" />
                        </div>
                    )}
                </aside>

                {editing ? (
                    <>
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
                            <VisualPicker
                                effective={topicVisualIds(
                                    selectedId ?? '',
                                    editing.icon,
                                    editing.accent,
                                )}
                                iconConfirmed={isContentIconId(editing.icon)}
                                accentConfirmed={isContentAccentId(editing.accent)}
                                onSelectIcon={(id) => {
                                    editor.setEditing({ ...editing, icon: id })
                                }}
                                onSelectAccent={(id) => {
                                    editor.setEditing({ ...editing, accent: id })
                                }}
                            />
                            <label className="field">
                                <span>Título</span>
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
                            <StringListField
                                label="Señales"
                                values={editing.signals}
                                addLabel="Agregar señal"
                                multiline
                                onChange={(signals) => {
                                    editor.setEditing({ ...editing, signals })
                                }}
                            />
                            <StringListField
                                label="Ejemplos"
                                values={editing.examples}
                                addLabel="Agregar ejemplo"
                                multiline
                                onChange={(examples) => {
                                    editor.setEditing({ ...editing, examples })
                                }}
                            />

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

                        <aside className="panel preview">
                            <p className="preview__tag">COMO LO VERÁ LA USUARIA</p>
                            <div className="preview__head">
                                <IconBadge
                                    name={editingVisual.icon}
                                    accent={editingVisual.accent}
                                    size={40}
                                />
                                <h2 className="preview__title">{editing.title || 'Sin título'}</h2>
                            </div>
                            <p className="preview__body">{editing.summary}</p>
                            {editing.signals.length > 0 && (
                                <>
                                    <p className="preview__section">SEÑALES</p>
                                    <ul className="preview__list">
                                        {editing.signals.map((signal, index) => (
                                            <li key={index}>{signal}</li>
                                        ))}
                                    </ul>
                                </>
                            )}
                            {editing.examples.length > 0 && (
                                <>
                                    <p className="preview__section">EJEMPLOS</p>
                                    <ul className="preview__list">
                                        {editing.examples.map((example, index) => (
                                            <li key={index}>«{example}»</li>
                                        ))}
                                    </ul>
                                </>
                            )}
                            <p className="preview__note">
                                Vista previa aproximada del detalle en la app.
                            </p>
                            <p className="preview__note">
                                El icono y el acento se eligen solo dentro de la biblioteca cerrada
                                de significados de la app. No hay color libre ni carga de imágenes:
                                cada acento ya trae su par de contraste verificado, así el contenido
                                nuevo nunca rompe la accesibilidad ni el significado de los colores.
                            </p>
                        </aside>
                    </>
                ) : (
                    <div className="panel">
                        <p className="muted">Selecciona un tipo de la lista o crea uno nuevo.</p>
                    </div>
                )}
            </div>

            <p className="note">
                <V2Icon name="info" size={18} />
                <span>
                    Los cambios publicados llegan a la app en su siguiente actualización de
                    contenido.
                </span>
            </p>
        </section>
    )
}

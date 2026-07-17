// Editor del cuestionario orientador (RF-43): metadatos, escala, preguntas
// (pesos y piso crítico) y niveles (umbrales, consejos y rutas recomendadas).
//
// La app elige el formulario de MAYOR `version` entre los válidos
// (FirestoreQuestionnaireRepository.kt), así que cada guardado del borrador
// estampa version = max(versiones vivas) + 1: al publicar, la nueva
// configuración rige de inmediato (RF-44). Publicar exige pasar TODAS las
// validaciones de coherencia (coherence.ts): la app descarta el documento
// completo ante cualquier pieza inválida.
import { useMemo } from 'react'
import { EditorActions } from '../../components/EditorActions'
import { EditorStatus } from '../../components/EditorStatus'
import { RowControls } from '../../components/RowControls'
import { StatusBadge } from '../../components/StatusBadge'
import { StringListField } from '../../components/StringListField'
import { nextOpaqueId } from '../../lib/ids'
import { useEntries } from '../../lib/useEntries'
import { useResourceEditor } from '../../lib/useResourceEditor'
import { routeFromRaw } from '../routes/schemas'
import { maxAchievableScore, validateQuestionnaire } from './coherence'
import { emptyForm, formFromRaw, normalizeForm, type FormDoc, type QuestionDoc } from './formSchema'

function moveItem<T>(items: readonly T[], index: number, delta: number): T[] {
    const target = index + delta
    if (target < 0 || target >= items.length) return [...items]
    const next = [...items]
    const [moved] = next.splice(index, 1)
    if (moved === undefined) return [...items]
    next.splice(target, 0, moved)
    return next
}

function parseIntOr0(value: string): number {
    const parsed = Number.parseInt(value, 10)
    return Number.isNaN(parsed) ? 0 : parsed
}

export function QuestionnairePage() {
    const forms = useEntries('forms', formFromRaw)
    const routes = useEntries('routes', routeFromRaw)

    // La app usa maxBy(version) sobre los documentos VIVOS: guardar el
    // borrador con la siguiente versión garantiza que, al publicar, rija.
    const nextVersion = useMemo(
        () =>
            (forms.entries ?? []).reduce(
                (max, entry) => Math.max(max, entry.liveData?.version ?? 0),
                0,
            ) + 1,
        [forms.entries],
    )

    const editor = useResourceEditor({
        resource: 'forms',
        entries: forms.entries,
        reload: forms.reload,
        setError: forms.setError,
        normalize: (form: FormDoc) => ({ ...normalizeForm(form), version: nextVersion }),
    })
    const { editing, selected, selectedId } = editor
    const error = forms.error ?? routes.error

    const publishedRouteIds = useMemo(
        () =>
            new Set(
                (routes.entries ?? [])
                    .filter((entry) => entry.liveData !== null)
                    .map((entry) => entry.docId),
            ),
        [routes.entries],
    )
    const routeNames = useMemo(
        () =>
            new Map(
                (routes.entries ?? []).map((entry) => [
                    entry.docId,
                    entry.data.title || entry.docId,
                ]),
            ),
        [routes.entries],
    )

    const publishIssues = useMemo(
        () =>
            selected !== null && selected.status === 'borrador'
                ? validateQuestionnaire(selected.data, publishedRouteIds, routeNames)
                : [],
        [selected, publishedRouteIds, routeNames],
    )
    const canPublish =
        selected !== null &&
        selected.status === 'borrador' &&
        !editor.isDirty &&
        publishIssues.length === 0

    const maxScore = editing ? maxAchievableScore(editing) : 0

    function startNew() {
        if (!forms.entries || forms.entries.length > 0) return
        const docId = nextOpaqueId(
            'f',
            forms.entries.map((entry) => entry.docId),
        )
        editor.startNew(docId, emptyForm())
    }

    // ---- Escala ----

    function addScaleOption() {
        if (!editing) return
        const id = nextOpaqueId(
            's',
            editing.scale.map((option) => option.id),
            1,
        )
        const maxValue = editing.scale.reduce((max, option) => Math.max(max, option.value), -1)
        editor.setEditing({
            ...editing,
            scale: [
                ...editing.scale,
                { id, order: editing.scale.length + 1, label: '', value: maxValue + 1 },
            ],
        })
    }

    function updateScaleOption(index: number, patch: { label?: string; value?: number }) {
        if (!editing) return
        editor.setEditing({
            ...editing,
            scale: editing.scale.map((option, i) =>
                i === index ? { ...option, ...patch } : option,
            ),
        })
    }

    // ---- Preguntas ----

    function addQuestion() {
        if (!editing) return
        const id = nextOpaqueId(
            'q',
            editing.questions.map((question) => question.id),
        )
        editor.setEditing({
            ...editing,
            questions: [
                ...editing.questions,
                { id, order: editing.questions.length + 1, text: '', weight: 1 },
            ],
        })
    }

    function updateQuestion(index: number, patch: { text?: string; weight?: number }) {
        if (!editing) return
        editor.setEditing({
            ...editing,
            questions: editing.questions.map((question, i) =>
                i === index ? { ...question, ...patch } : question,
            ),
        })
    }

    function setQuestionAlertLevel(index: number, alertLevelId: string) {
        if (!editing) return
        editor.setEditing({
            ...editing,
            questions: editing.questions.map((question, i) => {
                if (i !== index) return question
                const base: QuestionDoc = {
                    id: question.id,
                    order: question.order,
                    text: question.text,
                    weight: question.weight,
                }
                return alertLevelId === '' ? base : { ...base, alertLevelId }
            }),
        })
    }

    // ---- Niveles ----

    function addLevel() {
        if (!editing) return
        const id = nextOpaqueId(
            'n',
            editing.levels.map((level) => level.id),
        )
        const maxMin = editing.levels.reduce((max, level) => Math.max(max, level.minScore), -1)
        editor.setEditing({
            ...editing,
            levels: [
                ...editing.levels,
                {
                    id,
                    order: editing.levels.length + 1,
                    minScore: maxMin + 1,
                    title: '',
                    summary: '',
                    advice: [],
                    routeIds: [],
                },
            ],
        })
    }

    function updateLevel(
        index: number,
        patch: {
            title?: string
            summary?: string
            minScore?: number
            advice?: string[]
            routeIds?: string[]
        },
    ) {
        if (!editing) return
        editor.setEditing({
            ...editing,
            levels: editing.levels.map((level, i) =>
                i === index ? { ...level, ...patch } : level,
            ),
        })
    }

    return (
        <section className="resource">
            <header className="resource__header">
                <h1>Cuestionario orientador</h1>
                <span className="resource__rf">RF-43</span>
            </header>
            <p className="resource__desc">
                Preguntas, pesos, escala y niveles con sus umbrales. No se puede publicar una
                configuración incompleta o incoherente: la app descartaría el cuestionario completo.
                Al publicar, la nueva versión rige de inmediato (RF-44).
            </p>

            {error !== null && (
                <p className="alert" role="alert">
                    {error}
                </p>
            )}

            <div className="manager">
                <aside className="panel list">
                    {!forms.entries && <p className="muted">Cargando…</p>}
                    {forms.entries && forms.entries.length === 0 && !editor.isNew && (
                        <>
                            <p className="muted">No hay cuestionario todavía.</p>
                            <button
                                type="button"
                                disabled={editor.busy}
                                onClick={() => {
                                    startNew()
                                }}
                            >
                                + Crear cuestionario
                            </button>
                        </>
                    )}
                    {(forms.entries ?? []).map((entry) => (
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
                            <span className="list__title">
                                {entry.data.title || entry.docId} · v{String(entry.data.version)}
                            </span>
                            <StatusBadge status={entry.status} />
                        </button>
                    ))}
                    {editor.isNew && editing && (
                        <div className="list__item list__item--active">
                            <span className="list__title">{editing.title || '(nuevo)'}</span>
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
                                <span>Versión al publicar (automática)</span>
                                <input value={String(nextVersion)} readOnly disabled />
                            </label>
                        </div>
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
                            <span>Introducción</span>
                            <textarea
                                rows={3}
                                value={editing.intro}
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, intro: e.target.value })
                                }}
                            />
                        </label>
                        <label className="field">
                            <span>Aviso (no es un diagnóstico)</span>
                            <textarea
                                rows={2}
                                value={editing.notice}
                                onChange={(e) => {
                                    editor.setEditing({ ...editing, notice: e.target.value })
                                }}
                            />
                        </label>
                        <label className="field">
                            <span>Nota interna (reviewNote — la app la ignora)</span>
                            <textarea
                                rows={2}
                                value={editing.reviewNote ?? ''}
                                placeholder="Vacía para publicar; con [POR CONFIRMAR] bloquea la publicación."
                                onChange={(e) => {
                                    const value = e.target.value
                                    const base: FormDoc = { ...editing }
                                    delete base.reviewNote
                                    editor.setEditing(
                                        value === '' ? base : { ...base, reviewNote: value },
                                    )
                                }}
                            />
                        </label>

                        <div className="field">
                            <span>Escala de respuesta (la última opción es la más severa)</span>
                            {editing.scale.map((option, index) => (
                                <div className="picker__row" key={option.id}>
                                    <input
                                        value={option.label}
                                        placeholder={`Etiqueta ${String(index + 1)}`}
                                        onChange={(e) => {
                                            updateScaleOption(index, { label: e.target.value })
                                        }}
                                    />
                                    <input
                                        className="input--small"
                                        type="number"
                                        min={0}
                                        aria-label={`Valor de la opción ${String(index + 1)}`}
                                        value={option.value}
                                        onChange={(e) => {
                                            updateScaleOption(index, {
                                                value: parseIntOr0(e.target.value),
                                            })
                                        }}
                                    />
                                    <RowControls
                                        label={`opción ${String(index + 1)}`}
                                        index={index}
                                        count={editing.scale.length}
                                        onMove={(delta) => {
                                            editor.setEditing({
                                                ...editing,
                                                scale: moveItem(editing.scale, index, delta),
                                            })
                                        }}
                                        onRemove={() => {
                                            editor.setEditing({
                                                ...editing,
                                                scale: editing.scale.filter((_, i) => i !== index),
                                            })
                                        }}
                                    />
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    addScaleOption()
                                }}
                            >
                                + Agregar opción
                            </button>
                        </div>

                        <div className="field">
                            <span>Preguntas (peso × valor elegido = puntaje)</span>
                            {editing.questions.map((question, index) => (
                                <div className="question" key={question.id}>
                                    <div className="picker__row">
                                        <textarea
                                            rows={2}
                                            value={question.text}
                                            placeholder={`Pregunta ${String(index + 1)}`}
                                            onChange={(e) => {
                                                updateQuestion(index, { text: e.target.value })
                                            }}
                                        />
                                        <RowControls
                                            label={`pregunta ${String(index + 1)}`}
                                            index={index}
                                            count={editing.questions.length}
                                            onMove={(delta) => {
                                                editor.setEditing({
                                                    ...editing,
                                                    questions: moveItem(
                                                        editing.questions,
                                                        index,
                                                        delta,
                                                    ),
                                                })
                                            }}
                                            onRemove={() => {
                                                editor.setEditing({
                                                    ...editing,
                                                    questions: editing.questions.filter(
                                                        (_, i) => i !== index,
                                                    ),
                                                })
                                            }}
                                        />
                                    </div>
                                    <div className="question__meta">
                                        <label className="field field--inline">
                                            <span>Peso (entero ≥ 1)</span>
                                            <input
                                                className="input--small"
                                                type="number"
                                                min={1}
                                                value={question.weight}
                                                onChange={(e) => {
                                                    updateQuestion(index, {
                                                        weight: parseIntOr0(e.target.value),
                                                    })
                                                }}
                                            />
                                        </label>
                                        <label className="field field--inline">
                                            <span>Piso crítico (garantiza al menos ese nivel)</span>
                                            <select
                                                value={question.alertLevelId ?? ''}
                                                onChange={(e) => {
                                                    setQuestionAlertLevel(index, e.target.value)
                                                }}
                                            >
                                                <option value="">— sin piso crítico —</option>
                                                {editing.levels.map((level) => (
                                                    <option key={level.id} value={level.id}>
                                                        {level.title || level.id}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                    </div>
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    addQuestion()
                                }}
                            >
                                + Agregar pregunta
                            </button>
                        </div>

                        <div className="field">
                            <span>Niveles (de menor a mayor severidad)</span>
                            {editing.levels.map((level, index) => (
                                <div className="level" key={level.id}>
                                    <div className="picker__row">
                                        <input
                                            value={level.title}
                                            placeholder={`Nombre del nivel ${String(index + 1)}`}
                                            onChange={(e) => {
                                                updateLevel(index, { title: e.target.value })
                                            }}
                                        />
                                        <label className="field field--inline level__threshold">
                                            <span>Umbral (puntaje mínimo)</span>
                                            <input
                                                className="input--small"
                                                type="number"
                                                min={0}
                                                value={level.minScore}
                                                onChange={(e) => {
                                                    updateLevel(index, {
                                                        minScore: parseIntOr0(e.target.value),
                                                    })
                                                }}
                                            />
                                        </label>
                                        <RowControls
                                            label={`nivel ${String(index + 1)}`}
                                            index={index}
                                            count={editing.levels.length}
                                            onMove={(delta) => {
                                                editor.setEditing({
                                                    ...editing,
                                                    levels: moveItem(editing.levels, index, delta),
                                                })
                                            }}
                                            onRemove={() => {
                                                editor.setEditing({
                                                    ...editing,
                                                    levels: editing.levels.filter(
                                                        (_, i) => i !== index,
                                                    ),
                                                })
                                            }}
                                        />
                                    </div>
                                    <label className="field">
                                        <span>Descripción del nivel</span>
                                        <textarea
                                            rows={2}
                                            value={level.summary}
                                            onChange={(e) => {
                                                updateLevel(index, { summary: e.target.value })
                                            }}
                                        />
                                    </label>
                                    <StringListField
                                        label="Consejos"
                                        values={level.advice}
                                        addLabel="Agregar consejo"
                                        multiline
                                        onChange={(advice) => {
                                            updateLevel(index, { advice })
                                        }}
                                    />
                                    <div className="field">
                                        <span>Rutas recomendadas</span>
                                        {level.routeIds.map((routeId, routeIndex) => (
                                            <div className="picker__row" key={routeId}>
                                                <span className="picker__name">
                                                    {routeNames.get(routeId) ?? routeId}
                                                    {!publishedRouteIds.has(routeId) && (
                                                        <span className="picker__warn">
                                                            {' '}
                                                            · sin publicar
                                                        </span>
                                                    )}
                                                </span>
                                                <button
                                                    type="button"
                                                    className="btn--ghost"
                                                    aria-label={`Quitar ruta ${routeId}`}
                                                    onClick={() => {
                                                        updateLevel(index, {
                                                            routeIds: level.routeIds.filter(
                                                                (_, i) => i !== routeIndex,
                                                            ),
                                                        })
                                                    }}
                                                >
                                                    ✕
                                                </button>
                                            </div>
                                        ))}
                                        <select
                                            className="picker__add"
                                            value=""
                                            onChange={(e) => {
                                                if (e.target.value === '') return
                                                updateLevel(index, {
                                                    routeIds: [...level.routeIds, e.target.value],
                                                })
                                            }}
                                        >
                                            <option value="">Agregar ruta…</option>
                                            {(routes.entries ?? [])
                                                .filter(
                                                    (entry) =>
                                                        !level.routeIds.includes(entry.docId),
                                                )
                                                .map((entry) => (
                                                    <option key={entry.docId} value={entry.docId}>
                                                        {(entry.data.title || entry.docId) +
                                                            (entry.liveData === null
                                                                ? ' (sin publicar)'
                                                                : '')}
                                                    </option>
                                                ))}
                                        </select>
                                    </div>
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    addLevel()
                                }}
                            >
                                + Agregar nivel
                            </button>
                        </div>

                        <p className="editor__meta">
                            Puntaje posible: 0–{String(maxScore)} · Umbrales actuales:{' '}
                            {editing.levels.map((level) => String(level.minScore)).join(', ') ||
                                '—'}
                        </p>

                        <EditorActions
                            busy={editor.busy}
                            isDirty={editor.isDirty}
                            canPublish={canPublish}
                            isNew={editor.isNew}
                            confirmingDelete={editor.confirmingDelete}
                            deleteBlockedReason="El cuestionario no se elimina: la app siempre necesita uno vigente."
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
                            {forms.entries && forms.entries.length > 0
                                ? 'Selecciona el cuestionario de la lista.'
                                : 'Crea el cuestionario para empezar.'}
                        </p>
                    </div>
                )}
            </div>
        </section>
    )
}

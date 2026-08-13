// Editor del cuestionario orientador validado v2 (RF-43): metadatos,
// reconocimiento, escalas, bloques, municipios, preguntas (bloque/escala/peso/
// contexto), dimensiones (Track 1), banderas críticas (Track 2) y niveles
// (umbrales, consejos y accesos por municipio).
//
// La app elige el formulario de MAYOR `version` entre los válidos
// (FirestoreQuestionnaireRepository.kt), así que cada guardado del borrador
// estampa version = max(versiones vivas) + 1: al publicar, la nueva
// configuración rige de inmediato (RF-44). Publicar exige pasar TODAS las
// validaciones de coherencia (coherence.ts).
import { useMemo } from 'react'
import { EditorActions } from '../../components/EditorActions'
import { EditorStatus } from '../../components/EditorStatus'
import { RowControls } from '../../components/RowControls'
import { StatusBadge } from '../../components/StatusBadge'
import { StringListField } from '../../components/StringListField'
import { nextOpaqueId } from '../../lib/ids'
import { useEntries } from '../../lib/useEntries'
import { useResourceEditor } from '../../lib/useResourceEditor'
import { agencyFromRaw, lineFromRaw } from '../routes/schemas'
import {
    maxAchievableScore,
    validatePublishVersion,
    validateQuestionnaire,
    type PublishedDirectory,
} from './coherence'
import {
    emptyForm,
    formFromRaw,
    normalizeForm,
    type CountFlagDoc,
    type FormDoc,
    type ThresholdFlagDoc,
} from './formSchema'

type Ref = { id: string; name: string; published: boolean }

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

/** Selector de referencias (ids) con fichas removibles: preguntas, líneas o entidades. */
function RefPicker({
    label,
    selected,
    options,
    onChange,
}: {
    label: string
    selected: readonly string[]
    options: readonly Ref[]
    onChange: (ids: string[]) => void
}) {
    return (
        <div className="field">
            <span>{label}</span>
            {selected.map((id, index) => {
                const option = options.find((candidate) => candidate.id === id)
                return (
                    <div className="picker__row" key={id}>
                        <span className="picker__name">
                            {option?.name ?? id}
                            {option !== undefined && !option.published && (
                                <span className="picker__warn"> · sin publicar</span>
                            )}
                            {option === undefined && (
                                <span className="picker__warn"> · desconocido</span>
                            )}
                        </span>
                        <button
                            type="button"
                            className="btn--ghost"
                            aria-label={`Quitar ${id}`}
                            onClick={() => {
                                onChange(selected.filter((_, i) => i !== index))
                            }}
                        >
                            ✕
                        </button>
                    </div>
                )
            })}
            <select
                className="picker__add"
                value=""
                onChange={(e) => {
                    if (e.target.value === '') return
                    onChange([...selected, e.target.value])
                }}
            >
                <option value="">Agregar…</option>
                {options
                    .filter((option) => !selected.includes(option.id))
                    .map((option) => (
                        <option key={option.id} value={option.id}>
                            {option.name + (option.published ? '' : ' (sin publicar)')}
                        </option>
                    ))}
            </select>
        </div>
    )
}

export function QuestionnairePage() {
    const forms = useEntries('forms', formFromRaw)
    const lines = useEntries('lines', lineFromRaw)
    const agencies = useEntries('agencies', agencyFromRaw)

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
    const error = forms.error ?? lines.error ?? agencies.error

    const lineRefs = useMemo<Ref[]>(
        () =>
            (lines.entries ?? []).map((entry) => ({
                id: entry.docId,
                name: entry.data.name || entry.docId,
                published: entry.liveData !== null,
            })),
        [lines.entries],
    )
    const agencyRefs = useMemo<Ref[]>(
        () =>
            (agencies.entries ?? []).map((entry) => ({
                id: entry.docId,
                name: entry.data.name || entry.docId,
                published: entry.liveData !== null,
            })),
        [agencies.entries],
    )

    const published = useMemo<PublishedDirectory>(
        () => ({
            lineIds: new Set(lineRefs.filter((ref) => ref.published).map((ref) => ref.id)),
            agencyIds: new Set(agencyRefs.filter((ref) => ref.published).map((ref) => ref.id)),
            lineNames: new Map(lineRefs.map((ref) => [ref.id, ref.name])),
            agencyNames: new Map(agencyRefs.map((ref) => [ref.id, ref.name])),
        }),
        [lineRefs, agencyRefs],
    )

    const publishIssues = useMemo(
        () =>
            selected !== null && selected.status === 'borrador'
                ? [
                      ...validateQuestionnaire(selected.data, published),
                      ...validatePublishVersion(selected.data, nextVersion - 1),
                  ]
                : [],
        [selected, published, nextVersion],
    )
    const canPublish =
        selected !== null &&
        selected.status === 'borrador' &&
        !editor.isDirty &&
        publishIssues.length === 0

    const maxScore = editing ? maxAchievableScore(editing) : 0

    const vigenteId = useMemo(() => {
        let id: string | null = null
        let maxVersion = 0
        for (const entry of forms.entries ?? []) {
            if (entry.liveData !== null && entry.liveData.version > maxVersion) {
                maxVersion = entry.liveData.version
                id = entry.docId
            }
        }
        return id
    }, [forms.entries])

    const deleteBlockedReason =
        selected !== null && selected.docId === vigenteId
            ? 'No se elimina el cuestionario vigente: la app quedaría sin el que rige. Publica otro y luego elimina este.'
            : null

    function patch(next: Partial<FormDoc>) {
        if (!editing) return
        editor.setEditing({ ...editing, ...next })
    }

    function startNew() {
        if (!forms.entries) return
        const docId = nextOpaqueId(
            'f',
            forms.entries.map((entry) => entry.docId),
        )
        editor.startNew(docId, emptyForm())
    }

    const questionRefs: Ref[] = editing
        ? editing.questions.map((question) => ({
              id: question.id,
              name: question.text || question.id,
              published: true,
          }))
        : []
    const levelOptions = editing ? editing.levels : []

    return (
        <section className="resource">
            <header className="resource__header">
                <h1>Cuestionario orientador</h1>
                <span className="resource__rf">RF-43</span>
            </header>
            <p className="resource__desc">
                Instrumento validado v2: preguntas en dos bloques, escalas, dimensiones (tipos),
                banderas críticas, umbrales y accesos por municipio. No se puede publicar una
                configuración incompleta o incoherente: la app descartaría el cuestionario completo.
                La app siempre muestra UNO solo: el publicado de mayor versión (RF-44).
            </p>

            {error !== null && (
                <p className="alert" role="alert">
                    {error}
                </p>
            )}

            <div className="manager">
                <aside className="panel list">
                    <button
                        type="button"
                        disabled={!forms.entries || editor.busy}
                        onClick={() => {
                            startNew()
                        }}
                    >
                        + Nuevo cuestionario
                    </button>
                    {!forms.entries && <p className="muted">Cargando…</p>}
                    {forms.entries && forms.entries.length === 0 && !editor.isNew && (
                        <p className="muted">No hay cuestionario todavía.</p>
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
                            {entry.docId === vigenteId && (
                                <span className="badge badge--publicado">Vigente en la app</span>
                            )}
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
                                    patch({ title: e.target.value })
                                }}
                            />
                        </label>
                        <label className="field">
                            <span>Introducción</span>
                            <textarea
                                rows={3}
                                value={editing.intro}
                                onChange={(e) => {
                                    patch({ intro: e.target.value })
                                }}
                            />
                        </label>
                        <label className="field">
                            <span>Aviso permanente (RF-30 — visible en todo resultado)</span>
                            <textarea
                                rows={2}
                                value={editing.notice}
                                onChange={(e) => {
                                    patch({ notice: e.target.value })
                                }}
                            />
                        </label>
                        <div className="editor__grid">
                            <label className="field">
                                <span>Reconocimiento · encabezado</span>
                                <input
                                    value={editing.recognition.prefix}
                                    placeholder="Por tus respuestas, aparecen señales de"
                                    onChange={(e) => {
                                        patch({
                                            recognition: {
                                                ...editing.recognition,
                                                prefix: e.target.value,
                                            },
                                        })
                                    }}
                                />
                            </label>
                            <label className="field">
                                <span>Reconocimiento · sin señales</span>
                                <input
                                    value={editing.recognition.empty}
                                    onChange={(e) => {
                                        patch({
                                            recognition: {
                                                ...editing.recognition,
                                                empty: e.target.value,
                                            },
                                        })
                                    }}
                                />
                            </label>
                        </div>
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

                        {/* ---- Escalas ---- */}
                        <div className="field">
                            <span>Escalas de respuesta (la última opción es la más severa)</span>
                            {editing.scales.map((scale, scaleIndex) => (
                                <div className="level" key={scale.id}>
                                    <div className="picker__row">
                                        <strong className="picker__name">
                                            Escala «{scale.id}»
                                        </strong>
                                        <button
                                            type="button"
                                            className="btn--ghost"
                                            aria-label={`Quitar escala ${scale.id}`}
                                            onClick={() => {
                                                patch({
                                                    scales: editing.scales.filter(
                                                        (_, i) => i !== scaleIndex,
                                                    ),
                                                })
                                            }}
                                        >
                                            ✕
                                        </button>
                                    </div>
                                    {scale.options.map((option, index) => (
                                        <div className="picker__row" key={option.id}>
                                            <input
                                                value={option.label}
                                                placeholder={`Etiqueta ${String(index + 1)}`}
                                                onChange={(e) => {
                                                    patch({
                                                        scales: editing.scales.map((s, i) =>
                                                            i === scaleIndex
                                                                ? {
                                                                      ...s,
                                                                      options: s.options.map(
                                                                          (o, j) =>
                                                                              j === index
                                                                                  ? {
                                                                                        ...o,
                                                                                        label: e
                                                                                            .target
                                                                                            .value,
                                                                                    }
                                                                                  : o,
                                                                      ),
                                                                  }
                                                                : s,
                                                        ),
                                                    })
                                                }}
                                            />
                                            <input
                                                className="input--small"
                                                type="number"
                                                min={0}
                                                aria-label={`Valor de la opción ${String(index + 1)}`}
                                                value={option.value}
                                                onChange={(e) => {
                                                    patch({
                                                        scales: editing.scales.map((s, i) =>
                                                            i === scaleIndex
                                                                ? {
                                                                      ...s,
                                                                      options: s.options.map(
                                                                          (o, j) =>
                                                                              j === index
                                                                                  ? {
                                                                                        ...o,
                                                                                        value: parseIntOr0(
                                                                                            e.target
                                                                                                .value,
                                                                                        ),
                                                                                    }
                                                                                  : o,
                                                                      ),
                                                                  }
                                                                : s,
                                                        ),
                                                    })
                                                }}
                                            />
                                            <button
                                                type="button"
                                                className="btn--ghost"
                                                aria-label={`Quitar opción ${String(index + 1)}`}
                                                onClick={() => {
                                                    patch({
                                                        scales: editing.scales.map((s, i) =>
                                                            i === scaleIndex
                                                                ? {
                                                                      ...s,
                                                                      options: s.options.filter(
                                                                          (_, j) => j !== index,
                                                                      ),
                                                                  }
                                                                : s,
                                                        ),
                                                    })
                                                }}
                                            >
                                                ✕
                                            </button>
                                        </div>
                                    ))}
                                    <button
                                        type="button"
                                        className="btn--ghost listfield__add"
                                        onClick={() => {
                                            const optionId = nextOpaqueId(
                                                scale.id + '-o',
                                                scale.options.map((o) => o.id),
                                                1,
                                            )
                                            const maxValue = scale.options.reduce(
                                                (max, o) => Math.max(max, o.value),
                                                -1,
                                            )
                                            patch({
                                                scales: editing.scales.map((s, i) =>
                                                    i === scaleIndex
                                                        ? {
                                                              ...s,
                                                              options: [
                                                                  ...s.options,
                                                                  {
                                                                      id: optionId,
                                                                      order: s.options.length + 1,
                                                                      label: '',
                                                                      value: maxValue + 1,
                                                                  },
                                                              ],
                                                          }
                                                        : s,
                                                ),
                                            })
                                        }}
                                    >
                                        + Agregar opción
                                    </button>
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    const id = nextOpaqueId(
                                        'sc',
                                        editing.scales.map((s) => s.id),
                                    )
                                    patch({ scales: [...editing.scales, { id, options: [] }] })
                                }}
                            >
                                + Agregar escala
                            </button>
                        </div>

                        {/* ---- Bloques ---- */}
                        <div className="field">
                            <span>Bloques</span>
                            {editing.blocks.map((block, index) => (
                                <div className="picker__row" key={block.id}>
                                    <input
                                        value={block.title}
                                        placeholder={`Título del bloque ${String(index + 1)}`}
                                        onChange={(e) => {
                                            patch({
                                                blocks: editing.blocks.map((b, i) =>
                                                    i === index
                                                        ? { ...b, title: e.target.value }
                                                        : b,
                                                ),
                                            })
                                        }}
                                    />
                                    <RowControls
                                        label={`bloque ${String(index + 1)}`}
                                        index={index}
                                        count={editing.blocks.length}
                                        onMove={(delta) => {
                                            patch({
                                                blocks: moveItem(editing.blocks, index, delta),
                                            })
                                        }}
                                        onRemove={() => {
                                            patch({
                                                blocks: editing.blocks.filter(
                                                    (_, i) => i !== index,
                                                ),
                                            })
                                        }}
                                    />
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    const id = nextOpaqueId(
                                        'b',
                                        editing.blocks.map((b) => b.id),
                                    )
                                    patch({
                                        blocks: [
                                            ...editing.blocks,
                                            { id, order: editing.blocks.length + 1, title: '' },
                                        ],
                                    })
                                }}
                            >
                                + Agregar bloque
                            </button>
                        </div>

                        {/* ---- Municipios ---- */}
                        <div className="field">
                            <span>Municipios (para los accesos por nivel)</span>
                            {editing.municipalities.map((municipality, index) => (
                                <div className="picker__row" key={municipality.id}>
                                    <input
                                        value={municipality.label}
                                        placeholder={`Municipio ${String(index + 1)}`}
                                        onChange={(e) => {
                                            patch({
                                                municipalities: editing.municipalities.map(
                                                    (m, i) =>
                                                        i === index
                                                            ? { ...m, label: e.target.value }
                                                            : m,
                                                ),
                                            })
                                        }}
                                    />
                                    <RowControls
                                        label={`municipio ${String(index + 1)}`}
                                        index={index}
                                        count={editing.municipalities.length}
                                        onMove={(delta) => {
                                            patch({
                                                municipalities: moveItem(
                                                    editing.municipalities,
                                                    index,
                                                    delta,
                                                ),
                                            })
                                        }}
                                        onRemove={() => {
                                            patch({
                                                municipalities: editing.municipalities.filter(
                                                    (_, i) => i !== index,
                                                ),
                                            })
                                        }}
                                    />
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    const id = nextOpaqueId(
                                        'm',
                                        editing.municipalities.map((m) => m.id),
                                    )
                                    patch({
                                        municipalities: [
                                            ...editing.municipalities,
                                            {
                                                id,
                                                order: editing.municipalities.length + 1,
                                                label: '',
                                            },
                                        ],
                                    })
                                }}
                            >
                                + Agregar municipio
                            </button>
                        </div>

                        {/* ---- Preguntas ---- */}
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
                                                patch({
                                                    questions: editing.questions.map((q, i) =>
                                                        i === index
                                                            ? { ...q, text: e.target.value }
                                                            : q,
                                                    ),
                                                })
                                            }}
                                        />
                                        <RowControls
                                            label={`pregunta ${String(index + 1)}`}
                                            index={index}
                                            count={editing.questions.length}
                                            onMove={(delta) => {
                                                patch({
                                                    questions: moveItem(
                                                        editing.questions,
                                                        index,
                                                        delta,
                                                    ),
                                                })
                                            }}
                                            onRemove={() => {
                                                patch({
                                                    questions: editing.questions.filter(
                                                        (_, i) => i !== index,
                                                    ),
                                                })
                                            }}
                                        />
                                    </div>
                                    <div className="question__meta">
                                        <label className="field field--inline">
                                            <span>Bloque</span>
                                            <select
                                                value={question.blockId}
                                                onChange={(e) => {
                                                    patch({
                                                        questions: editing.questions.map((q, i) =>
                                                            i === index
                                                                ? { ...q, blockId: e.target.value }
                                                                : q,
                                                        ),
                                                    })
                                                }}
                                            >
                                                <option value="">—</option>
                                                {editing.blocks.map((block) => (
                                                    <option key={block.id} value={block.id}>
                                                        {block.title || block.id}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                        <label className="field field--inline">
                                            <span>Escala</span>
                                            <select
                                                value={question.scaleId}
                                                onChange={(e) => {
                                                    patch({
                                                        questions: editing.questions.map((q, i) =>
                                                            i === index
                                                                ? { ...q, scaleId: e.target.value }
                                                                : q,
                                                        ),
                                                    })
                                                }}
                                            >
                                                <option value="">—</option>
                                                {editing.scales.map((scale) => (
                                                    <option key={scale.id} value={scale.id}>
                                                        {scale.id}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                        <label className="field field--inline">
                                            <span>Peso (≥ 1; contexto = 0)</span>
                                            <input
                                                className="input--small"
                                                type="number"
                                                min={0}
                                                value={question.weight}
                                                onChange={(e) => {
                                                    patch({
                                                        questions: editing.questions.map((q, i) =>
                                                            i === index
                                                                ? {
                                                                      ...q,
                                                                      weight: parseIntOr0(
                                                                          e.target.value,
                                                                      ),
                                                                  }
                                                                : q,
                                                        ),
                                                    })
                                                }}
                                            />
                                        </label>
                                        <label className="field field--inline">
                                            <span>De contexto (P0, sin puntaje)</span>
                                            <input
                                                type="checkbox"
                                                checked={question.context}
                                                onChange={(e) => {
                                                    patch({
                                                        questions: editing.questions.map((q, i) =>
                                                            i === index
                                                                ? {
                                                                      ...q,
                                                                      context: e.target.checked,
                                                                  }
                                                                : q,
                                                        ),
                                                    })
                                                }}
                                            />
                                        </label>
                                    </div>
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    const id = nextOpaqueId(
                                        'q',
                                        editing.questions.map((q) => q.id),
                                    )
                                    patch({
                                        questions: [
                                            ...editing.questions,
                                            {
                                                id,
                                                order: editing.questions.length + 1,
                                                blockId: editing.blocks[0]?.id ?? '',
                                                scaleId: editing.scales[0]?.id ?? '',
                                                text: '',
                                                weight: 1,
                                                context: false,
                                            },
                                        ],
                                    })
                                }}
                            >
                                + Agregar pregunta
                            </button>
                        </div>

                        {/* ---- Dimensiones (Track 1) ---- */}
                        <div className="field">
                            <span>
                                Dimensiones — reconocimiento de tipos (se encienden con ≥ umbral)
                            </span>
                            {editing.dimensions.map((dimension, index) => (
                                <div className="level" key={dimension.id}>
                                    <div className="picker__row">
                                        <input
                                            value={dimension.label}
                                            placeholder="Etiqueta (p. ej. «violencia psicológica»)"
                                            onChange={(e) => {
                                                patch({
                                                    dimensions: editing.dimensions.map((d, i) =>
                                                        i === index
                                                            ? { ...d, label: e.target.value }
                                                            : d,
                                                    ),
                                                })
                                            }}
                                        />
                                        <label className="field field--inline">
                                            <span>Umbral</span>
                                            <input
                                                className="input--small"
                                                type="number"
                                                min={1}
                                                value={dimension.minValue}
                                                onChange={(e) => {
                                                    patch({
                                                        dimensions: editing.dimensions.map(
                                                            (d, i) =>
                                                                i === index
                                                                    ? {
                                                                          ...d,
                                                                          minValue: parseIntOr0(
                                                                              e.target.value,
                                                                          ),
                                                                      }
                                                                    : d,
                                                        ),
                                                    })
                                                }}
                                            />
                                        </label>
                                        <button
                                            type="button"
                                            className="btn--ghost"
                                            aria-label={`Quitar dimensión ${String(index + 1)}`}
                                            onClick={() => {
                                                patch({
                                                    dimensions: editing.dimensions.filter(
                                                        (_, i) => i !== index,
                                                    ),
                                                })
                                            }}
                                        >
                                            ✕
                                        </button>
                                    </div>
                                    <RefPicker
                                        label="Preguntas de la dimensión"
                                        selected={dimension.questionIds}
                                        options={questionRefs}
                                        onChange={(questionIds) => {
                                            patch({
                                                dimensions: editing.dimensions.map((d, i) =>
                                                    i === index ? { ...d, questionIds } : d,
                                                ),
                                            })
                                        }}
                                    />
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    const id = nextOpaqueId(
                                        'd',
                                        editing.dimensions.map((d) => d.id),
                                    )
                                    patch({
                                        dimensions: [
                                            ...editing.dimensions,
                                            {
                                                id,
                                                order: editing.dimensions.length + 1,
                                                label: '',
                                                questionIds: [],
                                                minValue: 1,
                                            },
                                        ],
                                    })
                                }}
                            >
                                + Agregar dimensión
                            </button>
                        </div>

                        {/* ---- Banderas críticas (Track 2) ---- */}
                        <div className="field">
                            <span>Banderas de umbral (una pregunta ≥ valor eleva al nivel)</span>
                            {editing.thresholdFlags.map((flag, index) => (
                                <div className="picker__row" key={index}>
                                    <select
                                        aria-label={`Pregunta de la bandera ${String(index + 1)}`}
                                        value={flag.questionId}
                                        onChange={(e) => {
                                            const next: ThresholdFlagDoc[] =
                                                editing.thresholdFlags.map((f, i) =>
                                                    i === index
                                                        ? { ...f, questionId: e.target.value }
                                                        : f,
                                                )
                                            patch({ thresholdFlags: next })
                                        }}
                                    >
                                        <option value="">— pregunta —</option>
                                        {editing.questions.map((question) => (
                                            <option key={question.id} value={question.id}>
                                                {question.id}
                                            </option>
                                        ))}
                                    </select>
                                    <input
                                        className="input--small"
                                        type="number"
                                        min={1}
                                        aria-label={`Valor mínimo de la bandera ${String(index + 1)}`}
                                        value={flag.minValue}
                                        onChange={(e) => {
                                            const next: ThresholdFlagDoc[] =
                                                editing.thresholdFlags.map((f, i) =>
                                                    i === index
                                                        ? {
                                                              ...f,
                                                              minValue: parseIntOr0(e.target.value),
                                                          }
                                                        : f,
                                                )
                                            patch({ thresholdFlags: next })
                                        }}
                                    />
                                    <select
                                        aria-label={`Nivel de la bandera ${String(index + 1)}`}
                                        value={flag.levelId}
                                        onChange={(e) => {
                                            const next: ThresholdFlagDoc[] =
                                                editing.thresholdFlags.map((f, i) =>
                                                    i === index
                                                        ? { ...f, levelId: e.target.value }
                                                        : f,
                                                )
                                            patch({ thresholdFlags: next })
                                        }}
                                    >
                                        <option value="">— nivel —</option>
                                        {levelOptions.map((level) => (
                                            <option key={level.id} value={level.id}>
                                                {level.title || level.id}
                                            </option>
                                        ))}
                                    </select>
                                    <button
                                        type="button"
                                        className="btn--ghost"
                                        aria-label={`Quitar bandera ${String(index + 1)}`}
                                        onClick={() => {
                                            patch({
                                                thresholdFlags: editing.thresholdFlags.filter(
                                                    (_, i) => i !== index,
                                                ),
                                            })
                                        }}
                                    >
                                        ✕
                                    </button>
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    patch({
                                        thresholdFlags: [
                                            ...editing.thresholdFlags,
                                            { questionId: '', minValue: 1, levelId: '' },
                                        ],
                                    })
                                }}
                            >
                                + Agregar bandera de umbral
                            </button>
                        </div>

                        <div className="field">
                            <span>Banderas de conteo (N preguntas ≥ valor elevan al nivel)</span>
                            {editing.countFlags.map((flag, index) => (
                                <div className="level" key={index}>
                                    <RefPicker
                                        label={`Preguntas de la bandera de conteo ${String(index + 1)}`}
                                        selected={flag.questionIds}
                                        options={questionRefs}
                                        onChange={(questionIds) => {
                                            const next: CountFlagDoc[] = editing.countFlags.map(
                                                (f, i) => (i === index ? { ...f, questionIds } : f),
                                            )
                                            patch({ countFlags: next })
                                        }}
                                    />
                                    <div className="question__meta">
                                        <label className="field field--inline">
                                            <span>Cada una ≥</span>
                                            <input
                                                className="input--small"
                                                type="number"
                                                min={1}
                                                value={flag.eachMinValue}
                                                onChange={(e) => {
                                                    const next: CountFlagDoc[] =
                                                        editing.countFlags.map((f, i) =>
                                                            i === index
                                                                ? {
                                                                      ...f,
                                                                      eachMinValue: parseIntOr0(
                                                                          e.target.value,
                                                                      ),
                                                                  }
                                                                : f,
                                                        )
                                                    patch({ countFlags: next })
                                                }}
                                            />
                                        </label>
                                        <label className="field field--inline">
                                            <span>Al menos</span>
                                            <input
                                                className="input--small"
                                                type="number"
                                                min={1}
                                                value={flag.countAtLeast}
                                                onChange={(e) => {
                                                    const next: CountFlagDoc[] =
                                                        editing.countFlags.map((f, i) =>
                                                            i === index
                                                                ? {
                                                                      ...f,
                                                                      countAtLeast: parseIntOr0(
                                                                          e.target.value,
                                                                      ),
                                                                  }
                                                                : f,
                                                        )
                                                    patch({ countFlags: next })
                                                }}
                                            />
                                        </label>
                                        <label className="field field--inline">
                                            <span>Nivel</span>
                                            <select
                                                value={flag.levelId}
                                                onChange={(e) => {
                                                    const next: CountFlagDoc[] =
                                                        editing.countFlags.map((f, i) =>
                                                            i === index
                                                                ? { ...f, levelId: e.target.value }
                                                                : f,
                                                        )
                                                    patch({ countFlags: next })
                                                }}
                                            >
                                                <option value="">— nivel —</option>
                                                {levelOptions.map((level) => (
                                                    <option key={level.id} value={level.id}>
                                                        {level.title || level.id}
                                                    </option>
                                                ))}
                                            </select>
                                        </label>
                                        <button
                                            type="button"
                                            className="btn--ghost"
                                            aria-label={`Quitar bandera de conteo ${String(index + 1)}`}
                                            onClick={() => {
                                                patch({
                                                    countFlags: editing.countFlags.filter(
                                                        (_, i) => i !== index,
                                                    ),
                                                })
                                            }}
                                        >
                                            ✕
                                        </button>
                                    </div>
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    patch({
                                        countFlags: [
                                            ...editing.countFlags,
                                            {
                                                questionIds: [],
                                                eachMinValue: 1,
                                                countAtLeast: 2,
                                                levelId: '',
                                            },
                                        ],
                                    })
                                }}
                            >
                                + Agregar bandera de conteo
                            </button>
                        </div>

                        {/* ---- Niveles ---- */}
                        <div className="field">
                            <span>Niveles (de menor a mayor severidad)</span>
                            {editing.levels.map((level, index) => (
                                <div className="level" key={level.id}>
                                    <div className="picker__row">
                                        <input
                                            value={level.title}
                                            placeholder={`Nombre del nivel ${String(index + 1)}`}
                                            onChange={(e) => {
                                                patch({
                                                    levels: editing.levels.map((l, i) =>
                                                        i === index
                                                            ? { ...l, title: e.target.value }
                                                            : l,
                                                    ),
                                                })
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
                                                    patch({
                                                        levels: editing.levels.map((l, i) =>
                                                            i === index
                                                                ? {
                                                                      ...l,
                                                                      minScore: parseIntOr0(
                                                                          e.target.value,
                                                                      ),
                                                                  }
                                                                : l,
                                                        ),
                                                    })
                                                }}
                                            />
                                        </label>
                                        <RowControls
                                            label={`nivel ${String(index + 1)}`}
                                            index={index}
                                            count={editing.levels.length}
                                            onMove={(delta) => {
                                                patch({
                                                    levels: moveItem(editing.levels, index, delta),
                                                })
                                            }}
                                            onRemove={() => {
                                                patch({
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
                                                patch({
                                                    levels: editing.levels.map((l, i) =>
                                                        i === index
                                                            ? { ...l, summary: e.target.value }
                                                            : l,
                                                    ),
                                                })
                                            }}
                                        />
                                    </label>
                                    <StringListField
                                        label="Consejos"
                                        values={level.advice}
                                        addLabel="Agregar consejo"
                                        multiline
                                        onChange={(advice) => {
                                            patch({
                                                levels: editing.levels.map((l, i) =>
                                                    i === index ? { ...l, advice } : l,
                                                ),
                                            })
                                        }}
                                    />
                                    <RefPicker
                                        label="Líneas de atención (nacionales, siempre visibles)"
                                        selected={level.lineIds}
                                        options={lineRefs}
                                        onChange={(lineIds) => {
                                            patch({
                                                levels: editing.levels.map((l, i) =>
                                                    i === index ? { ...l, lineIds } : l,
                                                ),
                                            })
                                        }}
                                    />
                                    <RefPicker
                                        label="Entidades fijas (no dependen del municipio)"
                                        selected={level.agencyIds}
                                        options={agencyRefs}
                                        onChange={(agencyIds) => {
                                            patch({
                                                levels: editing.levels.map((l, i) =>
                                                    i === index ? { ...l, agencyIds } : l,
                                                ),
                                            })
                                        }}
                                    />
                                    {editing.municipalities.map((municipality) => (
                                        <RefPicker
                                            key={municipality.id}
                                            label={`Entidades en ${municipality.label || municipality.id}`}
                                            selected={
                                                level.municipalAgencyIds[municipality.id] ?? []
                                            }
                                            options={agencyRefs}
                                            onChange={(ids) => {
                                                patch({
                                                    levels: editing.levels.map((l, i) =>
                                                        i === index
                                                            ? {
                                                                  ...l,
                                                                  municipalAgencyIds: {
                                                                      ...l.municipalAgencyIds,
                                                                      [municipality.id]: ids,
                                                                  },
                                                              }
                                                            : l,
                                                    ),
                                                })
                                            }}
                                        />
                                    ))}
                                </div>
                            ))}
                            <button
                                type="button"
                                className="btn--ghost listfield__add"
                                onClick={() => {
                                    const id = nextOpaqueId(
                                        'n',
                                        editing.levels.map((l) => l.id),
                                    )
                                    const maxMin = editing.levels.reduce(
                                        (max, l) => Math.max(max, l.minScore),
                                        -1,
                                    )
                                    patch({
                                        levels: [
                                            ...editing.levels,
                                            {
                                                id,
                                                order: editing.levels.length + 1,
                                                minScore: maxMin + 1,
                                                title: '',
                                                summary: '',
                                                advice: [],
                                                lineIds: [],
                                                agencyIds: [],
                                                municipalAgencyIds: {},
                                            },
                                        ],
                                    })
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
                            deleteBlockedReason={deleteBlockedReason}
                            onSave={() => {
                                void editor.save()
                            }}
                            onPublish={() => {
                                void editor.publish(publishIssues)
                            }}
                            onDelete={() => {
                                if (deleteBlockedReason !== null) return
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
                                ? 'Selecciona un cuestionario de la lista o crea uno nuevo.'
                                : 'Crea el cuestionario para empezar.'}
                        </p>
                    </div>
                )}
            </div>
        </section>
    )
}

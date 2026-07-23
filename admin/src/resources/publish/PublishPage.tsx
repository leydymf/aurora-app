// Publicación consolidada (RF-44): estado de los cinco recursos y publicación
// de los borradores pendientes en un solo lugar. Publica con LAS MISMAS
// validaciones duras que cada editor (ningún camino salta la compuerta):
// si un borrador tiene problemas, aquí solo se señala y se corrige en su
// editor. Orden recomendado: entidades → rutas → cuestionario (las
// referencias solo pueden apuntar a documentos ya publicados).
import { useMemo, useState } from 'react'
import { StatusBadge } from '../../components/StatusBadge'
import {
    buildOrderIndex,
    publishEntry,
    type DraftStatus,
    type ResourceCollection,
    type ResourceEntry,
} from '../../lib/drafts'
import { describeError } from '../../lib/errors'
import { useEntries } from '../../lib/useEntries'
import type { ValidationIssue } from '../../lib/validation'
import { topicFromRaw, validateTopic, type TopicDoc } from '../content/topicSchema'
import {
    maxAchievableScore,
    validatePublishVersion,
    validateQuestionnaire,
} from '../questionnaire/coherence'
import { formFromRaw, type FormDoc } from '../questionnaire/formSchema'
import {
    agencyFromRaw,
    lineFromRaw,
    routeFromRaw,
    validateAgency,
    validateLine,
    validateRoute,
    type AgencyDoc,
    type LineDoc,
    type RouteDoc,
} from '../routes/schemas'

function countDrafts(entries: readonly { status: DraftStatus }[] | null): number {
    return (entries ?? []).filter((entry) => entry.status === 'borrador').length
}

interface SectionProps<T extends Record<string, unknown>> {
    readonly resource: ResourceCollection
    readonly title: string
    readonly entries: ResourceEntry<T>[] | null
    readonly labelOf: (entry: ResourceEntry<T>) => string
    readonly issuesOf: (entry: ResourceEntry<T>) => ValidationIssue[]
    readonly busyId: string | null
    readonly onPublish: (entry: ResourceEntry<T>) => void
}

function Section<T extends Record<string, unknown>>({
    resource,
    title,
    entries,
    labelOf,
    issuesOf,
    busyId,
    onPublish,
}: SectionProps<T>) {
    return (
        <div className="panel publish__section">
            <h2 className="publish__title">{title}</h2>
            {!entries && <p className="muted">Cargando…</p>}
            {entries && entries.length === 0 && <p className="muted">Sin documentos.</p>}
            {(entries ?? []).map((entry) => {
                const issues = entry.status === 'borrador' ? issuesOf(entry) : []
                const rowId = `${resource}/${entry.docId}`
                return (
                    <div className="publish__row" key={entry.docId}>
                        <span className="list__title">{labelOf(entry)}</span>
                        <StatusBadge status={entry.status} />
                        {entry.status === 'borrador' &&
                            (issues.length === 0 ? (
                                <button
                                    type="button"
                                    disabled={busyId !== null}
                                    onClick={() => {
                                        onPublish(entry)
                                    }}
                                >
                                    {busyId === rowId ? 'Publicando…' : 'Publicar'}
                                </button>
                            ) : (
                                <span className="picker__warn">
                                    {String(issues.length)} problema(s): corrígelo en su editor
                                </span>
                            ))}
                    </div>
                )
            })}
        </div>
    )
}

export function PublishPage() {
    const topics = useEntries('topics', topicFromRaw)
    const routes = useEntries('routes', routeFromRaw)
    const agencies = useEntries('agencies', agencyFromRaw)
    const lines = useEntries('lines', lineFromRaw)
    const forms = useEntries('forms', formFromRaw)
    const [busyId, setBusyId] = useState<string | null>(null)
    const [actionError, setActionError] = useState<string | null>(null)

    const loadError = topics.error ?? routes.error ?? agencies.error ?? lines.error ?? forms.error
    const error = actionError ?? loadError

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
    const publishedLineIds = useMemo(
        () =>
            new Set(
                (lines.entries ?? [])
                    .filter((entry) => entry.liveData !== null)
                    .map((entry) => entry.docId),
            ),
        [lines.entries],
    )
    const lineNames = useMemo(
        () =>
            new Map(
                (lines.entries ?? []).map((entry) => [entry.docId, entry.data.name || entry.docId]),
            ),
        [lines.entries],
    )

    const pendingCount =
        countDrafts(topics.entries) +
        countDrafts(routes.entries) +
        countDrafts(agencies.entries) +
        countDrafts(lines.entries) +
        countDrafts(forms.entries)

    // Una única definición de la validación por recurso, usada tanto para
    // pintar el estado como para la compuerta lógica de publishRow.
    const agencyIssues = (entry: ResourceEntry<AgencyDoc>) => validateAgency(entry.data)
    const routeIssues = (entry: ResourceEntry<RouteDoc>) =>
        validateRoute(
            entry.data,
            buildOrderIndex(routes.entries, entry.docId, (data) => data.title),
            publishedAgencyIds,
            agencyNames,
        )
    const lineIssues = (entry: ResourceEntry<LineDoc>) =>
        validateLine(
            entry.data,
            buildOrderIndex(lines.entries, entry.docId, (data) => data.name),
        )
    const topicIssues = (entry: ResourceEntry<TopicDoc>) =>
        validateTopic(
            entry.data,
            buildOrderIndex(topics.entries, entry.docId, (data) => data.title),
        )
    const maxLiveFormVersion = useMemo(
        () =>
            (forms.entries ?? []).reduce(
                (max, entry) => Math.max(max, entry.liveData?.version ?? 0),
                0,
            ),
        [forms.entries],
    )
    const formIssues = (entry: ResourceEntry<FormDoc>) => [
        ...validateQuestionnaire(entry.data, {
            lineIds: publishedLineIds,
            agencyIds: publishedAgencyIds,
            lineNames,
            agencyNames,
        }),
        ...validatePublishVersion(entry.data, maxLiveFormVersion),
    ]

    // La compuerta se reafirma EN LÓGICA, no solo en el render del botón:
    // `issues` se calcula en el momento del clic y aquí se aborta si algo
    // dejó de ser publicable (mismo contrato que useResourceEditor.publish).
    function publishRow(
        resource: ResourceCollection,
        entry: ResourceEntry<Record<string, unknown>>,
        reload: () => Promise<void>,
        issues: readonly ValidationIssue[],
    ) {
        if (issues.length > 0) return
        setBusyId(`${resource}/${entry.docId}`)
        setActionError(null)
        void publishEntry(resource, entry.docId, entry.data)
            .then(reload)
            .catch((err: unknown) => {
                setActionError(describeError('No se pudo publicar.', err))
            })
            .finally(() => {
                setBusyId(null)
            })
    }

    return (
        <section className="resource">
            <header className="resource__header">
                <h1>Publicación</h1>
                <span className="resource__rf">RF-44</span>
            </header>
            <p className="resource__desc">
                La app solo lee lo publicado: los borradores viven aparte y no la afectan. Aquí ves
                el estado de todo y publicas lo pendiente. Orden recomendado cuando hay referencias
                nuevas: primero entidades, luego rutas y de último el cuestionario.
            </p>
            <p className="muted">
                {pendingCount === 0
                    ? 'No hay borradores pendientes: todo lo editado está publicado.'
                    : `Borradores pendientes de publicar: ${String(pendingCount)}.`}
            </p>

            {error !== null && (
                <p className="alert" role="alert">
                    {error}
                </p>
            )}

            <div className="publish">
                <Section
                    resource="agencies"
                    title="Entidades"
                    entries={agencies.entries}
                    labelOf={(entry) => entry.data.name || entry.docId}
                    issuesOf={agencyIssues}
                    busyId={busyId}
                    onPublish={(entry) => {
                        publishRow('agencies', entry, agencies.reload, agencyIssues(entry))
                    }}
                />
                <Section
                    resource="routes"
                    title="Rutas de atención"
                    entries={routes.entries}
                    labelOf={(entry) => entry.data.title || entry.docId}
                    issuesOf={routeIssues}
                    busyId={busyId}
                    onPublish={(entry) => {
                        publishRow('routes', entry, routes.reload, routeIssues(entry))
                    }}
                />
                <Section
                    resource="lines"
                    title="Líneas de atención"
                    entries={lines.entries}
                    labelOf={(entry) => entry.data.name || entry.docId}
                    issuesOf={lineIssues}
                    busyId={busyId}
                    onPublish={(entry) => {
                        publishRow('lines', entry, lines.reload, lineIssues(entry))
                    }}
                />
                <Section
                    resource="topics"
                    title="Contenido informativo"
                    entries={topics.entries}
                    labelOf={(entry) => entry.data.title || entry.docId}
                    issuesOf={topicIssues}
                    busyId={busyId}
                    onPublish={(entry) => {
                        publishRow('topics', entry, topics.reload, topicIssues(entry))
                    }}
                />
                <Section
                    resource="forms"
                    title="Cuestionario"
                    entries={forms.entries}
                    labelOf={(entry) =>
                        `${entry.data.title || entry.docId} · v${String(entry.data.version)} · ` +
                        `puntaje 0–${String(maxAchievableScore(entry.data))}`
                    }
                    issuesOf={formIssues}
                    busyId={busyId}
                    onPublish={(entry) => {
                        publishRow('forms', entry, forms.reload, formIssues(entry))
                    }}
                />
            </div>
        </section>
    )
}

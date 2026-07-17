// Esquema de un tipo de violencia (colección `topics`), espejo EXACTO de lo
// que la app Android lee: TopicDto (content/data/remote/ContentDtos.kt) y sus
// campos obligatorios en ContentMappers.kt. Agregar o renombrar un campo aquí
// es un cambio de esquema y requiere decisión explícita del proyecto.
import { num, str, strList } from '../../lib/parse'
import { containsDraftMarker, isBlank, type ValidationIssue } from '../../lib/validation'

/** Espejo de SUPPORTED_SCHEMA_VERSION de la app (ContentMappers.kt). */
export const SCHEMA_VERSION = 1

export type TopicDoc = {
    schemaVersion: number
    order: number
    title: string
    summary: string
    signals: string[]
    examples: string[]
}

export function topicFromRaw(raw: Record<string, unknown>): TopicDoc {
    return {
        schemaVersion: num(raw, 'schemaVersion', SCHEMA_VERSION),
        order: num(raw, 'order'),
        title: str(raw, 'title'),
        summary: str(raw, 'summary'),
        signals: strList(raw, 'signals'),
        examples: strList(raw, 'examples'),
    }
}

export function emptyTopic(order: number): TopicDoc {
    return {
        schemaVersion: SCHEMA_VERSION,
        order,
        title: '',
        summary: '',
        signals: [],
        examples: [],
    }
}

/** Recorta espacios accidentales antes de guardar; no elimina ítems. */
export function normalizeTopic(topic: TopicDoc): TopicDoc {
    return {
        ...topic,
        title: topic.title.trim(),
        summary: topic.summary.trim(),
        signals: topic.signals.map((signal) => signal.trim()),
        examples: topic.examples.map((example) => example.trim()),
    }
}

/**
 * Validación dura para PUBLICAR: espejo de lo que la app exige para no
 * descartar el documento (ContentMappers.kt: order, title y summary
 * obligatorios) más las reglas del panel: orden único, sin ítems vacíos y
 * sin el marcador [POR CONFIRMAR] (compuerta M-2 heredada de la siembra).
 */
export function validateTopic(
    topic: TopicDoc,
    orderTakenBy: ReadonlyMap<number, string>,
): ValidationIssue[] {
    const issues: ValidationIssue[] = []
    if (topic.schemaVersion !== SCHEMA_VERSION) {
        // Primera compuerta de la app (isSupportedSchema): una versión extraña
        // (p. ej. editada a mano en la consola) haría que la app lo ignore.
        issues.push({
            field: 'general',
            message: `schemaVersion ${String(topic.schemaVersion)} no es la soportada (${String(SCHEMA_VERSION)}).`,
        })
    }
    if (!Number.isInteger(topic.order) || topic.order < 1) {
        issues.push({ field: 'order', message: 'El orden debe ser un entero mayor o igual a 1.' })
    }
    const conflict = orderTakenBy.get(topic.order)
    if (conflict !== undefined) {
        issues.push({
            field: 'order',
            message: `El orden ${String(topic.order)} ya lo usa ${conflict}.`,
        })
    }
    if (isBlank(topic.title)) {
        issues.push({ field: 'title', message: 'El título es obligatorio.' })
    }
    if (isBlank(topic.summary)) {
        issues.push({ field: 'summary', message: 'El resumen es obligatorio.' })
    }
    topic.signals.forEach((signal, index) => {
        if (isBlank(signal)) {
            issues.push({
                field: 'signals',
                message: `La señal ${String(index + 1)} está vacía.`,
            })
        }
    })
    topic.examples.forEach((example, index) => {
        if (isBlank(example)) {
            issues.push({
                field: 'examples',
                message: `El ejemplo ${String(index + 1)} está vacío.`,
            })
        }
    })
    if (containsDraftMarker(topic)) {
        issues.push({
            field: 'general',
            message:
                'Contiene el marcador [POR CONFIRMAR]: verifica el dato con la fuente oficial antes de publicar.',
        })
    }
    return issues
}

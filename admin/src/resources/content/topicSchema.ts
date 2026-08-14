// Esquema de un tipo de violencia (colección `topics`), espejo EXACTO de lo
// que la app Android lee: TopicDto (content/data/remote/ContentDtos.kt) y sus
// campos obligatorios en ContentMappers.kt. Agregar o renombrar un campo aquí
// es un cambio de esquema y requiere decisión explícita del proyecto.
import { isContentAccentId, isContentIconId } from '../../components/contentVisuals'
import { num, str, strList } from '../../lib/parse'
import { containsDraftMarker, isBlank, type ValidationIssue } from '../../lib/validation'

/**
 * Espejo de SUPPORTED_SCHEMA_VERSION de la app (ContentMappers.kt). v2 (ADR
 * 2026-08-14): los topics ganan `icon`/`accent`, ids de la biblioteca cerrada.
 */
export const SCHEMA_VERSION = 2

export type TopicDoc = {
    schemaVersion: number
    order: number
    title: string
    summary: string
    signals: string[]
    examples: string[]
    icon: string
    accent: string
}

export function topicFromRaw(raw: Record<string, unknown>): TopicDoc {
    return {
        schemaVersion: num(raw, 'schemaVersion', SCHEMA_VERSION),
        order: num(raw, 'order'),
        title: str(raw, 'title'),
        summary: str(raw, 'summary'),
        signals: strList(raw, 'signals'),
        examples: strList(raw, 'examples'),
        // Vacío = documento v1 sin selección: el panel y la app pintan el
        // mapeo automático por id; publicar exigirá elegir de la biblioteca.
        icon: str(raw, 'icon'),
        accent: str(raw, 'accent'),
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
        // El neutro de la Guía: lo mismo que la app da a un id sin mapear.
        icon: 'book',
        accent: 'neutral',
    }
}

/**
 * Recorta espacios accidentales antes de guardar; no elimina ítems. Guardar
 * bajo el panel v2 sube además el documento al esquema vigente: la app
 * soporta 1..2 y la validación dura exigirá la selección visual al publicar.
 */
export function normalizeTopic(topic: TopicDoc): TopicDoc {
    return {
        ...topic,
        schemaVersion: SCHEMA_VERSION,
        title: topic.title.trim(),
        summary: topic.summary.trim(),
        signals: topic.signals.map((signal) => signal.trim()),
        examples: topic.examples.map((example) => example.trim()),
        icon: topic.icon.trim(),
        accent: topic.accent.trim(),
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
    if (!isContentIconId(topic.icon)) {
        // La biblioteca cerrada no crece con datos: el campo solo SELECCIONA
        // dentro de ella; cualquier otro valor degradaría en la app (RF-25).
        issues.push({ field: 'icon', message: 'Elige un icono de la biblioteca cerrada.' })
    }
    if (!isContentAccentId(topic.accent)) {
        issues.push({ field: 'accent', message: 'Elige un acento de la biblioteca cerrada.' })
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

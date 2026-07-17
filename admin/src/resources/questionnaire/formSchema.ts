// Esquema del cuestionario (colección `forms`), espejo EXACTO de lo que la
// app Android lee: FormDto/ScaleOptionDto/QuestionDto/LevelDto
// (questionnaire/data/remote/FormDtos.kt) y sus campos obligatorios en
// QuestionnaireMappers.kt. `reviewNote` es el único campo extra que existe en
// los datos reales (marcador P-38 de la siembra); la app lo ignora y el panel
// lo conserva editable, omitiéndolo cuando queda vacío. Agregar o renombrar
// cualquier otro campo es un cambio de esquema y requiere decisión explícita.
import { num, recordList, str, strList, strOrNull } from '../../lib/parse'

/** Espejo de SUPPORTED_SCHEMA_VERSION de la app (QuestionnaireMappers.kt). */
export const SCHEMA_VERSION = 1

export type ScaleOptionDoc = {
    id: string
    order: number
    label: string
    value: number
}

export type QuestionDoc = {
    id: string
    order: number
    text: string
    weight: number
    alertLevelId?: string
}

export type LevelDoc = {
    id: string
    order: number
    minScore: number
    title: string
    summary: string
    advice: string[]
    routeIds: string[]
}

export type FormDoc = {
    schemaVersion: number
    version: number
    title: string
    intro: string
    notice: string
    scale: ScaleOptionDoc[]
    questions: QuestionDoc[]
    levels: LevelDoc[]
    reviewNote?: string
}

function scaleFromRaw(raw: Record<string, unknown>): ScaleOptionDoc {
    return {
        id: str(raw, 'id'),
        order: num(raw, 'order'),
        label: str(raw, 'label'),
        value: num(raw, 'value'),
    }
}

function questionFromRaw(raw: Record<string, unknown>): QuestionDoc {
    const alertLevelId = strOrNull(raw, 'alertLevelId')
    return {
        id: str(raw, 'id'),
        order: num(raw, 'order'),
        text: str(raw, 'text'),
        weight: num(raw, 'weight'),
        ...(alertLevelId !== null ? { alertLevelId } : {}),
    }
}

function levelFromRaw(raw: Record<string, unknown>): LevelDoc {
    return {
        id: str(raw, 'id'),
        order: num(raw, 'order'),
        minScore: num(raw, 'minScore'),
        title: str(raw, 'title'),
        summary: str(raw, 'summary'),
        advice: strList(raw, 'advice'),
        routeIds: strList(raw, 'routeIds'),
    }
}

export function formFromRaw(raw: Record<string, unknown>): FormDoc {
    const reviewNote = strOrNull(raw, 'reviewNote')
    return {
        schemaVersion: num(raw, 'schemaVersion', SCHEMA_VERSION),
        version: num(raw, 'version'),
        title: str(raw, 'title'),
        intro: str(raw, 'intro'),
        notice: str(raw, 'notice'),
        // El orden del arreglo es el canónico en el editor (la app también
        // ordena por `order` al mapear).
        scale: recordList(raw, 'scale').map(scaleFromRaw).sort(byOrder),
        questions: recordList(raw, 'questions').map(questionFromRaw).sort(byOrder),
        levels: recordList(raw, 'levels').map(levelFromRaw).sort(byOrder),
        ...(reviewNote !== null ? { reviewNote } : {}),
    }
}

function byOrder(a: { order: number }, b: { order: number }): number {
    return a.order - b.order
}

/** Cuestionario vacío para el alta inicial (colección `forms` sin documentos). */
export function emptyForm(): FormDoc {
    return {
        schemaVersion: SCHEMA_VERSION,
        version: 0,
        title: '',
        intro: '',
        notice: '',
        scale: [],
        questions: [],
        levels: [],
    }
}

/**
 * Limpieza previa a guardar: recorta textos, descarta consejos vacíos,
 * re-numera `order` según la posición en el arreglo (el editor reordena
 * moviendo filas) y omite `reviewNote` si quedó vacía.
 */
export function normalizeForm(form: FormDoc): FormDoc {
    const { reviewNote, ...rest } = form
    const trimmedNote = reviewNote?.trim() ?? ''
    return {
        ...rest,
        title: form.title.trim(),
        intro: form.intro.trim(),
        notice: form.notice.trim(),
        scale: form.scale.map((option, index) => ({
            ...option,
            order: index + 1,
            label: option.label.trim(),
        })),
        questions: form.questions.map((question, index) => ({
            ...question,
            order: index + 1,
            text: question.text.trim(),
        })),
        levels: form.levels.map((level, index) => ({
            ...level,
            order: index + 1,
            title: level.title.trim(),
            summary: level.summary.trim(),
            advice: level.advice.map((item) => item.trim()).filter((item) => item.length > 0),
            routeIds: [...level.routeIds],
        })),
        ...(trimmedNote.length > 0 ? { reviewNote: trimmedNote } : {}),
    }
}

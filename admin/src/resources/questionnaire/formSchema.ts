// Esquema del cuestionario (colección `forms`), espejo EXACTO de lo que la app
// Android lee: FormDto/ScaleDto/ScaleOptionDto/BlockDto/QuestionDto/
// DimensionDto/RecognitionDto/ThresholdFlagDto/CountFlagDto/MunicipalityDto/
// LevelDto (questionnaire/data/remote/FormDtos.kt) y sus campos obligatorios en
// QuestionnaireMappers.kt. Instrumento validado v2 (documento §4/§5/§6).
// `reviewNote` es el único campo extra que existe en los datos reales (marcador
// P-38 de la siembra); la app lo ignora y el panel lo conserva editable,
// omitiéndolo cuando queda vacío. Agregar o renombrar cualquier otro campo es un
// cambio de esquema y requiere decisión explícita.
import { asRecord, isRecord, num, recordList, str, strList, strOrNull } from '../../lib/parse'

/** Espejo de SUPPORTED_SCHEMA_VERSION de la app (QuestionnaireMappers.kt). */
export const SCHEMA_VERSION = 2

export type ScaleOptionDoc = {
    id: string
    order: number
    label: string
    value: number
}

export type ScaleDoc = {
    id: string
    options: ScaleOptionDoc[]
}

export type BlockDoc = {
    id: string
    order: number
    title: string
}

export type QuestionDoc = {
    id: string
    order: number
    blockId: string
    scaleId: string
    text: string
    weight: number
    context: boolean
}

export type DimensionDoc = {
    id: string
    order: number
    label: string
    questionIds: string[]
    minValue: number
}

export type RecognitionDoc = {
    prefix: string
    empty: string
}

export type ThresholdFlagDoc = {
    questionId: string
    minValue: number
    levelId: string
}

export type CountFlagDoc = {
    questionIds: string[]
    eachMinValue: number
    countAtLeast: number
    levelId: string
}

export type MunicipalityDoc = {
    id: string
    order: number
    label: string
}

export type LevelDoc = {
    id: string
    order: number
    minScore: number
    title: string
    summary: string
    advice: string[]
    lineIds: string[]
    agencyIds: string[]
    municipalAgencyIds: Record<string, string[]>
}

export type FormDoc = {
    schemaVersion: number
    version: number
    title: string
    intro: string
    notice: string
    scales: ScaleDoc[]
    blocks: BlockDoc[]
    questions: QuestionDoc[]
    dimensions: DimensionDoc[]
    recognition: RecognitionDoc
    thresholdFlags: ThresholdFlagDoc[]
    countFlags: CountFlagDoc[]
    municipalities: MunicipalityDoc[]
    levels: LevelDoc[]
    reviewNote?: string
}

function byOrder(a: { order: number }, b: { order: number }): number {
    return a.order - b.order
}

function scaleOptionFromRaw(raw: Record<string, unknown>): ScaleOptionDoc {
    return {
        id: str(raw, 'id'),
        order: num(raw, 'order'),
        label: str(raw, 'label'),
        value: num(raw, 'value'),
    }
}

function scaleFromRaw(raw: Record<string, unknown>): ScaleDoc {
    return {
        id: str(raw, 'id'),
        options: recordList(raw, 'options').map(scaleOptionFromRaw).sort(byOrder),
    }
}

function blockFromRaw(raw: Record<string, unknown>): BlockDoc {
    return { id: str(raw, 'id'), order: num(raw, 'order'), title: str(raw, 'title') }
}

function questionFromRaw(raw: Record<string, unknown>): QuestionDoc {
    return {
        id: str(raw, 'id'),
        order: num(raw, 'order'),
        blockId: str(raw, 'blockId'),
        scaleId: str(raw, 'scaleId'),
        text: str(raw, 'text'),
        weight: num(raw, 'weight'),
        context: raw['context'] === true,
    }
}

function dimensionFromRaw(raw: Record<string, unknown>): DimensionDoc {
    return {
        id: str(raw, 'id'),
        order: num(raw, 'order'),
        label: str(raw, 'label'),
        questionIds: strList(raw, 'questionIds'),
        minValue: num(raw, 'minValue', 1),
    }
}

function thresholdFlagFromRaw(raw: Record<string, unknown>): ThresholdFlagDoc {
    return {
        questionId: str(raw, 'questionId'),
        minValue: num(raw, 'minValue', 1),
        levelId: str(raw, 'levelId'),
    }
}

function countFlagFromRaw(raw: Record<string, unknown>): CountFlagDoc {
    return {
        questionIds: strList(raw, 'questionIds'),
        eachMinValue: num(raw, 'eachMinValue', 1),
        countAtLeast: num(raw, 'countAtLeast', 2),
        levelId: str(raw, 'levelId'),
    }
}

function municipalityFromRaw(raw: Record<string, unknown>): MunicipalityDoc {
    return { id: str(raw, 'id'), order: num(raw, 'order'), label: str(raw, 'label') }
}

function municipalAgencyIdsFromRaw(raw: Record<string, unknown>): Record<string, string[]> {
    const value = raw['municipalAgencyIds']
    if (!isRecord(value)) return {}
    const result: Record<string, string[]> = {}
    for (const [key, ids] of Object.entries(value)) {
        result[key] = Array.isArray(ids)
            ? ids.filter((id): id is string => typeof id === 'string')
            : []
    }
    return result
}

function levelFromRaw(raw: Record<string, unknown>): LevelDoc {
    return {
        id: str(raw, 'id'),
        order: num(raw, 'order'),
        minScore: num(raw, 'minScore'),
        title: str(raw, 'title'),
        summary: str(raw, 'summary'),
        advice: strList(raw, 'advice'),
        lineIds: strList(raw, 'lineIds'),
        agencyIds: strList(raw, 'agencyIds'),
        municipalAgencyIds: municipalAgencyIdsFromRaw(raw),
    }
}

function recognitionFromRaw(raw: Record<string, unknown>): RecognitionDoc {
    const recognition = asRecord(raw['recognition'])
    return { prefix: str(recognition, 'prefix'), empty: str(recognition, 'empty') }
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
        scales: recordList(raw, 'scales').map(scaleFromRaw),
        blocks: recordList(raw, 'blocks').map(blockFromRaw).sort(byOrder),
        questions: recordList(raw, 'questions').map(questionFromRaw).sort(byOrder),
        dimensions: recordList(raw, 'dimensions').map(dimensionFromRaw).sort(byOrder),
        recognition: recognitionFromRaw(raw),
        thresholdFlags: recordList(raw, 'thresholdFlags').map(thresholdFlagFromRaw),
        countFlags: recordList(raw, 'countFlags').map(countFlagFromRaw),
        municipalities: recordList(raw, 'municipalities').map(municipalityFromRaw).sort(byOrder),
        levels: recordList(raw, 'levels').map(levelFromRaw).sort(byOrder),
        ...(reviewNote !== null ? { reviewNote } : {}),
    }
}

/** Cuestionario vacío para el alta inicial (colección `forms` sin documentos). */
export function emptyForm(): FormDoc {
    return {
        schemaVersion: SCHEMA_VERSION,
        version: 0,
        title: '',
        intro: '',
        notice: '',
        scales: [],
        blocks: [],
        questions: [],
        dimensions: [],
        recognition: { prefix: '', empty: '' },
        thresholdFlags: [],
        countFlags: [],
        municipalities: [],
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
        recognition: {
            prefix: form.recognition.prefix.trim(),
            empty: form.recognition.empty.trim(),
        },
        scales: form.scales.map((scale) => ({
            id: scale.id,
            options: scale.options.map((option, index) => ({
                ...option,
                order: index + 1,
                label: option.label.trim(),
            })),
        })),
        blocks: form.blocks.map((block, index) => ({
            ...block,
            order: index + 1,
            title: block.title.trim(),
        })),
        questions: form.questions.map((question, index) => ({
            ...question,
            order: index + 1,
            text: question.text.trim(),
        })),
        dimensions: form.dimensions.map((dimension, index) => ({
            ...dimension,
            order: index + 1,
            label: dimension.label.trim(),
            questionIds: [...dimension.questionIds],
        })),
        thresholdFlags: form.thresholdFlags.map((flag) => ({ ...flag })),
        countFlags: form.countFlags.map((flag) => ({
            ...flag,
            questionIds: [...flag.questionIds],
        })),
        municipalities: form.municipalities.map((municipality, index) => ({
            ...municipality,
            order: index + 1,
            label: municipality.label.trim(),
        })),
        levels: form.levels.map((level, index) => ({
            ...level,
            order: index + 1,
            title: level.title.trim(),
            summary: level.summary.trim(),
            advice: level.advice.map((item) => item.trim()).filter((item) => item.length > 0),
            lineIds: [...level.lineIds],
            agencyIds: [...level.agencyIds],
            municipalAgencyIds: Object.fromEntries(
                Object.entries(level.municipalAgencyIds).map(([key, ids]) => [key, [...ids]]),
            ),
        })),
        ...(trimmedNote.length > 0 ? { reviewNote: trimmedNote } : {}),
    }
}

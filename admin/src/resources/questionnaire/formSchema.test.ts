// normalizeForm es el eslabón entre las dos verdades del cuestionario: el
// campo `order` (por el que la app ordena en su mapper) debe coincidir con la
// posición del arreglo (por la que valida coherence.ts y reordena el editor).
import { describe, expect, it } from 'vitest'
import { formFromRaw, normalizeForm, type FormDoc } from './formSchema'

function baseForm(): FormDoc {
    return {
        schemaVersion: 1,
        version: 3,
        title: ' Reconocer tu situación. ',
        intro: 'Intro.',
        notice: 'Aviso.',
        scale: [
            { id: 's0', order: 9, label: ' Nunca ', value: 0 },
            { id: 's1', order: 4, label: 'Alguna vez', value: 1 },
        ],
        questions: [
            { id: 'q01', order: 7, text: ' ¿Controla lo que haces? ', weight: 1 },
            { id: 'q02', order: 2, text: '¿Te humilla?', weight: 2, alertLevelId: 'n01' },
        ],
        levels: [
            {
                id: 'n01',
                order: 5,
                minScore: 0,
                title: 'Variable',
                summary: 'Resumen.',
                advice: [' consejo ', '', '  '],
                routeIds: ['r01'],
            },
        ],
    }
}

describe('normalizeForm', () => {
    it('re-numera `order` según la posición del arreglo en escala, preguntas y niveles', () => {
        const normalized = normalizeForm(baseForm())
        expect(normalized.scale.map((option) => option.order)).toEqual([1, 2])
        expect(normalized.questions.map((question) => question.order)).toEqual([1, 2])
        expect(normalized.levels.map((level) => level.order)).toEqual([1])
    })

    it('recorta textos y descarta consejos vacíos (renglones abandonados)', () => {
        const normalized = normalizeForm(baseForm())
        expect(normalized.title).toBe('Reconocer tu situación.')
        expect(normalized.scale[0]?.label).toBe('Nunca')
        expect(normalized.questions[0]?.text).toBe('¿Controla lo que haces?')
        expect(normalized.levels[0]?.advice).toEqual(['consejo'])
    })

    it('omite reviewNote vacía y conserva recortada la que tiene contenido', () => {
        const emptied = normalizeForm({ ...baseForm(), reviewNote: '   ' })
        expect('reviewNote' in emptied).toBe(false)
        const kept = normalizeForm({ ...baseForm(), reviewNote: ' nota interna ' })
        expect(kept.reviewNote).toBe('nota interna')
    })

    it('conserva alertLevelId y routeIds intactos (solo re-numera y recorta)', () => {
        const normalized = normalizeForm(baseForm())
        expect(normalized.questions[1]?.alertLevelId).toBe('n01')
        expect(normalized.levels[0]?.routeIds).toEqual(['r01'])
    })
})

describe('formFromRaw', () => {
    it('ordena escala, preguntas y niveles por `order` al leer (orden canónico del editor)', () => {
        const raw = {
            schemaVersion: 1,
            version: 1,
            title: 't',
            intro: 'i',
            notice: 'n',
            scale: [
                { id: 's1', order: 2, label: 'B', value: 1 },
                { id: 's0', order: 1, label: 'A', value: 0 },
            ],
            questions: [
                { id: 'q02', order: 2, text: 'dos', weight: 1 },
                { id: 'q01', order: 1, text: 'uno', weight: 1 },
            ],
            levels: [],
        }
        const form = formFromRaw(raw)
        expect(form.scale.map((option) => option.id)).toEqual(['s0', 's1'])
        expect(form.questions.map((question) => question.id)).toEqual(['q01', 'q02'])
    })

    it('solo incluye reviewNote y alertLevelId cuando existen (exactOptionalPropertyTypes)', () => {
        const form = formFromRaw({ schemaVersion: 1, version: 1 })
        expect('reviewNote' in form).toBe(false)
        const withNote = formFromRaw({ schemaVersion: 1, version: 1, reviewNote: 'x' })
        expect(withNote.reviewNote).toBe('x')
    })
})

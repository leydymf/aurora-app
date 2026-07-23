// normalizeForm es el eslabón entre las dos verdades del cuestionario validado
// v2: el campo `order` (por el que la app ordena en su mapper) debe coincidir
// con la posición del arreglo (por la que valida coherence.ts y reordena el
// editor).
import { describe, expect, it } from 'vitest'
import { formFromRaw, normalizeForm, type FormDoc } from './formSchema'

function baseForm(): FormDoc {
    return {
        schemaVersion: 2,
        version: 3,
        title: ' Reconocer tu situación. ',
        intro: 'Intro.',
        notice: 'Aviso.',
        scales: [
            {
                id: 'freq',
                options: [
                    { id: 'f0', order: 9, label: ' Nunca ', value: 0 },
                    { id: 'f1', order: 4, label: 'Una vez', value: 1 },
                ],
            },
        ],
        blocks: [{ id: 'a', order: 5, title: ' Lo que vives ' }],
        questions: [
            {
                id: 'p1',
                order: 7,
                blockId: 'a',
                scaleId: 'freq',
                text: ' ¿Te humilla? ',
                weight: 1,
                context: false,
            },
            {
                id: 'p0',
                order: 2,
                blockId: 'a',
                scaleId: 'link',
                text: '¿Con quién?',
                weight: 0,
                context: true,
            },
        ],
        dimensions: [
            { id: 'psico', order: 3, label: ' psicológica ', questionIds: ['p1'], minValue: 1 },
        ],
        recognition: { prefix: ' Aparecen señales de ', empty: ' Sin señales. ' },
        thresholdFlags: [{ questionId: 'p1', minValue: 2, levelId: 'grave' }],
        countFlags: [{ questionIds: ['p1'], eachMinValue: 1, countAtLeast: 1, levelId: 'extremo' }],
        municipalities: [{ id: 'giron', order: 8, label: ' Girón ' }],
        levels: [
            {
                id: 'variable',
                order: 5,
                minScore: 0,
                title: ' Para tener presente ',
                summary: ' Resumen. ',
                advice: [' consejo ', '', '  '],
                lineIds: ['l02'],
                agencyIds: ['a13'],
                municipalAgencyIds: { giron: ['a11'] },
            },
        ],
    }
}

describe('normalizeForm', () => {
    it('re-numera `order` según la posición del arreglo en escala, bloques, preguntas, dimensiones, municipios y niveles', () => {
        const normalized = normalizeForm(baseForm())
        expect(normalized.scales[0]?.options.map((option) => option.order)).toEqual([1, 2])
        expect(normalized.blocks.map((block) => block.order)).toEqual([1])
        expect(normalized.questions.map((question) => question.order)).toEqual([1, 2])
        expect(normalized.dimensions.map((dimension) => dimension.order)).toEqual([1])
        expect(normalized.municipalities.map((municipality) => municipality.order)).toEqual([1])
        expect(normalized.levels.map((level) => level.order)).toEqual([1])
    })

    it('recorta textos y descarta consejos vacíos (renglones abandonados)', () => {
        const normalized = normalizeForm(baseForm())
        expect(normalized.title).toBe('Reconocer tu situación.')
        expect(normalized.scales[0]?.options[0]?.label).toBe('Nunca')
        expect(normalized.blocks[0]?.title).toBe('Lo que vives')
        expect(normalized.questions[0]?.text).toBe('¿Te humilla?')
        expect(normalized.dimensions[0]?.label).toBe('psicológica')
        expect(normalized.recognition.prefix).toBe('Aparecen señales de')
        expect(normalized.municipalities[0]?.label).toBe('Girón')
        expect(normalized.levels[0]?.advice).toEqual(['consejo'])
    })

    it('conserva contexto, banderas y accesos intactos (solo re-numera y recorta)', () => {
        const normalized = normalizeForm(baseForm())
        expect(normalized.questions[1]?.context).toBe(true)
        expect(normalized.thresholdFlags[0]).toEqual({
            questionId: 'p1',
            minValue: 2,
            levelId: 'grave',
        })
        expect(normalized.levels[0]?.lineIds).toEqual(['l02'])
        expect(normalized.levels[0]?.municipalAgencyIds).toEqual({ giron: ['a11'] })
    })

    it('omite reviewNote vacía y conserva recortada la que tiene contenido', () => {
        const emptied = normalizeForm({ ...baseForm(), reviewNote: '   ' })
        expect('reviewNote' in emptied).toBe(false)
        const kept = normalizeForm({ ...baseForm(), reviewNote: ' nota interna ' })
        expect(kept.reviewNote).toBe('nota interna')
    })
})

describe('formFromRaw', () => {
    it('ordena bloques, preguntas, dimensiones, municipios y niveles por `order` al leer', () => {
        const raw = {
            schemaVersion: 2,
            version: 1,
            title: 't',
            intro: 'i',
            notice: 'n',
            scales: [
                {
                    id: 'freq',
                    options: [
                        { id: 'f1', order: 2, label: 'B', value: 1 },
                        { id: 'f0', order: 1, label: 'A', value: 0 },
                    ],
                },
            ],
            blocks: [
                { id: 'b', order: 2, title: 'B' },
                { id: 'a', order: 1, title: 'A' },
            ],
            questions: [
                { id: 'q02', order: 2, blockId: 'a', scaleId: 'freq', text: 'dos', weight: 1 },
                { id: 'q01', order: 1, blockId: 'a', scaleId: 'freq', text: 'uno', weight: 1 },
            ],
            levels: [],
        }
        const form = formFromRaw(raw)
        expect(form.scales[0]?.options.map((option) => option.id)).toEqual(['f0', 'f1'])
        expect(form.blocks.map((block) => block.id)).toEqual(['a', 'b'])
        expect(form.questions.map((question) => question.id)).toEqual(['q01', 'q02'])
    })

    it('lee context como booleano y recognition como objeto anidado', () => {
        const form = formFromRaw({
            schemaVersion: 2,
            version: 1,
            questions: [{ id: 'p0', context: true }],
            recognition: { prefix: 'P', empty: 'E' },
        })
        expect(form.questions[0]?.context).toBe(true)
        expect(form.recognition).toEqual({ prefix: 'P', empty: 'E' })
    })

    it('lee municipalAgencyIds como mapa municipio → ids', () => {
        const form = formFromRaw({
            schemaVersion: 2,
            version: 1,
            levels: [
                { id: 'n01', municipalAgencyIds: { giron: ['a11'], bucaramanga: ['a07', 'a08'] } },
            ],
        })
        expect(form.levels[0]?.municipalAgencyIds).toEqual({
            giron: ['a11'],
            bucaramanga: ['a07', 'a08'],
        })
    })

    it('solo incluye reviewNote cuando existe (exactOptionalPropertyTypes)', () => {
        const form = formFromRaw({ schemaVersion: 2, version: 1 })
        expect('reviewNote' in form).toBe(false)
        const withNote = formFromRaw({ schemaVersion: 2, version: 1, reviewNote: 'x' })
        expect(withNote.reviewNote).toBe('x')
    })
})

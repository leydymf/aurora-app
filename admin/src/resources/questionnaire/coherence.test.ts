// Tests de las validaciones de coherencia del cuestionario validado v2 (RF-43)
// — las críticas: si algo de esto se publica mal, la app descarta el documento
// completo (QuestionnaireMappers.kt) o el motor orienta mal
// (CalculateGuidanceLevelUseCase.kt). El fixture es un instrumento compacto que
// ejercita las dos vías: dimensiones (Track 1) y umbrales + banderas (Track 2),
// con accesos por municipio. Máximo alcanzable: 1×3 + 2×3 + 3×3 = 18.
import { describe, expect, it } from 'vitest'
import {
    maxAchievableScore,
    validatePublishVersion,
    validateQuestionnaire,
    type PublishedDirectory,
} from './coherence'
import type { FormDoc } from './formSchema'

function validForm(): FormDoc {
    return {
        schemaVersion: 2,
        version: 1,
        title: 'Reconocer tu situación.',
        intro: 'Doce preguntas cortas para orientarte.',
        notice: 'Esto es una orientación preliminar, no un diagnóstico.',
        scales: [
            {
                id: 'freq',
                options: [
                    { id: 'f0', order: 1, label: 'Nunca', value: 0 },
                    { id: 'f1', order: 2, label: 'Una vez', value: 1 },
                    { id: 'f2', order: 3, label: 'Varias veces', value: 2 },
                    { id: 'f3', order: 4, label: 'Muchas veces', value: 3 },
                ],
            },
            {
                id: 'link',
                options: [
                    { id: 'k0', order: 1, label: 'Pareja', value: 0 },
                    { id: 'k1', order: 2, label: 'Familiar', value: 0 },
                ],
            },
        ],
        blocks: [
            { id: 'a', order: 1, title: 'Lo que estás viviendo' },
            { id: 'b', order: 2, title: 'Tu seguridad' },
        ],
        questions: [
            {
                id: 'p0',
                order: 1,
                blockId: 'a',
                scaleId: 'link',
                text: '¿Con quién?',
                weight: 0,
                context: true,
            },
            {
                id: 'p1',
                order: 2,
                blockId: 'a',
                scaleId: 'freq',
                text: '¿Te humilla?',
                weight: 1,
                context: false,
            },
            {
                id: 'p2',
                order: 3,
                blockId: 'a',
                scaleId: 'freq',
                text: '¿Te controla?',
                weight: 2,
                context: false,
            },
            {
                id: 'p3',
                order: 4,
                blockId: 'b',
                scaleId: 'freq',
                text: '¿Te amenaza?',
                weight: 3,
                context: false,
            },
        ],
        dimensions: [
            {
                id: 'psico',
                order: 1,
                label: 'violencia psicológica',
                questionIds: ['p1', 'p2'],
                minValue: 1,
            },
            { id: 'fisica', order: 2, label: 'violencia física', questionIds: ['p3'], minValue: 1 },
        ],
        recognition: {
            prefix: 'Por tus respuestas, aparecen señales de',
            empty: 'No aparecen señales claras.',
        },
        thresholdFlags: [{ questionId: 'p3', minValue: 3, levelId: 'extremo' }],
        countFlags: [
            { questionIds: ['p1', 'p3'], eachMinValue: 1, countAtLeast: 2, levelId: 'extremo' },
        ],
        municipalities: [
            { id: 'giron', order: 1, label: 'Girón' },
            { id: 'bucaramanga', order: 2, label: 'Bucaramanga' },
        ],
        levels: [
            {
                id: 'variable',
                order: 1,
                minScore: 0,
                title: 'Para tener presente',
                summary: 'Sin señales fuertes.',
                advice: ['Guarda a mano las líneas de atención.'],
                lineIds: ['l02'],
                agencyIds: [],
                municipalAgencyIds: {},
            },
            {
                id: 'moderado',
                order: 2,
                minScore: 5,
                title: 'Vale la pena que te orientes',
                summary: 'Situaciones que conviene no dejar pasar.',
                advice: ['Comunícate con la Línea 155.'],
                lineIds: ['l02'],
                agencyIds: ['a13'],
                municipalAgencyIds: { giron: ['a11'], bucaramanga: ['a07'] },
            },
            {
                id: 'grave',
                order: 3,
                minScore: 10,
                title: 'Es importante que busques apoyo pronto',
                summary: 'Situaciones serias.',
                advice: ['Acude a una Comisaría de Familia.'],
                lineIds: ['l02'],
                agencyIds: [],
                municipalAgencyIds: {},
            },
            {
                id: 'extremo',
                order: 4,
                minScore: 15,
                title: 'Tu seguridad puede estar en riesgo ahora',
                summary: 'Peligro alto.',
                advice: ['Si el peligro es ahora, llama al 123.'],
                lineIds: ['l02'],
                agencyIds: [],
                municipalAgencyIds: {},
            },
        ],
    }
}

const published: PublishedDirectory = {
    lineIds: new Set(['l02']),
    agencyIds: new Set(['a13', 'a11', 'a07']),
    lineNames: new Map([['l02', 'Línea 155']]),
    agencyNames: new Map([
        ['a13', 'Centro Integral de la Mujer'],
        ['a11', 'Comisaría de Familia, Girón'],
        ['a07', 'Comisaría de Familia La Joya'],
    ]),
}

function issuesOf(form: FormDoc, dir: PublishedDirectory = published): string[] {
    return validateQuestionnaire(form, dir).map((issue) => issue.message)
}

describe('maxAchievableScore (Σ peso × valor máximo de la escala de cada pregunta)', () => {
    it('calcula 18 para el fixture (1×3 + 2×3 + 3×3; la de contexto no suma)', () => {
        expect(maxAchievableScore(validForm())).toBe(18)
    })

    it('es 0 sin escalas o sin preguntas', () => {
        expect(maxAchievableScore({ ...validForm(), scales: [] })).toBe(0)
        expect(maxAchievableScore({ ...validForm(), questions: [] })).toBe(0)
    })
})

describe('validateQuestionnaire — configuración válida', () => {
    it('acepta el instrumento completo cuando todo lo referenciado está publicado', () => {
        expect(validateQuestionnaire(validForm(), published)).toEqual([])
    })
})

describe('validateQuestionnaire — completitud (criterio 2 de RF-43)', () => {
    it('bloquea un cuestionario sin preguntas', () => {
        expect(issuesOf({ ...validForm(), questions: [] }).join(' ')).toContain('Sin preguntas')
    })

    it('bloquea un cuestionario sin niveles', () => {
        expect(issuesOf({ ...validForm(), levels: [] }).join(' ')).toContain('Sin niveles')
    })

    it('bloquea sin dimensiones y sin municipios', () => {
        expect(issuesOf({ ...validForm(), dimensions: [] }).join(' ')).toContain(
            'al menos una dimensión',
        )
        expect(issuesOf({ ...validForm(), municipalities: [] }).join(' ')).toContain(
            'al menos un municipio',
        )
    })

    it('bloquea metadatos y reconocimiento vacíos', () => {
        const issues = validateQuestionnaire(
            {
                ...validForm(),
                title: ' ',
                intro: '',
                notice: '',
                recognition: { prefix: '', empty: '' },
            },
            published,
        )
        expect(issues.map((issue) => issue.field)).toEqual(
            expect.arrayContaining(['title', 'intro', 'notice', 'recognition']),
        )
    })
})

describe('validateQuestionnaire — umbrales (contiguos, sin huecos, alcanzables)', () => {
    it('exige un nivel de arranque con umbral 0', () => {
        const form = validForm()
        const first = form.levels[0]
        if (first) first.minScore = 1
        expect(issuesOf(form).join(' ')).toContain('umbral 0')
    })

    it('bloquea umbrales repetidos (a igual minScore el motor sobre-orienta)', () => {
        const form = validForm()
        const second = form.levels[1]
        if (second) second.minScore = 10
        expect(issuesOf(form).join(' ')).toContain('crecer estrictamente')
    })

    it('bloquea un nivel inalcanzable (umbral > 18)', () => {
        const form = validForm()
        const last = form.levels[3]
        if (last) last.minScore = 19
        expect(issuesOf(form).join(' ')).toContain('inalcanzable')
    })

    it('acepta un umbral exactamente en el máximo alcanzable (18)', () => {
        const form = validForm()
        const last = form.levels[3]
        if (last) last.minScore = 18
        expect(issuesOf(form)).toEqual([])
    })
})

describe('validateQuestionnaire — pesos, contexto y escalas', () => {
    it('bloquea una pregunta puntuable con peso 0, negativo o no entero', () => {
        for (const weight of [0, -1, 1.5]) {
            const form = validForm()
            const scored = form.questions[1]
            if (scored) scored.weight = weight
            expect(issuesOf(form).join(' '), `peso ${String(weight)}`).toMatch(/≥ 1|entero ≥ 0/)
        }
    })

    it('exige peso 0 en la pregunta de contexto', () => {
        const form = validForm()
        const context = form.questions[0]
        if (context) context.weight = 2
        expect(issuesOf(form).join(' ')).toContain('su peso debe ser 0')
    })

    it('bloquea valores de escala repetidos y escalas con menos de 2 opciones', () => {
        const repeated = validForm()
        const option = repeated.scales[0]?.options[2]
        if (option) option.value = 1
        expect(issuesOf(repeated).join(' ')).toContain('repetido')

        const short = validForm()
        const freq = short.scales[0]
        if (freq) freq.options = freq.options.slice(0, 1)
        expect(issuesOf(short).join(' ')).toContain('al menos 2 opciones')
    })
})

describe('validateQuestionnaire — referencias estructurales', () => {
    it('bloquea una pregunta que referencia un bloque o una escala inexistente', () => {
        const badBlock = validForm()
        const q1 = badBlock.questions[1]
        if (q1) q1.blockId = 'nope'
        expect(issuesOf(badBlock).join(' ')).toContain('bloque inexistente')

        const badScale = validForm()
        const q2 = badScale.questions[1]
        if (q2) q2.scaleId = 'nope'
        expect(issuesOf(badScale).join(' ')).toContain('escala inexistente')
    })

    it('bloquea una dimensión que referencia una pregunta inexistente o queda vacía', () => {
        const dangling = validForm()
        const dim = dangling.dimensions[0]
        if (dim) dim.questionIds = ['zz']
        expect(issuesOf(dangling).join(' ')).toContain('pregunta inexistente')

        const empty = validForm()
        const dim2 = empty.dimensions[0]
        if (dim2) dim2.questionIds = []
        expect(issuesOf(empty).join(' ')).toContain('no referencia ninguna pregunta')
    })
})

describe('validateQuestionnaire — banderas bien formadas (§5.3)', () => {
    it('bloquea una bandera de umbral hacia una pregunta o nivel inexistente', () => {
        const badQuestion = validForm()
        const flag = badQuestion.thresholdFlags[0]
        if (flag) flag.questionId = 'zz'
        expect(issuesOf(badQuestion).join(' ')).toContain('pregunta inexistente')

        const badLevel = validForm()
        const flag2 = badLevel.thresholdFlags[0]
        if (flag2) flag2.levelId = 'n99'
        expect(issuesOf(badLevel).join(' ')).toContain('nivel inexistente')
    })

    it('bloquea un umbral de bandera fuera del rango de la escala de la pregunta', () => {
        const form = validForm()
        const flag = form.thresholdFlags[0]
        if (flag) flag.minValue = 5 // la escala freq llega a 3
        expect(issuesOf(form).join(' ')).toContain('valor máximo')
    })

    it('bloquea un conteo mayor que la cantidad de preguntas que agrupa', () => {
        const form = validForm()
        const flag = form.countFlags[0]
        if (flag) flag.countAtLeast = 3 // solo agrupa 2 preguntas
        expect(issuesOf(form).join(' ')).toContain('cantidad de preguntas')
    })
})

describe('validateQuestionnaire — accesos por municipio', () => {
    it('bloquea una línea o entidad de acceso sin publicar', () => {
        const noLine: PublishedDirectory = { ...published, lineIds: new Set() }
        expect(issuesOf(validForm(), noLine).join(' ')).toContain('no está publicada')

        const noAgency: PublishedDirectory = { ...published, agencyIds: new Set(['a11', 'a07']) }
        expect(issuesOf(validForm(), noAgency).join(' ')).toContain('no está publicada')
    })

    it('bloquea accesos para un municipio inexistente', () => {
        const form = validForm()
        const moderado = form.levels[1]
        if (moderado) moderado.municipalAgencyIds = { xxxx: ['a11'] }
        expect(issuesOf(form).join(' ')).toContain('municipio inexistente')
    })
})

describe('validateQuestionnaire — compuertas de esquema y marcador', () => {
    it('bloquea un schemaVersion distinto del soportado', () => {
        expect(issuesOf({ ...validForm(), schemaVersion: 1 }).join(' ')).toContain('schemaVersion')
    })

    it('bloquea el marcador [POR CONFIRMAR] aunque esté en la nota interna', () => {
        const form = { ...validForm(), reviewNote: '[POR CONFIRMAR: pendiente]' }
        expect(issuesOf(form).join(' ')).toContain('[POR CONFIRMAR]')
    })
})

describe('validatePublishVersion (la app rige por el vivo de mayor versión)', () => {
    it('acepta un borrador cuya versión supera la publicada más alta', () => {
        expect(validatePublishVersion({ ...validForm(), version: 3 }, 2)).toEqual([])
    })

    it('bloquea empates y versiones menores', () => {
        expect(
            validatePublishVersion({ ...validForm(), version: 2 }, 2)
                .map((issue) => issue.message)
                .join(' '),
        ).toContain('no supera')
        expect(validatePublishVersion({ ...validForm(), version: 1 }, 2)).toHaveLength(1)
    })
})

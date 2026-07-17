// Tests de las validaciones de coherencia del cuestionario (RF-43) — las
// críticas: si algo de esto se publica mal, la app descarta el documento
// completo (QuestionnaireMappers.kt) o el motor orienta mal
// (CalculateGuidanceLevelUseCase.kt). El fixture replica el f01 real de
// tools/seed/content/forms.json (pesos 1,1,3,3,3,2 · escala 0,1,2 · umbrales
// 0,4,10,17 · máximo alcanzable 13×2 = 26).
import { describe, expect, it } from 'vitest'
import { maxAchievableScore, validatePublishVersion, validateQuestionnaire } from './coherence'
import type { FormDoc } from './formSchema'

function realForm(): FormDoc {
    return {
        schemaVersion: 1,
        version: 1,
        title: 'Reconocer tu situación.',
        intro: 'Seis preguntas cortas para ayudarte a poner en palabras lo que vives.',
        notice: 'Es una orientación, no un diagnóstico.',
        scale: [
            { id: 's0', order: 1, label: 'Nunca', value: 0 },
            { id: 's1', order: 2, label: 'Alguna vez', value: 1 },
            { id: 's2', order: 3, label: 'Con frecuencia', value: 2 },
        ],
        questions: [
            { id: 'q01', order: 1, text: '¿Controla lo que haces?', weight: 1 },
            { id: 'q02', order: 2, text: '¿Te humilla o te aísla?', weight: 1 },
            {
                id: 'q03',
                order: 3,
                text: '¿Te ha causado daño físico?',
                weight: 3,
                alertLevelId: 'n03',
            },
            { id: 'q04', order: 4, text: '¿Te ha amenazado?', weight: 3, alertLevelId: 'n04' },
            {
                id: 'q05',
                order: 5,
                text: '¿Te ha presionado sexualmente?',
                weight: 3,
                alertLevelId: 'n03',
            },
            { id: 'q06', order: 6, text: '¿Sientes miedo?', weight: 2, alertLevelId: 'n03' },
        ],
        levels: [
            {
                id: 'n01',
                order: 1,
                minScore: 0,
                title: 'Variable',
                summary: 'Sin patrón de riesgo alto.',
                advice: ['Explora la sección de información.'],
                routeIds: ['r01'],
            },
            {
                id: 'n02',
                order: 2,
                minScore: 4,
                title: 'Moderado',
                summary: 'Señales que merecen atención.',
                advice: ['Habla con alguien de confianza.'],
                routeIds: ['r01', 'r02'],
            },
            {
                id: 'n03',
                order: 3,
                minScore: 10,
                title: 'Grave',
                summary: 'Riesgo serio para tu integridad.',
                advice: ['Acude a la Comisaría de Familia.'],
                routeIds: ['r02', 'r03', 'r01'],
            },
            {
                id: 'n04',
                order: 4,
                minScore: 17,
                title: 'Extremo',
                summary: 'Tu seguridad puede estar en peligro.',
                advice: ['Si el peligro es ahora, llama al 123.'],
                routeIds: ['r02', 'r03'],
            },
        ],
    }
}

const publishedRoutes = new Set(['r01', 'r02', 'r03'])
const routeNames = new Map([
    ['r01', 'Salud'],
    ['r02', 'Protección'],
    ['r03', 'Justicia'],
])

function issuesOf(form: FormDoc, published = publishedRoutes): string[] {
    return validateQuestionnaire(form, published, routeNames).map((issue) => issue.message)
}

describe('maxAchievableScore (derivado del motor: Σ pesos × valor máximo)', () => {
    it('calcula 26 para la configuración real (13 de pesos × valor máximo 2)', () => {
        expect(maxAchievableScore(realForm())).toBe(26)
    })

    it('es 0 sin escala o sin preguntas', () => {
        expect(maxAchievableScore({ ...realForm(), scale: [] })).toBe(0)
        expect(maxAchievableScore({ ...realForm(), questions: [] })).toBe(0)
    })
})

describe('validateQuestionnaire — configuración válida', () => {
    it('acepta la configuración real del seed (f01)', () => {
        expect(validateQuestionnaire(realForm(), publishedRoutes, routeNames)).toEqual([])
    })
})

describe('validateQuestionnaire — completitud (criterio 2 de RF-43)', () => {
    it('bloquea un cuestionario sin preguntas', () => {
        expect(issuesOf({ ...realForm(), questions: [] }).join(' ')).toContain('Sin preguntas')
    })

    it('bloquea un cuestionario sin niveles', () => {
        expect(issuesOf({ ...realForm(), levels: [] }).join(' ')).toContain('Sin niveles')
    })

    it('bloquea metadatos vacíos (la app descarta el documento completo)', () => {
        const issues = validateQuestionnaire(
            { ...realForm(), title: ' ', intro: '', notice: '' },
            publishedRoutes,
            routeNames,
        )
        expect(issues.map((issue) => issue.field)).toEqual(
            expect.arrayContaining(['title', 'intro', 'notice']),
        )
    })
})

describe('validateQuestionnaire — umbrales (ordenados, sin huecos, alcanzables)', () => {
    it('exige un nivel de arranque con umbral 0 (evita el fallback del motor)', () => {
        const form = realForm()
        const first = form.levels[0]
        if (first) first.minScore = 1
        expect(issuesOf(form).join(' ')).toContain('umbral 0')
    })

    it('bloquea umbrales repetidos (a igual minScore el motor sobre-orienta: config ambigua)', () => {
        const form = realForm()
        const second = form.levels[1]
        if (second) second.minScore = 10
        expect(issuesOf(form).join(' ')).toContain('crecer estrictamente')
    })

    it('bloquea umbrales fuera de orden respecto a la severidad', () => {
        const form = realForm()
        const third = form.levels[2]
        if (third) third.minScore = 3
        expect(issuesOf(form).join(' ')).toContain('crecer estrictamente')
    })

    it('bloquea un nivel inalcanzable (umbral > 26 en la configuración real)', () => {
        const form = realForm()
        const last = form.levels[3]
        if (last) last.minScore = 27
        expect(issuesOf(form).join(' ')).toContain('inalcanzable')
    })

    it('acepta un umbral exactamente en el máximo alcanzable (26)', () => {
        const form = realForm()
        const last = form.levels[3]
        if (last) last.minScore = 26
        expect(issuesOf(form)).toEqual([])
    })
})

describe('validateQuestionnaire — pesos y escala (rango que espera el motor)', () => {
    it('bloquea peso 0 (anula la pregunta) y pesos negativos o no enteros', () => {
        for (const weight of [0, -1, 1.5]) {
            const form = realForm()
            const first = form.questions[0]
            if (first) first.weight = weight
            expect(issuesOf(form).join(' '), `peso ${String(weight)}`).toContain('entero ≥ 1')
        }
    })

    it('bloquea valores de escala repetidos (ancla ambigua del piso crítico)', () => {
        const form = realForm()
        const last = form.scale[2]
        if (last) last.value = 1
        expect(issuesOf(form).join(' ')).toContain('repetido')
    })

    it('bloquea una escala con menos de 2 opciones', () => {
        const form = realForm()
        form.scale = form.scale.slice(0, 1)
        expect(issuesOf(form).join(' ')).toContain('al menos 2 opciones')
    })

    it('bloquea valores que no crecen con el orden (la última opción es la más severa)', () => {
        const form = realForm()
        form.scale = [
            { id: 's0', order: 1, label: 'Nunca', value: 2 },
            { id: 's1', order: 2, label: 'Alguna vez', value: 1 },
            { id: 's2', order: 3, label: 'Con frecuencia', value: 0 },
        ]
        expect(issuesOf(form).join(' ')).toContain('crecer con el orden')
    })
})

describe('validateQuestionnaire — referencias cruzadas', () => {
    it('bloquea un alertLevelId que no apunta a ningún nivel', () => {
        const form = realForm()
        const q3 = form.questions[2]
        if (q3) q3.alertLevelId = 'n99'
        expect(issuesOf(form).join(' ')).toContain('n99')
    })

    it('bloquea una ruta recomendada que no está publicada', () => {
        const issues = issuesOf(realForm(), new Set(['r01', 'r02']))
        expect(issues.join(' ')).toContain('Justicia')
    })

    it('permite un nivel sin rutas (la app solo oculta el enlace)', () => {
        const form = realForm()
        const first = form.levels[0]
        if (first) first.routeIds = []
        expect(issuesOf(form)).toEqual([])
    })
})

describe('validateQuestionnaire — compuerta [POR CONFIRMAR] (M-2)', () => {
    it('bloquea el marcador aunque esté en la nota interna (reviewNote)', () => {
        const form = { ...realForm(), reviewNote: '[POR CONFIRMAR: pendiente P-38]' }
        expect(issuesOf(form).join(' ')).toContain('[POR CONFIRMAR]')
    })
})

describe('validateQuestionnaire — schemaVersion', () => {
    it('bloquea un schemaVersion distinto del soportado (la app lo ignoraría)', () => {
        expect(issuesOf({ ...realForm(), schemaVersion: 2 }).join(' ')).toContain('schemaVersion')
    })
})

describe('validatePublishVersion (la app rige por el vivo de mayor versión)', () => {
    it('acepta un borrador cuya versión supera la publicada más alta', () => {
        expect(validatePublishVersion({ ...realForm(), version: 3 }, 2)).toEqual([])
    })

    it('bloquea empates y versiones menores (otro cuestionario se publicó en medio)', () => {
        expect(
            validatePublishVersion({ ...realForm(), version: 2 }, 2)
                .map((issue) => issue.message)
                .join(' '),
        ).toContain('no supera')
        expect(validatePublishVersion({ ...realForm(), version: 1 }, 2)).toHaveLength(1)
    })
})

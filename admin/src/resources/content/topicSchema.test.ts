import { describe, expect, it } from 'vitest'
import {
    normalizeTopic,
    SCHEMA_VERSION,
    topicFromRaw,
    validateTopic,
    type TopicDoc,
} from './topicSchema'

function validTopic(): TopicDoc {
    return {
        schemaVersion: SCHEMA_VERSION,
        order: 1,
        title: 'Violencia física',
        summary: 'Cualquier acción que ponga en riesgo tu cuerpo o tu salud.',
        signals: ['Golpes o empujones.'],
        examples: ['Te empuja durante una pelea.'],
        icon: 'figure',
        accent: 'blue',
    }
}

const noOrders = new Map<number, string>()

function fields(topic: TopicDoc, orders = noOrders): string[] {
    return validateTopic(topic, orders).map((issue) => issue.field)
}

describe('validateTopic (validación dura para publicar, RF-41)', () => {
    it('acepta un topic completo', () => {
        expect(validateTopic(validTopic(), noOrders)).toEqual([])
    })

    it('exige título y resumen (espejo de ContentMappers.kt)', () => {
        expect(fields({ ...validTopic(), title: '  ' })).toContain('title')
        expect(fields({ ...validTopic(), summary: '' })).toContain('summary')
    })

    it('exige orden entero mayor o igual a 1', () => {
        expect(fields({ ...validTopic(), order: 0 })).toContain('order')
        expect(fields({ ...validTopic(), order: 1.5 })).toContain('order')
    })

    it('rechaza un orden que ya usa otro topic', () => {
        const orders = new Map([[1, 'Violencia psicológica']])
        expect(fields(validTopic(), orders)).toContain('order')
    })

    it('rechaza señales o ejemplos vacíos', () => {
        expect(fields({ ...validTopic(), signals: ['ok', '  '] })).toContain('signals')
        expect(fields({ ...validTopic(), examples: [''] })).toContain('examples')
    })

    it('bloquea un schemaVersion distinto del soportado (isSupportedSchema de la app)', () => {
        expect(fields({ ...validTopic(), schemaVersion: 1 })).toContain('general')
        expect(fields({ ...validTopic(), schemaVersion: 3 })).toContain('general')
    })

    it('rechaza icon o accent fuera de la biblioteca cerrada (esquema v2)', () => {
        expect(fields({ ...validTopic(), icon: 'unicornio' })).toContain('icon')
        expect(fields({ ...validTopic(), icon: '' })).toContain('icon')
        expect(fields({ ...validTopic(), accent: 'fucsia' })).toContain('accent')
        // El violeta de la nube no existe como id de dato: es exclusivo de la
        // salida rápida de la app (anexo v2-29).
        expect(fields({ ...validTopic(), accent: 'violet' })).toContain('accent')
        expect(fields({ ...validTopic(), accent: 'cloud' })).toContain('accent')
    })

    it('acepta cualquier icono y acento de la biblioteca, sin pares fijos', () => {
        // La elección es libre DENTRO de la biblioteca: no se exige la pareja
        // del mapeo automático.
        expect(
            validateTopic({ ...validTopic(), icon: 'device', accent: 'lilac' }, noOrders),
        ).toEqual([])
    })

    it('bloquea el marcador [POR CONFIRMAR] en cualquier campo (compuerta M-2)', () => {
        expect(fields({ ...validTopic(), summary: 'Sede [POR CONFIRMAR dirección]' })).toContain(
            'general',
        )
        // Sin distinguir mayúsculas, como el DRAFT_MARKER de tools/seed/seed.mjs.
        expect(fields({ ...validTopic(), signals: ['dato [por confirmar]'] })).toContain('general')
    })
})

describe('topicFromRaw (lectura tolerante, espejo de TopicDto)', () => {
    it('aplica valores por defecto seguros ante un documento vacío', () => {
        expect(topicFromRaw({})).toEqual({
            schemaVersion: SCHEMA_VERSION,
            order: 0,
            title: '',
            summary: '',
            signals: [],
            examples: [],
            icon: '',
            accent: '',
        })
    })

    it('conserva exactamente los campos del esquema', () => {
        const raw = {
            schemaVersion: 2,
            order: 2,
            title: 'Violencia psicológica',
            summary: 'Busca degradarte o controlarte.',
            signals: ['Te humilla.'],
            examples: ['Revisa tu celular.'],
            icon: 'lobes',
            accent: 'lilac',
        }
        expect(topicFromRaw(raw)).toEqual(raw)
    })

    it('un documento v1 conserva su versión y queda sin selección visual', () => {
        // La lectura es tolerante (nunca inventa datos): el ascenso a v2
        // ocurre al GUARDAR (normalizeTopic), no al leer.
        const legacy = topicFromRaw({ schemaVersion: 1, order: 1, title: 'Física' })
        expect(legacy.schemaVersion).toBe(1)
        expect(legacy.icon).toBe('')
        expect(legacy.accent).toBe('')
    })
})

describe('normalizeTopic', () => {
    it('recorta espacios accidentales sin eliminar ítems', () => {
        const normalized = normalizeTopic({
            ...validTopic(),
            title: '  Violencia física ',
            signals: [' con espacios ', ''],
        })
        expect(normalized.title).toBe('Violencia física')
        expect(normalized.signals).toEqual(['con espacios', ''])
    })

    it('sube un topic v1 al esquema vigente al guardar (la app soporta 1..2)', () => {
        const normalized = normalizeTopic({ ...validTopic(), schemaVersion: 1 })
        expect(normalized.schemaVersion).toBe(SCHEMA_VERSION)
    })
})

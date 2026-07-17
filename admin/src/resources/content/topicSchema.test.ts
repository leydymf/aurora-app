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
        expect(fields({ ...validTopic(), schemaVersion: 2 })).toContain('general')
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
        })
    })

    it('conserva exactamente los campos del esquema', () => {
        const raw = {
            schemaVersion: 1,
            order: 2,
            title: 'Violencia psicológica',
            summary: 'Busca degradarte o controlarte.',
            signals: ['Te humilla.'],
            examples: ['Revisa tu celular.'],
        }
        expect(topicFromRaw(raw)).toEqual(raw)
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
})

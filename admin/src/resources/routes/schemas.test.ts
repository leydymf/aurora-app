import { describe, expect, it } from 'vitest'
import {
    emptyAgency,
    normalizeAgency,
    validateAgency,
    validateLine,
    validateRoute,
    type AgencyDoc,
    type LineDoc,
    type RouteDoc,
} from './schemas'

const noOrders = new Map<number, string>()

function validRoute(): RouteDoc {
    return {
        schemaVersion: 1,
        order: 1,
        title: 'Salud',
        summary: 'El sector salud te atiende de forma gratuita e inmediata.',
        agencyIds: ['a01'],
    }
}

function validAgency(): AgencyDoc {
    return {
        schemaVersion: 1,
        name: 'Policía Nacional — cuadrante',
        role: 'Atiende la emergencia en el momento.',
        whenToUse: 'Cuando estás en peligro ahora mismo.',
        phones: ['123'],
        address: null,
        hours: '24 horas, todos los días',
    }
}

function validLine(): LineDoc {
    return {
        schemaVersion: 1,
        order: 1,
        name: 'Línea 155 — Orientación a mujeres',
        number: '155',
        description: 'Línea nacional y gratuita de orientación a mujeres.',
        hours: '24 horas, todos los días',
    }
}

const published = new Set(['a01', 'a02'])
const names = new Map([
    ['a01', 'Urgencias'],
    ['a09', 'Entidad nueva'],
])

describe('validateRoute (integridad referencial RF-42)', () => {
    it('acepta una ruta completa cuyas entidades están publicadas', () => {
        expect(validateRoute(validRoute(), noOrders, published, names)).toEqual([])
    })

    it('bloquea publicar una ruta que apunta a una entidad NO publicada', () => {
        const route = { ...validRoute(), agencyIds: ['a01', 'a09'] }
        const issues = validateRoute(route, noOrders, published, names)
        expect(issues.map((issue) => issue.field)).toContain('agencyIds')
        expect(issues.map((issue) => issue.message).join(' ')).toContain('Entidad nueva')
    })

    it('bloquea una referencia a una entidad inexistente (muestra el id)', () => {
        const route = { ...validRoute(), agencyIds: ['a99'] }
        const issues = validateRoute(route, noOrders, published, names)
        expect(issues.map((issue) => issue.message).join(' ')).toContain('a99')
    })

    it('una ruta sin entidades es válida (la app la pinta vacía): no bloquea', () => {
        const route = { ...validRoute(), agencyIds: [] }
        expect(validateRoute(route, noOrders, published, names)).toEqual([])
    })

    it('exige título, resumen y orden único (espejo de ContentMappers.kt)', () => {
        expect(
            validateRoute({ ...validRoute(), title: ' ' }, noOrders, published, names).map(
                (issue) => issue.field,
            ),
        ).toContain('title')
        const taken = new Map([[1, 'Protección']])
        expect(
            validateRoute(validRoute(), taken, published, names).map((issue) => issue.field),
        ).toContain('order')
    })
})

describe('validateAgency (validación dura de teléfonos RF-42)', () => {
    it('acepta una entidad completa; teléfonos y dirección pueden faltar', () => {
        expect(validateAgency(validAgency())).toEqual([])
        expect(validateAgency({ ...validAgency(), phones: [], address: null })).toEqual([])
    })

    it('bloquea un teléfono no marcable (extensión, formato viejo)', () => {
        const withExt = { ...validAgency(), phones: ['(607) 633 7000 ext. 101'] }
        expect(validateAgency(withExt).map((issue) => issue.field)).toContain('phones')
        const old = { ...validAgency(), phones: ['6337000'] }
        expect(validateAgency(old).map((issue) => issue.field)).toContain('phones')
    })

    it('exige nombre, qué hace y cuándo acudir (espejo de ContentMappers.kt)', () => {
        const fields = validateAgency({
            ...validAgency(),
            name: '',
            role: ' ',
            whenToUse: '',
        }).map((issue) => issue.field)
        expect(fields).toEqual(expect.arrayContaining(['name', 'role', 'whenToUse']))
    })

    it('bloquea el marcador [POR CONFIRMAR] (compuerta M-2)', () => {
        const marked = { ...validAgency(), address: 'Carrera 33 [POR CONFIRMAR]' }
        expect(validateAgency(marked).map((issue) => issue.field)).toContain('general')
    })

    it('bloquea un schemaVersion distinto del soportado', () => {
        expect(
            validateAgency({ ...validAgency(), schemaVersion: 9 }).map((issue) => issue.field),
        ).toContain('general')
    })
})

describe('validateLine', () => {
    it('acepta una línea completa', () => {
        expect(validateLine(validLine(), noOrders)).toEqual([])
    })

    it('exige que el número sea marcable: es lo que abre el marcador', () => {
        const bad = { ...validLine(), number: '[POR CONFIRMAR]' }
        expect(validateLine(bad, noOrders).map((issue) => issue.field)).toContain('number')
    })

    it('exige nombre, número y descripción (espejo de ContentMappers.kt)', () => {
        const fields = validateLine(
            { ...validLine(), name: '', number: '', description: ' ' },
            noOrders,
        ).map((issue) => issue.field)
        expect(fields).toEqual(expect.arrayContaining(['name', 'number', 'description']))
    })
})

describe('normalizeAgency', () => {
    it('convierte los opcionales vacíos en null (esquema de la app: address/hours nulos)', () => {
        const normalized = normalizeAgency({ ...emptyAgency(), address: '  ', hours: ' x ' })
        expect(normalized.address).toBeNull()
        expect(normalized.hours).toBe('x')
    })

    it('descarta renglones de teléfono vacíos y recorta los demás', () => {
        const normalized = normalizeAgency({ ...emptyAgency(), phones: [' 123 ', '', '  '] })
        expect(normalized.phones).toEqual(['123'])
    })
})

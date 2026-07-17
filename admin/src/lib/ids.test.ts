import { describe, expect, it } from 'vitest'
import { nextOpaqueId } from './ids'

describe('nextOpaqueId', () => {
    it('continúa desde la numeración más alta existente', () => {
        expect(nextOpaqueId('t', ['t01', 't04', 't02'])).toBe('t05')
    })

    it('arranca en 01 cuando no hay ids previos', () => {
        expect(nextOpaqueId('t', [])).toBe('t01')
    })

    it('ignora ids de otros prefijos o mal formados', () => {
        expect(nextOpaqueId('a', ['t09', 'a02', 'ax3'])).toBe('a03')
    })

    it('respeta el relleno pedido (escala s0, s1, …)', () => {
        expect(nextOpaqueId('s', ['s0', 's1', 's2'], 1)).toBe('s3')
    })

    it('no reutiliza huecos: un id borrado no vuelve a asignarse', () => {
        expect(nextOpaqueId('t', ['t01', 't03'])).toBe('t04')
    })
})

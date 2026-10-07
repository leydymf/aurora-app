import { describe, expect, it } from 'vitest'
import { routeVisualIds, topicVisualIds } from './contentVisuals'

// Espejo del contrato de degradación de la app (ContentVisualTest.kt): la
// resolución del panel debe pintar EXACTAMENTE lo que la usuaria verá.
describe('topicVisualIds / routeVisualIds (degradación estilo RF-25)', () => {
    it('sin campos rige el mapeo automático por id', () => {
        expect(topicVisualIds('t01')).toEqual({ icon: 'figure', accent: 'blue' })
        expect(topicVisualIds('t02', '', '')).toEqual({ icon: 'lobes', accent: 'lilac' })
        expect(routeVisualIds('r01')).toEqual({ icon: 'cross', accent: 'green' })
    })

    it('un id desconocido sin campos cae al neutro', () => {
        expect(topicVisualIds('t99')).toEqual({ icon: 'book', accent: 'neutral' })
        expect(routeVisualIds('r99')).toEqual({ icon: 'pin', accent: 'neutral' })
    })

    it('una selección válida de la biblioteca se respeta', () => {
        expect(topicVisualIds('t01', 'device', 'green')).toEqual({
            icon: 'device',
            accent: 'green',
        })
    })

    it('un valor fuera de la biblioteca degrada por campo al automático', () => {
        expect(topicVisualIds('t03', 'unicornio', 'fucsia')).toEqual(topicVisualIds('t03'))
        expect(topicVisualIds('t01', 'device', 'corrupto')).toEqual({
            icon: 'device',
            accent: 'blue',
        })
    })

    it('el violeta de la nube no es seleccionable desde datos (anexo v2-29)', () => {
        expect(topicVisualIds('t01', '', 'violet')).toEqual(topicVisualIds('t01'))
        expect(topicVisualIds('t01', '', 'cloud')).toEqual(topicVisualIds('t01'))
    })
})

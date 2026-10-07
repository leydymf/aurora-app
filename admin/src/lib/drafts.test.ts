// Regla que decide qué versión edita y publica el panel (2026-10-06).
//
// Nace de un hallazgo real: `drafts/topics__t05` quedó congelado el 17/7/2026
// con el texto «Prueba» de una corrida E2E, mientras `topics/t05` llevaba el
// contenido validado por la directora publicado con `publicar-tipos`. El panel
// mostraba el borrador viejo, así que un clic en «Publicar» habría sobrescrito
// el contenido validado. La misma situación existía en t01, t04, agencies/a03 y
// forms/f01.
import { describe, expect, it } from 'vitest'
import { combine } from './drafts'

interface Topic {
    readonly title: string
    readonly summary: string
    readonly order: number
}

const live: Topic = { title: 'Violencia digital', summary: 'El texto validado', order: 5 }
const stale: Topic = { title: 'violencia digital', summary: 'Prueba', order: 5 }

describe('combine', () => {
    it('un borrador marcado publicado que difiere de lo vivo está obsoleto: se edita lo publicado', () => {
        const entry = combine('t05', 'publicado', stale, live, null, null)

        expect(entry.data).toEqual(live)
        expect(entry.staleDraftDiscarded).toBe(true)
        // No hay nada que advertir: lo que se publicaría es lo que ya está.
        expect(entry.differsFromLive).toBe(false)
    })

    it('un borrador publicado que coincide con lo vivo se usa tal cual', () => {
        const entry = combine('t06', 'publicado', live, live, null, null)

        expect(entry.data).toEqual(live)
        expect(entry.staleDraftDiscarded).toBe(false)
        expect(entry.differsFromLive).toBe(false)
    })

    it('el orden de las claves no cuenta como diferencia', () => {
        const reordered = { order: 5, summary: 'El texto validado', title: 'Violencia digital' }

        const entry = combine('t05', 'publicado', reordered, live, null, null)

        expect(entry.staleDraftDiscarded).toBe(false)
    })

    it('un borrador en edición manda, aunque difiera: es trabajo real sin publicar', () => {
        const editing: Topic = { ...live, summary: 'Una corrección en curso' }

        const entry = combine('t05', 'borrador', editing, live, null, null)

        expect(entry.data).toEqual(editing)
        expect(entry.staleDraftDiscarded).toBe(false)
        // …pero el panel avisa de que publicar cambia lo que la app muestra hoy.
        expect(entry.differsFromLive).toBe(true)
    })

    it('un borrador en edición idéntico a lo vivo no dispara ningún aviso', () => {
        const entry = combine('t05', 'borrador', live, live, null, null)

        expect(entry.differsFromLive).toBe(false)
    })

    it('sin documento vivo (recurso nuevo) manda el borrador y no hay nada que comparar', () => {
        const entry = combine('t09', 'borrador', stale, null, null, null)

        expect(entry.data).toEqual(stale)
        expect(entry.staleDraftDiscarded).toBe(false)
        expect(entry.differsFromLive).toBe(false)
    })

    it('conserva el id, el estado y las fechas del borrador', () => {
        const updatedAt = new Date('2026-07-17T15:29:50Z')
        const publishedAt = new Date('2026-07-17T15:29:50Z')

        const entry = combine('t05', 'publicado', stale, live, updatedAt, publishedAt)

        expect(entry.docId).toBe('t05')
        expect(entry.status).toBe('publicado')
        expect(entry.hasDraft).toBe(true)
        expect(entry.updatedAt).toBe(updatedAt)
        expect(entry.publishedAt).toBe(publishedAt)
        expect(entry.liveData).toEqual(live)
    })

    it('detecta diferencias dentro de listas anidadas, no solo en el primer nivel', () => {
        const withSignals = { ...live, signals: ['una señal', 'otra señal'] }
        const draftSignals = { ...live, signals: ['Prueba'] }

        const entry = combine('t05', 'publicado', draftSignals, withSignals, null, null)

        expect(entry.staleDraftDiscarded).toBe(true)
        expect(entry.data).toEqual(withSignals)
    })
})

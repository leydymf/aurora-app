import { describe, expect, it } from 'vitest'
import { isValidColombianPhone, normalizePhone, phoneIssueMessage } from './phone'

describe('isValidColombianPhone (validación dura RF-42)', () => {
    it('acepta las líneas cortas nacionales del contenido real', () => {
        // Las de tools/seed/content/lines.json: 155, 123, 141, 122.
        for (const line of ['155', '123', '141', '122', '106']) {
            expect(isValidColombianPhone(line), line).toBe(true)
        }
    })

    it('acepta celulares de 10 dígitos con y sin +57 y con separadores', () => {
        expect(isValidColombianPhone('3001234567')).toBe(true)
        expect(isValidColombianPhone('300 123 4567')).toBe(true)
        expect(isValidColombianPhone('+57 310-555-0123')).toBe(true)
    })

    it('acepta fijos unificados 60X (Santander: 607) con y sin +57', () => {
        expect(isValidColombianPhone('6076337000')).toBe(true)
        expect(isValidColombianPhone('(607) 633 7000')).toBe(true)
        expect(isValidColombianPhone('+57 601 234 5678')).toBe(true)
    })

    it('acepta la línea gratuita nacional 018000', () => {
        expect(isValidColombianPhone('018000 123 456')).toBe(true)
        expect(isValidColombianPhone('01 8000 1234567')).toBe(true)
    })

    it('rechaza extensiones: no son marcables con ACTION_DIAL', () => {
        expect(isValidColombianPhone('(607) 633 7000 ext. 101')).toBe(false)
    })

    it('rechaza formatos viejos, incompletos o marcadores sin confirmar', () => {
        expect(isValidColombianPhone('6337000')).toBe(false) // fijo de 7 dígitos (pre-2021)
        expect(isValidColombianPhone('300123456')).toBe(false) // celular de 9 dígitos
        expect(isValidColombianPhone('6096337000')).toBe(false) // indicativo 609 no asignado
        expect(isValidColombianPhone('[POR CONFIRMAR]')).toBe(false)
        expect(isValidColombianPhone('')).toBe(false)
    })
})

describe('normalizePhone', () => {
    it('quita separadores y conserva el prefijo +', () => {
        expect(normalizePhone('+57 (607) 633-70.00')).toBe('+576076337000')
    })
})

describe('phoneIssueMessage', () => {
    it('devuelve null para un teléfono válido y un mensaje para uno inválido', () => {
        expect(phoneIssueMessage('123')).toBeNull()
        expect(phoneIssueMessage('ext. 101')).toContain('no es un teléfono marcable')
    })
})

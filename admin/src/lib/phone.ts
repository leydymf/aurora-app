// Validación dura de teléfonos colombianos (RF-42). El valor guardado es
// EXACTAMENTE lo que la app pasa al marcador con ACTION_DIAL
// (content/ui/ContentElements.kt, dialNumber), así que solo se aceptan
// números marcables:
//   - línea corta nacional (123, 141, 155, 122, …): 1XX
//   - celular: 3XX XXX XXXX (10 dígitos), con +57 opcional
//   - fijo unificado (numeración de 2021): 60X + 7 dígitos, con +57 opcional
//   - gratuita nacional: 018000 + 6 o 7 dígitos
// Separadores permitidos al escribir: espacios, guiones, puntos y paréntesis.
// Se rechaza todo lo demás — incluidas extensiones («ext.»), que no son
// marcables: esa información va en el horario o en la descripción.

const SHORT_LINE = /^1\d{2}$/
const MOBILE = /^(?:\+57)?3\d{9}$/
const LANDLINE = /^(?:\+57)?60[1-8]\d{7}$/
const TOLL_FREE = /^018000\d{6,7}$/

/** Quita separadores de formato; conserva dígitos y el prefijo +. */
export function normalizePhone(raw: string): string {
    return raw.replace(/[\s\-().]/g, '')
}

export function isValidColombianPhone(raw: string): boolean {
    const phone = normalizePhone(raw)
    return (
        SHORT_LINE.test(phone) ||
        MOBILE.test(phone) ||
        LANDLINE.test(phone) ||
        TOLL_FREE.test(phone)
    )
}

/** null si el teléfono es válido; si no, el mensaje para la administradora. */
export function phoneIssueMessage(raw: string): string | null {
    if (isValidColombianPhone(raw)) return null
    return (
        `«${raw}» no es un teléfono marcable. Formatos válidos: línea corta (123), ` +
        'celular (3XX XXX XXXX), fijo (60X XXX XXXX, +57 opcional) o gratuita (018000…). ' +
        'Una extensión no se puede marcar: va en el horario o en la descripción.'
    )
}

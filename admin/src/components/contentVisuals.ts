// Espejo EXACTO de core/ui/v2/ContentVisual.kt (esquema v2, ADR 2026-08-14,
// opción a de D2): los documentos de topics y routes pueden traer `icon` y
// `accent` que SELECCIONAN dentro de la biblioteca cerrada — la biblioteca no
// crece con datos. Los ids de dato nombran el DIBUJO o el MATIZ, nunca el
// significado, porque también quedan como literales en el APK (RNF-05); el
// significado real solo se muestra aquí, en el panel interno. El mapa fijo por
// id de documento (decisión D2 original) sobrevive como contrato de
// degradación: campo vacío, desconocido o fuera de la biblioteca cae POR CAMPO
// al automático, que a su vez cae al neutro. Este archivo y ContentVisual.kt
// deben moverse juntos.
import type { AccentName, V2IconName } from './icons'

/** Ids de icono seleccionables desde los datos (espejo de selectableIcons). */
export type ContentIconId =
    | 'figure'
    | 'lobes'
    | 'slash_circle'
    | 'card'
    | 'device'
    | 'book'
    | 'cross'
    | 'shield'
    | 'scales'
    | 'pin'

/**
 * Ids de acento seleccionables desde los datos (espejo de selectableAccents).
 * El violeta de la nube queda fuera a propósito: es exclusivo de la salida
 * rápida de la app (anexo v2-29) y ni siquiera existe como id de dato.
 */
export type ContentAccentId = 'blue' | 'lilac' | 'pink' | 'sand' | 'green' | 'neutral'

export const CONTENT_ICONS: Record<ContentIconId, { icon: V2IconName; label: string }> = {
    figure: { icon: 'figure', label: 'Persona — violencia física' },
    lobes: { icon: 'lobes', label: 'Mente — violencia psicológica' },
    slash_circle: { icon: 'slashCircle', label: 'Límite — violencia sexual' },
    card: { icon: 'card', label: 'Lo material — económica y patrimonial' },
    device: { icon: 'device', label: 'Teléfono — violencia digital' },
    book: { icon: 'guide', label: 'Libro — neutro de la Guía' },
    cross: { icon: 'health', label: 'Cruz — salud' },
    shield: { icon: 'protection', label: 'Escudo — protección' },
    scales: { icon: 'justice', label: 'Balanza — justicia' },
    pin: { icon: 'place', label: 'Lugar — neutro de rutas' },
}

export const CONTENT_ACCENTS: Record<ContentAccentId, { accent: AccentName; label: string }> = {
    blue: { accent: 'protection', label: 'Azul — protección' },
    lilac: { accent: 'recognize', label: 'Lila — reconocer' },
    pink: { accent: 'notice', label: 'Rosa — aviso y cuidado' },
    sand: { accent: 'justice', label: 'Arena — justicia' },
    green: { accent: 'care', label: 'Verde — salud y cuidado' },
    neutral: { accent: 'group', label: 'Neutro — agrupa' },
}

export const CONTENT_ICON_IDS = Object.keys(CONTENT_ICONS) as readonly ContentIconId[]
export const CONTENT_ACCENT_IDS = Object.keys(CONTENT_ACCENTS) as readonly ContentAccentId[]

export function isContentIconId(value: string): value is ContentIconId {
    return (CONTENT_ICON_IDS as readonly string[]).includes(value)
}

export function isContentAccentId(value: string): value is ContentAccentId {
    return (CONTENT_ACCENT_IDS as readonly string[]).includes(value)
}

export type ContentVisualIds = {
    readonly icon: ContentIconId
    readonly accent: ContentAccentId
}

export type ContentVisual = {
    readonly icon: V2IconName
    readonly accent: AccentName
}

/** Mapeo automático de tipos (D2 original, v2-12), en ids de dato. */
function defaultTopicIds(topicId: string): ContentVisualIds {
    switch (topicId) {
        // Física — daño al cuerpo: azul de protección.
        case 't01':
            return { icon: 'figure', accent: 'blue' }
        // Psicológica — la mente: lila de reconocer.
        case 't02':
            return { icon: 'lobes', accent: 'lilac' }
        // Sexual — límite vulnerado: rosa de aviso y cuidado.
        case 't03':
            return { icon: 'slash_circle', accent: 'pink' }
        // Económica y patrimonial — lo material: arena de justicia.
        case 't04':
            return { icon: 'card', accent: 'sand' }
        // Todo id no mapeado cae al neutro con el icono de Guía (regla 5).
        default:
            return { icon: 'book', accent: 'neutral' }
    }
}

/** Mapeo automático de sectores (v2-14), en ids de dato. */
function defaultRouteIds(routeId: string): ContentVisualIds {
    switch (routeId) {
        // Salud — verde de cuidado.
        case 'r01':
            return { icon: 'cross', accent: 'green' }
        // Protección — azul y escudo.
        case 'r02':
            return { icon: 'shield', accent: 'blue' }
        // Justicia — arena y balanza.
        case 'r03':
            return { icon: 'scales', accent: 'sand' }
        default:
            return { icon: 'pin', accent: 'neutral' }
    }
}

// Degradación POR CAMPO, idéntica al `select` de la app: un valor fuera de la
// biblioteca no rompe nada — ese campo cae al automático por id.
function select(defaults: ContentVisualIds, icon: string, accent: string): ContentVisualIds {
    return {
        icon: isContentIconId(icon) ? icon : defaults.icon,
        accent: isContentAccentId(accent) ? accent : defaults.accent,
    }
}

/** Resolución en ids de dato — lo que el selector marca como selección vigente. */
export function topicVisualIds(topicId: string, icon = '', accent = ''): ContentVisualIds {
    return select(defaultTopicIds(topicId), icon, accent)
}

export function routeVisualIds(routeId: string, icon = '', accent = ''): ContentVisualIds {
    return select(defaultRouteIds(routeId), icon, accent)
}

function toVisual(ids: ContentVisualIds): ContentVisual {
    return { icon: CONTENT_ICONS[ids.icon].icon, accent: CONTENT_ACCENTS[ids.accent].accent }
}

/** Tipos de la Guía (v2-12): selección explícita con degradación al significado del tema. */
export function topicVisual(topicId: string, icon = '', accent = ''): ContentVisual {
    return toVisual(topicVisualIds(topicId, icon, accent))
}

/** Sectores de las rutas (v2-14): selección explícita con degradación al significado fijo. */
export function routeVisual(routeId: string, icon = '', accent = ''): ContentVisual {
    return toVisual(routeVisualIds(routeId, icon, accent))
}

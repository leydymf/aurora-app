// Espejo EXACTO de core/ui/v2/ContentVisual.kt (decisión D2, ratificada
// 2026-08-12): el esquema `schemaVersion` 1 no trae campo de icono/color, así
// que la APP asigna icono y matiz por id de documento con un mapa fijo hacia la
// librería cerrada, con fallback al neutro. El panel replica ese mapa solo para
// PINTAR (lista y vista previa «como lo verá la usuaria»); no ofrece selector:
// no hay dónde guardar la elección. Si algún día se quiere elegir desde el
// panel, será con `schemaVersion` nuevo (opción a de D2) — este espejo muere
// ese día junto con el mapa de la app.
import type { AccentName, V2IconName } from './icons'

export type ContentVisual = {
    readonly icon: V2IconName
    readonly accent: AccentName
}

/** Tipos de la Guía (v2-12): matiz por significado del tema. */
export function topicVisual(topicId: string): ContentVisual {
    switch (topicId) {
        // Física — daño al cuerpo: azul de protección.
        case 't01':
            return { icon: 'figure', accent: 'protection' }
        // Psicológica — la mente: lila de reconocer.
        case 't02':
            return { icon: 'lobes', accent: 'recognize' }
        // Sexual — límite vulnerado: rosa de aviso y cuidado.
        case 't03':
            return { icon: 'slashCircle', accent: 'notice' }
        // Económica y patrimonial — lo material: arena de justicia.
        case 't04':
            return { icon: 'card', accent: 'justice' }
        // Todo id no mapeado cae al neutro con el icono de Guía (regla 5).
        default:
            return { icon: 'guide', accent: 'group' }
    }
}

/** Sectores de las rutas (v2-14): significados fijos del anexo. */
export function routeVisual(routeId: string): ContentVisual {
    switch (routeId) {
        // Salud — verde de cuidado.
        case 'r01':
            return { icon: 'health', accent: 'care' }
        // Protección — azul y escudo.
        case 'r02':
            return { icon: 'protection', accent: 'protection' }
        // Justicia — arena y balanza.
        case 'r03':
            return { icon: 'justice', accent: 'justice' }
        default:
            return { icon: 'place', accent: 'group' }
    }
}

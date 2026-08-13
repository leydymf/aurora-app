// Espejo SVG de la librería CERRADA de iconografía V2 (anexo v2-29): el
// SUBCONJUNTO que el panel usa, portado 1:1 de core/ui/v2/V2Icons.kt +
// res/drawable (paths literales, mismo viewBox 24, trazo 1.9, extremos
// redondeados). Los iconos que el panel no necesita (home, settings, journal,
// cloud, media, code, close…) se omiten a propósito — la nube violeta es
// exclusiva de la app (RF-16). El panel no inventa iconos de significado: si
// un dibujo no está en la librería de la app, la app no sabe renderizarlo.
//
// Además del espejo hay un puñado de MOBILIARIO propio del panel (sobre,
// candado del campo de contraseña, lápiz, más) — navegación y formularios
// de escritorio, sin significado de contenido; mismo estilo de trazo. El panel
// es interno y sin camuflaje (admin/CLAUDE.md), por eso el candado es admisible
// aquí y vedado en la app (regla 4 del anexo).

/** Iconos con significado fijo — espejo exacto de V2Icons.kt. */
export type V2IconName =
    | 'figure' // persona — tipo «física»
    | 'lobes' // dos lóbulos — «psicológica»
    | 'slashCircle' // círculo con barra — «sexual»
    | 'card' // tarjeta — «económica»
    | 'device' // móvil — «digital»
    | 'health' // cruz suave — salud
    | 'protection' // escudo — protección
    | 'justice' // balanza — justicia
    | 'call' // auricular — llamar
    | 'recognize' // brújula — cuestionario
    | 'guide' // libro abierto — guía / contenido
    | 'date' // calendario — fecha
    | 'synced' // visto en círculo — publicado / listo
    | 'waiting' // reloj — en espera
    | 'delete' // papelera — eliminar
    | 'info' // círculo de información
    | 'place' // pin de lugar — dónde / rutas
    | 'signOut' // puerta con flecha — cerrar sesión

/** Mobiliario del panel (sin significado de contenido). */
export type PanelIconName = 'mail' | 'lock' | 'edit' | 'plus'

export type IconName = V2IconName | PanelIconName

type IconSpec = {
    readonly d: string
    /** Solo `call` trae la escala 0.88 del drawable original. */
    readonly transform?: string
    readonly strokeWidth?: number
}

const ICONS: Record<IconName, IconSpec> = {
    // ——— Espejo de V2Icons.kt (paths literales de res/drawable) ———
    figure: {
        d: 'M12,5.8 m-2.3,0 a2.3,2.3 0 1 0 4.6,0 a2.3,2.3 0 1 0 -4.6,0 M12,8.1 L12,14.6 M12,10.4 L8.2,13 M12,10.4 L15.8,13 M12,14.6 L9.2,19.6 M12,14.6 L14.8,19.6',
    },
    lobes: {
        d: 'M9.6,12 m-4.4,0 a4.4,4.4 0 1 0 8.8,0 a4.4,4.4 0 1 0 -8.8,0 M14.4,12 m-4.4,0 a4.4,4.4 0 1 0 8.8,0 a4.4,4.4 0 1 0 -8.8,0',
    },
    slashCircle: {
        d: 'M12,12 m-8,0 a8,8 0 1 0 16,0 a8,8 0 1 0 -16,0 M6.7,17.3 L17.3,6.7',
    },
    card: {
        d: 'M6,6.5 L18,6.5 A2.5,2.5 0 0 1 20.5,9 L20.5,15 A2.5,2.5 0 0 1 18,17.5 L6,17.5 A2.5,2.5 0 0 1 3.5,15 L3.5,9 A2.5,2.5 0 0 1 6,6.5 Z M3.5,10.6 L20.5,10.6',
    },
    device: {
        d: 'M9.7,3.5 L14.3,3.5 A2.2,2.2 0 0 1 16.5,5.7 L16.5,18.3 A2.2,2.2 0 0 1 14.3,20.5 L9.7,20.5 A2.2,2.2 0 0 1 7.5,18.3 L7.5,5.7 A2.2,2.2 0 0 1 9.7,3.5 Z M10.8,17.3 L13.2,17.3',
    },
    health: { d: 'M12,7.4 L12,16.6 M7.4,12 L16.6,12' },
    protection: {
        d: 'M12,3.9 L18.4,6.3 L18.4,11.4 C18.4,15.6 15.8,18.8 12,20.1 C8.2,18.8 5.6,15.6 5.6,11.4 L5.6,6.3 Z',
    },
    justice: {
        d: 'M12,5 L12,19 M9.2,19 L14.8,19 M6,7.5 L18,7.5 M6,7.5 L3.4,12.2 M6,7.5 L8.6,12.2 M3.4,12.2 A2.6,2.6 0 0 0 8.6,12.2 M18,7.5 L15.4,12.2 M18,7.5 L20.6,12.2 M15.4,12.2 A2.6,2.6 0 0 0 20.6,12.2',
    },
    call: {
        d: 'M22,16.92 v3 a2,2 0 0 1 -2.18,2 19.79,19.79 0 0 1 -8.63,-3.07 19.5,19.5 0 0 1 -6,-6 19.79,19.79 0 0 1 -3.07,-8.67 A2,2 0 0 1 4.11,2 h3 a2,2 0 0 1 2,1.72 12.84,12.84 0 0 0 0.7,2.81 2,2 0 0 1 -0.45,2.11 L8.09,9.91 a16,16 0 0 0 6,6 l1.27,-1.27 a2,2 0 0 1 2.11,-0.45 12.84,12.84 0 0 0 2.81,0.7 A2,2 0 0 1 22,16.92 Z',
        transform: 'translate(1.44 1.44) scale(0.88)',
        strokeWidth: 2.16,
    },
    recognize: {
        d: 'M20,12 A8,8 0 1 1 4,12 A8,8 0 1 1 20,12 M12,7.6 L14.1,12 L12,16.4 L9.9,12 Z',
    },
    guide: {
        d: 'M12,7.2 C10.2,5.9 7.3,5.2 4.3,5.2 L4.3,17.4 C7.3,17.4 10.2,18.1 12,19.4 C13.8,18.1 16.7,17.4 19.7,17.4 L19.7,5.2 C16.7,5.2 13.8,5.9 12,7.2 L12,19.4',
    },
    date: {
        d: 'M6.5,5.5 L17.5,5.5 A2,2 0 0 1 19.5,7.5 L19.5,17.5 A2,2 0 0 1 17.5,19.5 L6.5,19.5 A2,2 0 0 1 4.5,17.5 L4.5,7.5 A2,2 0 0 1 6.5,5.5 Z M4.5,9.7 L19.5,9.7 M8.6,3.6 L8.6,7 M15.4,3.6 L15.4,7',
    },
    synced: {
        d: 'M12,12 m-8,0 a8,8 0 1 0 16,0 a8,8 0 1 0 -16,0 M8.4,12.4 L11,15 L15.8,9.6',
    },
    waiting: {
        d: 'M12,12 m-8,0 a8,8 0 1 0 16,0 a8,8 0 1 0 -16,0 M12,7.6 L12,12 L15.2,13.9',
    },
    delete: {
        d: 'M5.2,7 L18.8,7 M9.6,7 L9.6,6 A1.2,1.2 0 0 1 10.8,4.8 L13.2,4.8 A1.2,1.2 0 0 1 14.4,6 L14.4,7 M6.8,7 L7.6,18.7 A2,2 0 0 0 9.6,20.5 L14.4,20.5 A2,2 0 0 0 16.4,18.7 L17.2,7 M10.3,10.4 L10.3,16.8 M13.7,10.4 L13.7,16.8',
    },
    info: {
        d: 'M12,12 m-8.5,0 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0 M12,11 L12,16.2 M12,7.9 L12,7.95',
    },
    place: {
        d: 'M12,21.2 C12,21.2 5.2,15 5.2,10.2 a6.8,6.8 0 0 1 13.6,0 C18.8,15 12,21.2 12,21.2 Z M12,10.2 m-2.3,0 a2.3,2.3 0 1 0 4.6,0 a2.3,2.3 0 1 0 -4.6,0',
    },
    signOut: {
        d: 'M13.5,8 L13.5,5.5 L4.5,5.5 L4.5,18.5 L13.5,18.5 L13.5,16 M9,12 L19.5,12 M16.8,9.3 L19.5,12 L16.8,14.7',
    },

    // ——— Mobiliario del panel (mismo estilo de trazo) ———
    mail: {
        d: 'M5.5,6.5 L18.5,6.5 A2,2 0 0 1 20.5,8.5 L20.5,15.5 A2,2 0 0 1 18.5,17.5 L5.5,17.5 A2,2 0 0 1 3.5,15.5 L3.5,8.5 A2,2 0 0 1 5.5,6.5 Z M4.2,7.8 L12,13 L19.8,7.8',
    },
    lock: {
        d: 'M7.5,10.5 L16.5,10.5 A1.8,1.8 0 0 1 18.3,12.3 L18.3,18.2 A1.8,1.8 0 0 1 16.5,20 L7.5,20 A1.8,1.8 0 0 1 5.7,18.2 L5.7,12.3 A1.8,1.8 0 0 1 7.5,10.5 Z M8.4,10.5 L8.4,8.1 A3.6,3.6 0 0 1 15.6,8.1 L15.6,10.5 M12,14.4 L12,16.2',
    },
    edit: {
        d: 'M14.8,5.6 L18.4,9.2 L9.2,18.4 L4.9,19.1 L5.6,14.8 Z M13.3,7.1 L16.9,10.7',
    },
    plus: { d: 'M12,5.5 L12,18.5 M5.5,12 L18.5,12' },
}

/**
 * Icono de contorno del sistema: hereda el color del texto (`currentColor`),
 * así el par tinte/trazo lo decide la clase de acento del contenedor y ningún
 * componente hardcodea colores (misma regla que en Compose).
 */
export function V2Icon({ name, size = 21 }: { readonly name: IconName; readonly size?: number }) {
    const spec = ICONS[name]
    return (
        <svg
            viewBox="0 0 24 24"
            width={size}
            height={size}
            aria-hidden="true"
            focusable="false"
            className="icon"
        >
            <path
                d={spec.d}
                transform={spec.transform}
                fill="none"
                stroke="currentColor"
                strokeWidth={spec.strokeWidth ?? 1.9}
                strokeLinecap="round"
                strokeLinejoin="round"
            />
        </svg>
    )
}

/** Acentos por significado — espejo de los pares de V2Palette (anexo v2-29). */
export type AccentName = 'recognize' | 'protection' | 'care' | 'notice' | 'justice' | 'group'

/**
 * Círculo de acento con icono de contorno: espejo del AccentBadge de Compose
 * (icono 21 sobre círculo de 46; el panel usa 34 por densidad de escritorio).
 */
export function IconBadge({
    name,
    accent,
    size = 34,
}: {
    readonly name: IconName
    readonly accent: AccentName
    readonly size?: number
}) {
    return (
        <span
            className={`iconbadge accent--${accent}`}
            style={{ width: size, height: size }}
            aria-hidden="true"
        >
            <V2Icon name={name} size={Math.round(size * (21 / 46))} />
        </span>
    )
}

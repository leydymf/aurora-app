// Selector de icono y acento del contenido (P23·b, esquema v2 — opción a de
// D2): muestra ÚNICAMENTE la biblioteca cerrada con su significado. No hay
// color libre ni carga de imágenes: cada acento ya trae su par de contraste
// verificado, así la elección nunca rompe la accesibilidad ni el significado
// de los colores (regla 1 del anexo v2-29).
import {
    CONTENT_ACCENTS,
    CONTENT_ACCENT_IDS,
    CONTENT_ICONS,
    CONTENT_ICON_IDS,
    type ContentAccentId,
    type ContentIconId,
    type ContentVisualIds,
} from './contentVisuals'
import { IconBadge } from './icons'

export function VisualPicker({
    effective,
    onSelectIcon,
    onSelectAccent,
}: {
    /** Selección vigente ya resuelta (explícita o degradada al automático). */
    readonly effective: ContentVisualIds
    readonly onSelectIcon: (id: ContentIconId) => void
    readonly onSelectAccent: (id: ContentAccentId) => void
}) {
    return (
        <>
            <div className="field">
                <span>Icono (biblioteca cerrada)</span>
                <div className="visualpicker" role="group" aria-label="Icono">
                    {CONTENT_ICON_IDS.map((id) => {
                        const option = CONTENT_ICONS[id]
                        const active = id === effective.icon
                        return (
                            <button
                                key={id}
                                type="button"
                                title={option.label}
                                aria-label={option.label}
                                aria-pressed={active}
                                className={
                                    'visualpicker__option' +
                                    (active ? ' visualpicker__option--active' : '')
                                }
                                onClick={() => {
                                    onSelectIcon(id)
                                }}
                            >
                                <IconBadge
                                    name={option.icon}
                                    accent={CONTENT_ACCENTS[effective.accent].accent}
                                />
                            </button>
                        )
                    })}
                </div>
                <p className="muted">{CONTENT_ICONS[effective.icon].label}</p>
            </div>
            <div className="field">
                <span>Acento (biblioteca cerrada)</span>
                <div className="visualpicker" role="group" aria-label="Acento">
                    {CONTENT_ACCENT_IDS.map((id) => {
                        const option = CONTENT_ACCENTS[id]
                        const active = id === effective.accent
                        return (
                            <button
                                key={id}
                                type="button"
                                title={option.label}
                                aria-label={option.label}
                                aria-pressed={active}
                                className={
                                    'visualpicker__option' +
                                    (active ? ' visualpicker__option--active' : '')
                                }
                                onClick={() => {
                                    onSelectAccent(id)
                                }}
                            >
                                <span
                                    className={`accentdot accent--${option.accent}`}
                                    aria-hidden="true"
                                />
                            </button>
                        )
                    })}
                </div>
                <p className="muted">{CONTENT_ACCENTS[effective.accent].label}</p>
            </div>
        </>
    )
}

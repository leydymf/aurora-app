// Recurso «Rutas, entidades y líneas» (RF-42): tres pestañas porque
// comparten integridad referencial (las rutas apuntan a entidades por id) y
// alimentan la misma sección de la app. Estilo V2 (v2-26): pestañas como
// píldoras, sectores con su icono/matiz y la nota de que un cambio publicado
// llega sin nueva versión de la app.
import { useState } from 'react'
import { V2Icon } from '../../components/icons'
import { AgenciesTab } from './AgenciesTab'
import { LinesTab } from './LinesTab'
import { RoutesTab } from './RoutesTab'

const TABS = [
    { key: 'routes', label: 'Rutas de atención' },
    { key: 'agencies', label: 'Entidades' },
    { key: 'lines', label: 'Líneas de atención' },
] as const

type TabKey = (typeof TABS)[number]['key']

export function RoutesPage() {
    const [tab, setTab] = useState<TabKey>('routes')

    return (
        <section className="resource">
            <header className="resource__header">
                <h1>Rutas, entidades y líneas</h1>
                <span className="resource__rf">RF-42</span>
            </header>
            <p className="resource__desc">
                La ruta de atención que muestra la app (Salud–Protección–Justicia), las entidades a
                las que apunta y las líneas de contacto. Los cambios se guardan como borrador y solo
                llegan a la app al publicar (RF-44). Para publicar una ruta, sus entidades deben
                estar publicadas primero.
            </p>
            <div className="tabs">
                {TABS.map((entry) => (
                    <button
                        key={entry.key}
                        type="button"
                        className={entry.key === tab ? 'tab tab--active' : 'tab'}
                        onClick={() => {
                            setTab(entry.key)
                        }}
                    >
                        {entry.label}
                    </button>
                ))}
            </div>
            {tab === 'routes' && <RoutesTab />}
            {tab === 'agencies' && <AgenciesTab />}
            {tab === 'lines' && <LinesTab />}

            <p className="note">
                <V2Icon name="info" size={18} />
                <span>
                    <strong>Un teléfono corregido aquí</strong> llega a la app en su siguiente
                    actualización de contenido, sin necesidad de una nueva versión de la aplicación.
                </span>
            </p>
        </section>
    )
}

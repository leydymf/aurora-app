// Marcador de un recurso aún sin CRUD (Fase B1). La gestión real llega en B2.
interface ResourcePlaceholderProps {
    readonly title: string
    readonly rf: string
    readonly description: string
}

export function ResourcePlaceholder({ title, rf, description }: ResourcePlaceholderProps) {
    return (
        <section className="resource">
            <header className="resource__header">
                <h1>{title}</h1>
                <span className="resource__rf">{rf}</span>
            </header>
            <p className="resource__desc">{description}</p>
            <p className="resource__todo">Gestión pendiente (Fase B2).</p>
        </section>
    )
}

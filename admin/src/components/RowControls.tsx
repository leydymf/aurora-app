// Controles de fila de una lista editable: subir, bajar y quitar. El orden
// del arreglo es el orden canónico (normalizeForm re-numera `order` según la
// posición), así que mover filas es la única forma de reordenar.
interface RowControlsProps {
    /** Nombre del elemento para los aria-label (accesibilidad). */
    readonly label: string
    readonly index: number
    readonly count: number
    readonly onMove: (delta: number) => void
    readonly onRemove: () => void
}

export function RowControls({ label, index, count, onMove, onRemove }: RowControlsProps) {
    return (
        <>
            <button
                type="button"
                className="btn--ghost"
                aria-label={`Subir ${label}`}
                disabled={index === 0}
                onClick={() => {
                    onMove(-1)
                }}
            >
                ↑
            </button>
            <button
                type="button"
                className="btn--ghost"
                aria-label={`Bajar ${label}`}
                disabled={index === count - 1}
                onClick={() => {
                    onMove(1)
                }}
            >
                ↓
            </button>
            <button
                type="button"
                className="btn--ghost"
                aria-label={`Quitar ${label}`}
                onClick={() => {
                    onRemove()
                }}
            >
                ✕
            </button>
        </>
    )
}

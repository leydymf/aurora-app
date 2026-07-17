// Lista editable de textos (señales, ejemplos, teléfonos, consejos…):
// agregar, editar y quitar ítems. El orden es el de la lista.
interface StringListFieldProps {
    readonly label: string
    readonly values: readonly string[]
    readonly onChange: (values: string[]) => void
    readonly addLabel: string
    readonly multiline?: boolean
    readonly placeholder?: string
}

export function StringListField({
    label,
    values,
    onChange,
    addLabel,
    multiline = false,
    placeholder,
}: StringListFieldProps) {
    function updateItem(index: number, value: string) {
        onChange(values.map((item, i) => (i === index ? value : item)))
    }

    function removeItem(index: number) {
        onChange(values.filter((_, i) => i !== index))
    }

    return (
        <div className="field">
            <span>{label}</span>
            {values.map((item, index) => (
                <div className="listfield__item" key={index}>
                    {multiline ? (
                        <textarea
                            rows={2}
                            value={item}
                            placeholder={placeholder}
                            onChange={(e) => {
                                updateItem(index, e.target.value)
                            }}
                        />
                    ) : (
                        <input
                            value={item}
                            placeholder={placeholder}
                            onChange={(e) => {
                                updateItem(index, e.target.value)
                            }}
                        />
                    )}
                    <button
                        type="button"
                        className="btn--ghost listfield__remove"
                        aria-label={`Quitar ítem ${String(index + 1)} de ${label}`}
                        onClick={() => {
                            removeItem(index)
                        }}
                    >
                        ✕
                    </button>
                </div>
            ))}
            <button
                type="button"
                className="btn--ghost listfield__add"
                onClick={() => {
                    onChange([...values, ''])
                }}
            >
                + {addLabel}
            </button>
        </div>
    )
}

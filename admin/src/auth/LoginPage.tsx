// Pantalla de inicio de sesión de la administradora (RF-40).
//
// Sin registro público: la cuenta admin se crea con tools/admin/create-admin.mjs.
// Aquí solo se autentica; un fallo (credenciales malas o cuenta sin claim admin)
// muestra el mismo mensaje genérico.
import { useState, type FormEvent } from 'react'
import { useAuth } from './useAuth'

export function LoginPage() {
    const { signIn } = useAuth()
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState<string | null>(null)
    const [submitting, setSubmitting] = useState(false)

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        setError(null)
        setSubmitting(true)
        try {
            await signIn(email, password)
        } catch (err) {
            setError(err instanceof Error ? err.message : 'No se pudo iniciar sesión.')
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <main className="login">
            <form
                className="login__card"
                onSubmit={(e) => {
                    void handleSubmit(e)
                }}
            >
                <h1 className="login__title">Aurora · Administración</h1>
                <p className="login__hint">Acceso restringido al equipo de la operación.</p>

                <label className="field">
                    <span>Correo</span>
                    <input
                        type="email"
                        autoComplete="username"
                        required
                        value={email}
                        onChange={(e) => {
                            setEmail(e.target.value)
                        }}
                    />
                </label>

                <label className="field">
                    <span>Contraseña</span>
                    <input
                        type="password"
                        autoComplete="current-password"
                        required
                        value={password}
                        onChange={(e) => {
                            setPassword(e.target.value)
                        }}
                    />
                </label>

                {error && (
                    <p className="login__error" role="alert">
                        {error}
                    </p>
                )}

                <button type="submit" disabled={submitting}>
                    {submitting ? 'Entrando…' : 'Iniciar sesión'}
                </button>
            </form>
        </main>
    )
}

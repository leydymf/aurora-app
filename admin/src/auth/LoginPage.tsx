// Pantalla de inicio de sesión de la administradora (RF-40), estilo V2 (v2-24).
//
// Sin registro público: la cuenta admin se crea con tools/admin/create-admin.mjs.
// Aquí solo se autentica; un fallo (credenciales malas o cuenta sin claim admin)
// muestra el MISMO mensaje genérico — el mockup enseña un rechazo por rol
// explícito, pero revelaría que la cuenta existe; por decisión de la sesión
// 2026-08-12 se conserva el texto genérico con el estilo de aviso del mockup.
import { useState, type FormEvent } from 'react'
import { useAuth } from './useAuth'
import auroraMark from '../assets/aurora-mark.svg'
import { V2Icon } from '../components/icons'

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
                <div className="login__brand">
                    <img className="login__mark" src={auroraMark} alt="" />
                    <h1 className="login__title">Aurora</h1>
                    <p className="login__hint">Panel de administración</p>
                </div>

                <label className="field">
                    <span>Correo institucional</span>
                    <div className="inputwrap">
                        <V2Icon name="mail" size={18} />
                        <input
                            type="email"
                            autoComplete="username"
                            required
                            value={email}
                            onChange={(e) => {
                                setEmail(e.target.value)
                            }}
                        />
                    </div>
                </label>

                <label className="field">
                    <span>Contraseña</span>
                    <div className="inputwrap">
                        <V2Icon name="lock" size={18} />
                        <input
                            type="password"
                            autoComplete="current-password"
                            required
                            value={password}
                            onChange={(e) => {
                                setPassword(e.target.value)
                            }}
                        />
                    </div>
                </label>

                {error && (
                    <p className="alert" role="alert">
                        <V2Icon name="info" size={18} />
                        <span>{error}</span>
                    </p>
                )}

                <button type="submit" disabled={submitting}>
                    {submitting ? 'Entrando…' : 'Entrar'}
                </button>

                <p className="login__footer">
                    El panel gestiona solo contenido. Ningún rol accede a datos de usuarias.
                </p>
            </form>
        </main>
    )
}

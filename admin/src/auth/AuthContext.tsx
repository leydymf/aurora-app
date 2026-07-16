// Estado de autenticación de la administradora (RF-40).
//
// Es el ÚNICO estado global del panel (regla de admin/CLAUDE.md). Expone la
// sesión admin verificada y las acciones de entrar/salir.
//
// RF-40 (denegar cuentas sin rol): tras autenticarse se refresca el token y se
// lee el custom claim `admin`. Si NO está presente, se cierra la sesión de
// inmediato y se devuelve un mensaje genérico: el panel no distingue "no eres
// admin" de "credenciales inválidas", para no filtrar qué cuentas existen.
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { onAuthStateChanged, signInWithEmailAndPassword, signOut, type User } from 'firebase/auth'
import { auth } from '../firebase/config'
import { AuthContext, type AuthState } from './auth-context'

const GENERIC_ERROR = 'No se pudo iniciar sesión. Verifica tus credenciales.'

/** Devuelve true solo si el token del usuario porta el claim admin === true. */
async function hasAdminClaim(user: User): Promise<boolean> {
    // forceRefresh: los custom claims recién asignados por el script no llegan
    // hasta refrescar el token.
    const token = await user.getIdTokenResult(true)
    return token.claims.admin === true
}

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<User | null>(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        // Reacciona a sesiones persistidas (recarga de página) y a cierres. Cada
        // vez revalida el claim: una cuenta a la que se le revocó el rol no queda
        // dentro por tener una sesión vieja.
        const unsubscribe = onAuthStateChanged(auth, (current) => {
            if (!current) {
                setUser(null)
                setLoading(false)
                return
            }
            void hasAdminClaim(current)
                .then(async (isAdmin) => {
                    if (isAdmin) {
                        setUser(current)
                    } else {
                        await signOut(auth)
                        setUser(null)
                    }
                })
                .catch(async () => {
                    await signOut(auth)
                    setUser(null)
                })
                .finally(() => {
                    setLoading(false)
                })
        })
        return unsubscribe
    }, [])

    const value = useMemo<AuthState>(
        () => ({
            user,
            loading,
            signIn: async (email, password) => {
                let credential
                try {
                    credential = await signInWithEmailAndPassword(auth, email, password)
                } catch {
                    throw new Error(GENERIC_ERROR)
                }
                const isAdmin = await hasAdminClaim(credential.user)
                if (!isAdmin) {
                    // Cuenta válida pero sin rol admin: fuera de inmediato (RF-40).
                    await signOut(auth)
                    throw new Error(GENERIC_ERROR)
                }
                // El listener de onAuthStateChanged fija el estado; no lo duplicamos.
            },
            signOutAdmin: () => signOut(auth),
        }),
        [user, loading],
    )

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

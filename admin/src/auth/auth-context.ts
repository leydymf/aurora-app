// Definición del contexto de autenticación (separada del componente proveedor
// para no romper el fast-refresh de Vite: un módulo mezcla componentes con
// hooks/constantes solo si se separan).
import { createContext } from 'react'
import type { User } from 'firebase/auth'

export interface AuthState {
    /** Usuaria admin verificada, o null si no hay sesión válida. */
    readonly user: User | null
    /** true mientras se resuelve el estado inicial de sesión. */
    readonly loading: boolean
    /** Inicia sesión; lanza Error con mensaje genérico si falla o no es admin. */
    readonly signIn: (email: string, password: string) => Promise<void>
    /** Cierra la sesión actual. */
    readonly signOutAdmin: () => Promise<void>
}

export const AuthContext = createContext<AuthState | null>(null)

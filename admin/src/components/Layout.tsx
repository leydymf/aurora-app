// Estructura base del panel: navegación entre los 4 recursos + salida.
//
// Solo se monta cuando hay una sesión admin verificada (ver App.tsx). Las
// páginas de recurso están vacías por ahora (Fase B2 traerá el CRUD).
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

const RESOURCES = [
    { to: '/contenido', label: 'Contenido', rf: 'RF-41' },
    { to: '/rutas', label: 'Rutas y líneas', rf: 'RF-42' },
    { to: '/cuestionario', label: 'Cuestionario', rf: 'RF-43' },
    { to: '/publicacion', label: 'Publicación', rf: 'RF-44' },
] as const

export function Layout() {
    const { user, signOutAdmin } = useAuth()

    return (
        <div className="layout">
            <aside className="sidebar">
                <div className="sidebar__brand">Aurora · Admin</div>
                <nav className="sidebar__nav">
                    {RESOURCES.map((resource) => (
                        <NavLink
                            key={resource.to}
                            to={resource.to}
                            className={({ isActive }) =>
                                isActive ? 'navlink navlink--active' : 'navlink'
                            }
                        >
                            <span>{resource.label}</span>
                            <span className="navlink__rf">{resource.rf}</span>
                        </NavLink>
                    ))}
                </nav>
                <div className="sidebar__footer">
                    <span className="sidebar__user" title={user?.email ?? undefined}>
                        {user?.email}
                    </span>
                    <button
                        type="button"
                        className="sidebar__logout"
                        onClick={() => {
                            void signOutAdmin()
                        }}
                    >
                        Cerrar sesión
                    </button>
                </div>
            </aside>
            <main className="content">
                <Outlet />
            </main>
        </div>
    )
}

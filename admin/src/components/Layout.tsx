// Estructura base del panel: navegación entre los 4 recursos + salida.
//
// Solo se monta cuando hay una sesión admin verificada (ver App.tsx). Estilo
// V2 (v2-25/26/27): marca Aurora arriba, navegación en píldoras con icono de
// contorno y ficha de la administradora al pie. Los RF de cada recurso siguen
// visibles en el encabezado de su página.
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import auroraMark from '../assets/aurora-mark.svg'
import { V2Icon, type IconName } from './icons'

const RESOURCES: readonly { to: string; label: string; icon: IconName }[] = [
    { to: '/contenido', label: 'Contenido', icon: 'guide' },
    { to: '/rutas', label: 'Rutas y líneas', icon: 'place' },
    { to: '/cuestionario', label: 'Cuestionario', icon: 'recognize' },
    { to: '/publicacion', label: 'Publicación', icon: 'synced' },
]

export function Layout() {
    const { user, signOutAdmin } = useAuth()
    const initial = user?.email?.charAt(0) ?? 'a'

    return (
        <div className="layout">
            <aside className="sidebar">
                <div className="sidebar__brand">
                    <img className="sidebar__mark" src={auroraMark} alt="" />
                    <span>Aurora</span>
                </div>
                <nav className="sidebar__nav">
                    {RESOURCES.map((resource) => (
                        <NavLink
                            key={resource.to}
                            to={resource.to}
                            className={({ isActive }) =>
                                isActive ? 'navlink navlink--active' : 'navlink'
                            }
                        >
                            <V2Icon name={resource.icon} size={18} />
                            <span>{resource.label}</span>
                        </NavLink>
                    ))}
                </nav>
                <div className="sidebar__footer">
                    <div className="sidebar__who">
                        <span className="sidebar__avatar" aria-hidden="true">
                            {initial}
                        </span>
                        <span className="sidebar__user" title={user?.email ?? undefined}>
                            {user?.email}
                        </span>
                    </div>
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

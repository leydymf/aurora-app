// Raíz del panel: decide entre login y panel según la sesión admin verificada.
//
// Mientras AuthProvider resuelve el estado inicial se muestra un cargando; sin
// sesión válida, el login; con sesión admin, el layout con sus rutas.
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/useAuth'
import { LoginPage } from './auth/LoginPage'
import { Layout } from './components/Layout'
import { ContentPage } from './resources/content/ContentPage'
import { RoutesPage } from './resources/routes/RoutesPage'
import { QuestionnairePage } from './resources/questionnaire/QuestionnairePage'
import { PublishPage } from './resources/publish/PublishPage'

function AdminRoutes() {
    const { user, loading } = useAuth()

    if (loading) {
        return <div className="loading">Cargando…</div>
    }

    if (!user) {
        return <LoginPage />
    }

    return (
        <Routes>
            <Route element={<Layout />}>
                <Route path="/contenido" element={<ContentPage />} />
                <Route path="/rutas" element={<RoutesPage />} />
                <Route path="/cuestionario" element={<QuestionnairePage />} />
                <Route path="/publicacion" element={<PublishPage />} />
                <Route path="*" element={<Navigate to="/contenido" replace />} />
            </Route>
        </Routes>
    )
}

export function App() {
    return (
        <AuthProvider>
            <BrowserRouter>
                <AdminRoutes />
            </BrowserRouter>
        </AuthProvider>
    )
}

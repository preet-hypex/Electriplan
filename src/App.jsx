import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { configError } from './lib/supabase'
import ConfigError from './components/ConfigError'
import ProtectedRoute from './components/ProtectedRoute'
import Login from './pages/Login'
import Invite from './pages/Invite'
import ResetPassword from './pages/ResetPassword'
import Home from './pages/Home'

export default function App() {
  if (configError) return <ConfigError reason={configError} />

  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/invite" element={<Invite />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/" element={<ProtectedRoute><Home /></ProtectedRoute>} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}

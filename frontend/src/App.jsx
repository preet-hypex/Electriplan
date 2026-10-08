import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { CompanyProvider } from './context/CompanyContext'
import { configError } from './lib/supabase'
import ConfigError from './components/ConfigError'
import ProtectedRoute from './components/ProtectedRoute'
import Login from './pages/Login'
import Invite from './pages/Invite'
import ResetPassword from './pages/ResetPassword'
import Home from './pages/Home'
import NewProject from './pages/NewProject'
import Project from './pages/Project'
import HouseFloorPlan from './pages/HouseFloorPlan'
import FloorPlan from './pages/FloorPlan'
import Account from './pages/Account'

export default function App() {
  if (configError) return <ConfigError reason={configError} />

  return (
    <AuthProvider>
      <CompanyProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/invite" element={<Invite />} />
            <Route path="/reset-password" element={<ResetPassword />} />
            <Route path="/" element={<ProtectedRoute><Home /></ProtectedRoute>} />
            <Route path="/projects/new" element={<ProtectedRoute><NewProject /></ProtectedRoute>} />
            <Route path="/projects/:id" element={<ProtectedRoute><Project /></ProtectedRoute>} />
            <Route path="/projects/:projectId/houses/:houseId/floor-plan" element={<ProtectedRoute><HouseFloorPlan /></ProtectedRoute>} />
            <Route path="/floor-plan" element={<ProtectedRoute><FloorPlan /></ProtectedRoute>} />
            <Route path="/account" element={<ProtectedRoute><Account /></ProtectedRoute>} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
      </CompanyProvider>
    </AuthProvider>
  )
}

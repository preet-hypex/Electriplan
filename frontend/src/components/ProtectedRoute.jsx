import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { invitationState } from '../lib/invitation'
import { isRecovering, sessionStore } from '../lib/recovery'

export default function ProtectedRoute({ children }) {
  const { loading, user } = useAuth()
  const location = useLocation()

  if (loading) return <div className="center">Checking your session…</div>
  if (!user) return <Navigate to="/login" state={{ from: location }} replace />
  if (invitationState(user) === 'pending') return <Navigate to="/invite" replace />
  if (isRecovering(sessionStore(), user.id)) return <Navigate to="/reset-password" replace />
  return children
}

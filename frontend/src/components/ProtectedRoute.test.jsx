import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { rememberRecovery } from '../lib/recovery'

const mocks = vi.hoisted(() => ({ auth: null }))
vi.mock('../context/AuthContext', () => ({ useAuth: () => mocks.auth }))

import ProtectedRoute from './ProtectedRoute'

function at(auth) {
  mocks.auth = auth
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<ProtectedRoute><div>secret stuff</div></ProtectedRoute>} />
        <Route path="/login" element={<div>login page</div>} />
        <Route path="/invite" element={<div>invite page</div>} />
        <Route path="/reset-password" element={<div>reset page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute', () => {
  it('waits while the session loads', () => {
    at({ loading: true, user: null })
    expect(screen.getByText(/checking your session/i)).toBeInTheDocument()
  })

  it('sends a signed-out visitor to sign in', () => {
    at({ loading: false, user: null })
    expect(screen.getByText('login page')).toBeInTheDocument()
  })

  it('sends someone who has not accepted their invitation to finish it', () => {
    at({ loading: false, user: { id: 'u1', invited_at: '2026-01-01', user_metadata: {} } })
    expect(screen.getByText('invite page')).toBeInTheDocument()
  })

  it('holds someone who arrived by a reset link on the reset page', () => {
    rememberRecovery(sessionStorage, 'u1')
    at({ loading: false, user: { id: 'u1', user_metadata: {} } })
    expect(screen.getByText('reset page')).toBeInTheDocument()
  })

  it('lets a signed-in user through', () => {
    at({ loading: false, user: { id: 'u1', user_metadata: {} } })
    expect(screen.getByText('secret stuff')).toBeInTheDocument()
  })
})

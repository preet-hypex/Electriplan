import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ auth: null }))

vi.mock('../context/AuthContext', () => ({ useAuth: () => mocks.auth }))
vi.mock('../lib/supabase', () => ({ supabase: {}, authRedirectError: null }))

import brand from '../lib/brand'
import Login from './Login'

function renderLogin(extra = {}) {
  mocks.auth = { user: null, loading: false, signIn: vi.fn(), resetPassword: vi.fn(), ...extra }
  render(<MemoryRouter initialEntries={['/login']}><Login /></MemoryRouter>)
}

describe('Login', () => {
  it('shows the brand from src/brand.json', () => {
    renderLogin()
    expect(screen.getAllByLabelText(brand.name).length).toBeGreaterThan(0)
  })

  it('offers no way to create an account', () => {
    renderLogin()
    for (const link of screen.queryAllByRole('link')) expect(link.getAttribute('href')).not.toMatch(/signup|register/)
    expect(screen.getByText(/accounts are by invitation/i)).toBeInTheDocument()
  })

  it('signs in with the trimmed email and goes home', async () => {
    renderLogin({ signIn: vi.fn().mockResolvedValue({ error: null }) })

    await userEvent.type(screen.getByLabelText('Email'), ' sam@example.com ')
    await userEvent.type(screen.getByLabelText('Password'), 'hunter22')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    expect(mocks.auth.signIn).toHaveBeenCalledWith('sam@example.com', 'hunter22')
  })

  it('shows why sign-in failed', async () => {
    renderLogin({ signIn: vi.fn().mockResolvedValue({ error: new Error('Invalid login credentials') }) })

    await userEvent.type(screen.getByLabelText('Email'), 'sam@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByText('Invalid login credentials')).toBeInTheDocument()
  })

  it('sends a reset link without saying whether the address has an account', async () => {
    renderLogin({ resetPassword: vi.fn().mockResolvedValue({ error: null }) })

    await userEvent.click(screen.getByRole('link', { name: 'Forgot?' }))
    await userEvent.type(screen.getByLabelText('Email'), ' sam@example.com ')
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }))

    expect(mocks.auth.resetPassword).toHaveBeenCalledWith('sam@example.com')
    expect(await screen.findByText(/if that address has an account, a reset link is on its way/i)).toBeInTheDocument()
  })

  it('shows why Supabase refused a reset request', async () => {
    renderLogin({ resetPassword: vi.fn().mockResolvedValue({ error: new Error('For security purposes, you can only request this after 60 seconds.') }) })

    await userEvent.click(screen.getByRole('link', { name: 'Forgot?' }))
    await userEvent.type(screen.getByLabelText('Email'), 'sam@example.com')
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }))

    expect(await screen.findByText(/only request this after 60 seconds/)).toBeInTheDocument()
  })

  it('asks for an email before requesting a reset link', async () => {
    renderLogin()

    await userEvent.click(screen.getByRole('link', { name: 'Forgot?' }))
    await userEvent.click(screen.getByRole('button', { name: /send reset link/i }))

    expect(mocks.auth.resetPassword).not.toHaveBeenCalled()
    expect(await screen.findByText('An email address is required')).toBeInTheDocument()
  })

  it('returns to the page it was sent from, query string included', () => {
    function Where() {
      const location = useLocation()
      return <div>at {location.pathname}{location.search}</div>
    }
    mocks.auth = { user: { id: 'u1' }, loading: false, signIn: vi.fn(), resetPassword: vi.fn() }
    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: { pathname: '/reports', search: '?q=1' } } }]}>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/reports" element={<Where />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('at /reports?q=1')).toBeInTheDocument()
  })
})

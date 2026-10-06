import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ api: vi.fn() }))

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'u1', email: 'sam@example.com', user_metadata: { full_name: 'Sam Lee' } },
    signOut: vi.fn(),
  }),
}))
vi.mock('../lib/api', () => ({ api: mocks.api }))

import Home from './Home'

describe('Home', () => {
  it('shows the copy of this user that the API keeps in Postgres', async () => {
    mocks.api.mockResolvedValue({
      id: 'u1',
      copy: { email: 'sam@example.com', userMetadata: { full_name: 'Sam Lee' }, copiedAt: '2026-10-06T00:00:00Z' },
    })
    render(<Home />)

    expect(mocks.api).toHaveBeenCalledWith('/api/me')
    expect(await screen.findByText('Copied')).toBeInTheDocument()
    expect(screen.getAllByText('Sam Lee').length).toBeGreaterThan(0)
  })

  it('says so when the API has not copied the user yet', async () => {
    mocks.api.mockResolvedValue({ id: 'u1', copy: null })
    render(<Home />)
    expect(await screen.findByText(/not copied yet/i)).toBeInTheDocument()
  })

  it('still shows the Supabase session when the API is down', async () => {
    mocks.api.mockRejectedValue(new Error('A service the app depends on did not answer.'))
    render(<Home />)
    expect(await screen.findByText(/the api did not answer/i)).toBeInTheDocument()
    expect(screen.getByText('sam@example.com')).toBeInTheDocument()
  })
})

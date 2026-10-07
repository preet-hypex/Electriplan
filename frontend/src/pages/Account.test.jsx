import { render as renderInDom, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ api: vi.fn() }))

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'u1', email: 'sam@example.com', user_metadata: { full_name: 'Sam Lee' } },
    signOut: vi.fn(),
  }),
}))
vi.mock('../lib/api', () => ({ api: mocks.api }))

import Account from './Account'

const render = ui => renderInDom(<MemoryRouter>{ui}</MemoryRouter>)

describe('Account', () => {
  it('shows the copy of this user that the API keeps in Postgres', async () => {
    mocks.api.mockResolvedValue({
      id: 'u1',
      copy: { email: 'sam@example.com', userMetadata: { full_name: 'Sam Lee' }, copiedAt: '2026-10-06T00:00:00Z' },
    })
    render(<Account />)

    expect(mocks.api).toHaveBeenCalledWith('/api/me')
    expect(await screen.findByText('Copied')).toBeInTheDocument()
    expect(screen.getAllByText('Sam Lee').length).toBeGreaterThan(0)
  })

  it('says so when the API has not copied the user yet', async () => {
    mocks.api.mockResolvedValue({ id: 'u1', copy: null })
    render(<Account />)
    expect(await screen.findByText(/not copied yet/i)).toBeInTheDocument()
  })

  it('still shows the Supabase session when the API is down', async () => {
    mocks.api.mockRejectedValue(new Error('A service the app depends on did not answer.'))
    render(<Account />)
    expect(await screen.findByText(/the api did not answer/i)).toBeInTheDocument()
    expect(within(screen.getByRole('main')).getByText('sam@example.com')).toBeInTheDocument()
  })
})

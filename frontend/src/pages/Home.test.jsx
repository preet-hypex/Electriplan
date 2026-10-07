import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'u1', email: 'sam@example.com', user_metadata: { full_name: 'Sam Lee' } },
    signOut: vi.fn(),
  }),
}))

import Home from './Home'

const renderHome = () => render(<MemoryRouter><Home /></MemoryRouter>)

describe('Home', () => {
  it('greets the user by name', () => {
    renderHome()
    expect(screen.getByText(/, Sam Lee$/)).toBeInTheDocument()
  })

  it('starts a floor plan from the main call to action', () => {
    renderHome()
    expect(screen.getByRole('link', { name: /upload a floor plan/i })).toHaveAttribute('href', '/floor-plan')
  })

  it('links only the workflow steps that exist today', () => {
    renderHome()
    const steps = within(screen.getByRole('region', { name: 'Your workflow' }))
    expect(steps.getByRole('link', { name: /floor plan/i })).toHaveAttribute('href', '/floor-plan')
    expect(steps.getAllByRole('link')).toHaveLength(1)
    expect(screen.getByText('Electrical layout').closest('[aria-disabled="true"]')).not.toBeNull()
    expect(screen.getByText('Coming next')).toBeInTheDocument()
  })

  it('shows unbuilt sections in the sidebar as coming soon, not as links', () => {
    renderHome()
    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: /floor plans/i })).toHaveAttribute('href', '/floor-plan')
    expect(within(nav).queryByRole('link', { name: /quotes/i })).toBeNull()
    expect(within(nav).getAllByText('Soon')).toHaveLength(3)
  })
})

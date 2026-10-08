import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u1', email: 'sam@example.com', user_metadata: {} }, signOut: vi.fn() }),
}))

import AppLayout from './AppLayout'

const renderLayout = () =>
  render(<MemoryRouter><AppLayout title="Home"><p>page</p></AppLayout></MemoryRouter>)

describe('AppLayout menu button', () => {
  beforeEach(() => localStorage.clear())

  it('collapses the sidebar to icons and expands it again', async () => {
    const { container } = renderLayout()
    const shell = container.querySelector('.shell')
    expect(shell).not.toHaveClass('collapsed')

    await userEvent.click(screen.getByRole('button', { name: 'Collapse the menu' }))
    expect(shell).toHaveClass('collapsed')
    expect(screen.getByRole('button', { name: 'Expand the menu' })).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(screen.getByRole('button', { name: 'Expand the menu' }))
    expect(shell).not.toHaveClass('collapsed')
  })

  it('remembers the choice for the next page', async () => {
    const first = renderLayout()
    await userEvent.click(screen.getByRole('button', { name: 'Collapse the menu' }))
    first.unmount()

    const { container } = renderLayout()
    expect(container.querySelector('.shell')).toHaveClass('collapsed')
  })

  it('keeps every link named when only icons show', async () => {
    renderLayout()
    await userEvent.click(screen.getByRole('button', { name: 'Collapse the menu' }))
    expect(screen.getByRole('link', { name: /floor-plan editor/i })).toHaveAttribute('title', 'Floor-plan editor')
  })
})

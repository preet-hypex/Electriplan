import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { VIEWER_COMPANY, SUMMARY, company, page } from '../test/projects'

const mocks = vi.hoisted(() => ({ company: null, listProjects: vi.fn(), recentHouses: vi.fn() }))

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u1', email: 'sam@example.com', user_metadata: { full_name: 'Sam Lee' } }, signOut: vi.fn() }),
}))
vi.mock('../context/CompanyContext', () => ({ useCompany: () => mocks.company }))
vi.mock('../api/projects', async original => ({
  ...(await original()),
  listProjects: mocks.listProjects,
  recentHouses: mocks.recentHouses,
}))

import Home from './Home'

const renderHome = () => render(<MemoryRouter><Home /></MemoryRouter>)

describe('Home: the project list', () => {
  beforeEach(() => {
    mocks.company = company()
    mocks.listProjects.mockReset().mockResolvedValue(page([SUMMARY]))
    mocks.recentHouses.mockReset().mockResolvedValue([])
  })

  it('greets the person and names their company', async () => {
    renderHome()
    expect(screen.getByText(/, Sam Lee$/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Hypex' })).toBeInTheDocument()
  })

  it('lists projects with their reference, site and houses by stage', async () => {
    renderHome()
    const link = await screen.findByRole('link', { name: '12 Example St' })
    expect(link).toHaveAttribute('href', `/projects/${SUMMARY.id}`)
    const row = link.closest('tr')
    expect(within(row).getByText('PRJ-000042')).toBeInTheDocument()
    expect(within(row).getByText('Brunswick, VIC')).toBeInTheDocument()
    expect(within(row).getByText('Awaiting floor plan').closest('.stage')).toHaveTextContent('2Awaiting floor plan')
    expect(within(row).getByText('Analysing floor plan')).toBeInTheDocument()
    expect(screen.getByText('1 project')).toBeInTheDocument()
  })

  it('offers New project only to roles that may start one', async () => {
    renderHome()
    expect(screen.getByRole('link', { name: /new project/i })).toHaveAttribute('href', '/projects/new')

    mocks.company = company({ company: VIEWER_COMPANY })
    renderHome()
    await screen.findAllByRole('link', { name: '12 Example St' })
    expect(screen.getAllByRole('link', { name: /new project/i })).toHaveLength(1)
  })

  it('says what to do when the company has no projects yet', async () => {
    mocks.listProjects.mockResolvedValue(page([]))
    renderHome()
    expect(await screen.findByText('No projects yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /start your first project/i })).toHaveAttribute('href', '/projects/new')
  })

  it('tells a viewer who starts projects', async () => {
    mocks.company = company({ company: VIEWER_COMPANY })
    mocks.listProjects.mockResolvedValue(page([]))
    renderHome()
    expect(await screen.findByText(/an owner, admin or builder in your company starts projects/i)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /start your first project/i })).toBeNull()
  })

  it('searches, filters by status and shows archived projects', async () => {
    renderHome()
    await screen.findByRole('link', { name: '12 Example St' })

    await userEvent.type(screen.getByRole('searchbox', { name: 'Search projects' }), 'brunswick')
    await waitFor(() => expect(mocks.listProjects).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'brunswick', page: 0 })))

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Status' }), 'on_hold')
    await waitFor(() => expect(mocks.listProjects).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'on_hold' })))

    mocks.listProjects.mockResolvedValue(page([]))
    await userEvent.click(screen.getByRole('checkbox', { name: 'Archived' }))
    await waitFor(() => expect(mocks.listProjects).toHaveBeenLastCalledWith(expect.objectContaining({ archived: true })))
    expect(await screen.findByText('No projects match')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Archived projects' })).toBeInTheDocument()
  })

  it('pages through long lists', async () => {
    mocks.listProjects.mockResolvedValue(page([SUMMARY], 30))
    renderHome()
    expect(await screen.findByText('1–25 of 30')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(mocks.listProjects).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))
  })

  it('offers the houses changed most recently, linked to their project', async () => {
    mocks.recentHouses.mockResolvedValue([{
      id: 'h1', name: 'Type A', stage: 'floor_plan_review', updatedAt: new Date().toISOString(),
      project: { id: SUMMARY.id, reference: 'PRJ-000042', name: '12 Example St' },
    }])
    renderHome()
    const recent = within(await screen.findByRole('region', { name: 'Continue where you left off' }))
    expect(recent.getByRole('link', { name: /type a/i })).toHaveAttribute('href', `/projects/${SUMMARY.id}`)
    expect(recent.getByText('Checking floor plan')).toBeInTheDocument()
    expect(recent.getByText('PRJ-000042 · 12 Example St')).toBeInTheDocument()
  })

  it('explains when the person is not in any company', () => {
    mocks.company = company({ status: 'none', company: null, companies: [] })
    renderHome()
    expect(screen.getByText('You are not in a company yet')).toBeInTheDocument()
    expect(mocks.listProjects).not.toHaveBeenCalled()
  })

  it('shows the API’s message when the list cannot be loaded', async () => {
    mocks.listProjects.mockRejectedValue(new Error('The API did not answer'))
    renderHome()
    expect(await screen.findByRole('alert')).toHaveTextContent('The projects could not be loaded: The API did not answer')
  })
})

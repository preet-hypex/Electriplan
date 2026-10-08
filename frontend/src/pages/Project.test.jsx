import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PROJECT, VIEWER_COMPANY, company } from '../test/projects'

const mocks = vi.hoisted(() => ({
  company: null, getProject: vi.fn(), updateProject: vi.fn(), addHouse: vi.fn(),
  archiveProject: vi.fn(), restoreProject: vi.fn(), distributorsIn: vi.fn(),
}))

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u1', email: 'sam@example.com', user_metadata: {} }, signOut: vi.fn() }),
}))
vi.mock('../context/CompanyContext', () => ({ useCompany: () => mocks.company }))
vi.mock('../api/projects', async original => ({
  ...(await original()),
  getProject: mocks.getProject,
  updateProject: mocks.updateProject,
  addHouse: mocks.addHouse,
  archiveProject: mocks.archiveProject,
  restoreProject: mocks.restoreProject,
  distributorsIn: mocks.distributorsIn,
}))

import Project from './Project'

const renderPage = () => render(
  <MemoryRouter initialEntries={[`/projects/${PROJECT.id}`]}>
    <Routes><Route path="/projects/:id" element={<Project />} /></Routes>
  </MemoryRouter>,
)

describe('Project page', () => {
  beforeEach(() => {
    mocks.company = company()
    mocks.getProject.mockReset().mockResolvedValue(PROJECT)
    mocks.updateProject.mockReset()
    mocks.addHouse.mockReset().mockResolvedValue({})
    mocks.archiveProject.mockReset().mockResolvedValue({ ...PROJECT, archived: true })
    mocks.restoreProject.mockReset()
    mocks.distributorsIn.mockReset().mockResolvedValue([{ code: 'jemena', name: 'Jemena', state: 'VIC' }])
  })

  it('shows the project’s details and its current houses', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: '12 Example St', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toHaveTextContent('Projects/PRJ-000042')
    expect(screen.getByText('12 Example St, Brunswick, VIC, 3056')).toBeInTheDocument()
    expect(screen.getByText('jemena')).toBeInTheDocument()
    const houses = within(screen.getByRole('region', { name: 'Houses' }))
    expect(houses.getByText('Type A')).toBeInTheDocument()
    expect(houses.getByText('Awaiting floor plan')).toBeInTheDocument()
    expect(houses.queryByText('Old design')).toBeNull()
    expect(houses.getByText('1 house')).toBeInTheDocument()
  })

  it('adds a house, then shows the project again', async () => {
    renderPage()
    await screen.findByText('Type A')
    await userEvent.type(screen.getByRole('textbox', { name: 'House name' }), 'Type B')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Dwelling type' }), 'townhouse')
    await userEvent.click(screen.getByRole('button', { name: /add house/i }))

    await waitFor(() => expect(mocks.addHouse).toHaveBeenCalledWith(PROJECT.id, { name: 'Type B', dwellingType: 'townhouse' }))
    await waitFor(() => expect(mocks.getProject).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('textbox', { name: 'House name' })).toHaveValue('')
  })

  it('edits the details with the version it read', async () => {
    mocks.updateProject.mockResolvedValue({ ...PROJECT, name: '14 Example St', version: 4 })
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit details' }))
    const name = screen.getByLabelText('Name')
    await userEvent.clear(name)
    await userEvent.type(name, '14 Example St')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(mocks.updateProject).toHaveBeenCalledWith(PROJECT.id,
      expect.objectContaining({ name: '14 Example St', version: 3, status: 'active' })))
    expect(await screen.findByRole('heading', { name: '14 Example St', level: 1 })).toBeInTheDocument()
  })

  it('offers to reload when someone else changed the project meanwhile', async () => {
    mocks.updateProject.mockRejectedValue(Object.assign(new Error('Someone changed this since you opened it.'), { status: 409, body: {} }))
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Edit details' }))
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Someone changed this since you opened it.')
    await userEvent.click(screen.getByRole('button', { name: 'Reload' }))
    await waitFor(() => expect(mocks.getProject).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('button', { name: 'Save changes' })).toBeNull()
  })

  it('archives the project, which makes it read-only', async () => {
    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: 'Archive' }))
    expect(await screen.findByText(/this project is archived/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Restore' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit details' })).toBeNull()
    expect(screen.queryByRole('textbox', { name: 'House name' })).toBeNull()
  })

  it('lets a viewer look but not change anything', async () => {
    mocks.company = company({ company: VIEWER_COMPANY })
    renderPage()
    await screen.findByText('Type A')
    expect(screen.queryByRole('button', { name: 'Edit details' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Archive' })).toBeNull()
    expect(screen.queryByRole('textbox', { name: 'House name' })).toBeNull()
  })

  it('says so when the project is not in this company', async () => {
    mocks.getProject.mockRejectedValue(Object.assign(new Error('No project with that id in this company.'), { status: 404 }))
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('This project does not exist in your company')
  })
})

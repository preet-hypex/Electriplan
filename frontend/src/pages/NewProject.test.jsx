import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PROJECT, VIEWER_COMPANY, company } from '../test/projects'

const mocks = vi.hoisted(() => ({ company: null, createProject: vi.fn(), distributorsIn: vi.fn(), findAddresses: vi.fn() }))

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u1', email: 'sam@example.com', user_metadata: {} }, signOut: vi.fn() }),
}))
vi.mock('../context/CompanyContext', () => ({ useCompany: () => mocks.company }))
vi.mock('../api/projects', async original => ({
  ...(await original()),
  createProject: mocks.createProject,
  distributorsIn: mocks.distributorsIn,
  findAddresses: mocks.findAddresses,
}))

import NewProject from './NewProject'

const renderPage = () => render(
  <MemoryRouter initialEntries={['/projects/new']}>
    <Routes>
      <Route path="/projects/new" element={<NewProject />} />
      <Route path="/projects/:id" element={<p>Opened the project</p>} />
    </Routes>
  </MemoryRouter>,
)

describe('NewProject', () => {
  beforeEach(() => {
    mocks.company = company()
    mocks.createProject.mockReset().mockResolvedValue(PROJECT)
    mocks.distributorsIn.mockReset().mockImplementation(state =>
      Promise.resolve(state === 'VIC' ? [{ code: 'jemena', name: 'Jemena', state: 'VIC' }, { code: 'citipower', name: 'CitiPower', state: 'VIC' }] : []))
  })

  it('creates the project from what was entered, then opens it', async () => {
    renderPage()
    await userEvent.type(screen.getByLabelText('Name'), '  12 Example St ')
    await userEvent.type(screen.getByLabelText('Suburb'), 'Brunswick')
    await userEvent.selectOptions(screen.getByLabelText(/^State/), 'VIC')
    await userEvent.type(screen.getByLabelText('Postcode'), '3056')
    await waitFor(() => expect(screen.getByRole('option', { name: 'Jemena' })).toBeInTheDocument())
    await userEvent.selectOptions(screen.getByLabelText('Distributor'), 'jemena')
    await userEvent.click(screen.getByRole('button', { name: 'Create project' }))

    expect(await screen.findByText('Opened the project')).toBeInTheDocument()
    expect(mocks.createProject).toHaveBeenCalledWith({
      name: '12 Example St',
      description: undefined, lotNumber: undefined, dueOn: undefined,
      site: { street: undefined, suburb: 'Brunswick', state: 'VIC', postcode: '3056' },
      distributor: 'jemena', supplyPhases: 1, status: 'active', version: undefined,
    })
  })

  it('fills the site from a found address, keeping the fields editable', async () => {
    mocks.findAddresses.mockResolvedValue([{ label: '12 Glenlyon Road, Brunswick VIC 3056', street: '12 Glenlyon Road',
      suburb: 'Brunswick', state: 'VIC', postcode: '3056', latitude: -37.77, longitude: 144.96 }])
    renderPage()
    await userEvent.type(screen.getByRole('combobox', { name: 'Find the address' }), '12 glen')
    await userEvent.click(await screen.findByRole('option', { name: /12 Glenlyon Road/ }))

    expect(screen.getByLabelText('Street')).toHaveValue('12 Glenlyon Road')
    expect(screen.getByLabelText('Suburb')).toHaveValue('Brunswick')
    expect(screen.getByLabelText(/^State/)).toHaveValue('VIC')
    expect(screen.getByLabelText('Postcode')).toHaveValue('3056')
    expect(await screen.findByRole('option', { name: 'Jemena' })).toBeInTheDocument()
    await userEvent.clear(screen.getByLabelText('Street'))
    await userEvent.type(screen.getByLabelText('Street'), '14 Glenlyon Road')
    expect(screen.getByLabelText('Street')).toHaveValue('14 Glenlyon Road')
  })

  it('offers only the chosen state’s distributors, and none until a state is chosen', async () => {
    renderPage()
    expect(screen.getByLabelText('Distributor')).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText(/^State/), 'NSW')
    expect(await screen.findByText('None set up in NSW yet')).toBeInTheDocument()
    expect(screen.getByLabelText('Distributor')).toBeDisabled()
    await userEvent.selectOptions(screen.getByLabelText(/^State/), 'VIC')
    expect(await screen.findByRole('option', { name: 'CitiPower' })).toBeInTheDocument()
  })

  it('shows each field error the API gives beside its field', async () => {
    mocks.createProject.mockRejectedValue(Object.assign(new Error('Check the highlighted fields.'), {
      status: 400,
      body: { message: 'Check the highlighted fields.', errors: [
        { field: 'name', message: 'Give the project a name' },
        { field: 'site.state', message: 'Choose the state the site is in' },
      ] },
    }))
    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Create project' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Check the highlighted fields.')
    expect(screen.getByText('Give the project a name')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('Choose the state the site is in')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create project' })).toBeEnabled()
  })

  it('tells roles that cannot start projects', () => {
    mocks.company = company({ company: VIEWER_COMPANY })
    renderPage()
    expect(screen.getByText(/your role cannot start projects/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create project' })).toBeNull()
  })
})

import { act, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { COMPANY } from '../test/projects'

const mocks = vi.hoisted(() => ({ user: null, myCompanies: vi.fn(), currentCompany: vi.fn(), setCompany: vi.fn() }))

vi.mock('./AuthContext', () => ({ useAuth: () => ({ user: mocks.user }) }))
vi.mock('../lib/api', () => ({ setCompany: mocks.setCompany }))
vi.mock('../api/projects', () => ({ myCompanies: mocks.myCompanies, currentCompany: mocks.currentCompany }))

import { CompanyProvider, useCompany } from './CompanyContext'

const OTHER = { id: 'c0000000-0000-4000-8000-000000000002', name: 'Builder Co', role: 'electrician', licence: 'active' }
let seen
function Probe() {
  seen = useCompany()
  return <p>{seen.status}{seen.company ? `: ${seen.company.name}` : ''}</p>
}
const renderProvider = () => render(<CompanyProvider><Probe /></CompanyProvider>)

describe('CompanyContext', () => {
  beforeEach(() => {
    localStorage.clear()
    mocks.user = { id: 'u1' }
    mocks.setCompany.mockReset()
    mocks.myCompanies.mockReset().mockResolvedValue([{ id: COMPANY.id, name: 'Hypex', role: 'owner', licence: 'active' }, OTHER])
    mocks.currentCompany.mockReset().mockImplementation(async () => {
      const id = mocks.setCompany.mock.calls.at(-1)[0]
      return id === OTHER.id ? { ...OTHER, permissions: ['company.view'] } : COMPANY
    })
  })

  it('works in the first company, sends it with every call, and knows the role’s permissions', async () => {
    renderProvider()
    expect(await screen.findByText('ready: Hypex')).toBeInTheDocument()
    expect(mocks.setCompany).toHaveBeenLastCalledWith(COMPANY.id)
    expect(seen.can('project.edit')).toBe(true)
    expect(seen.can('licence.view')).toBe(false)
    expect(seen.companies).toHaveLength(2)
  })

  it('switches company, and remembers the choice for next time', async () => {
    renderProvider()
    await screen.findByText('ready: Hypex')
    await act(() => seen.switchTo(OTHER.id))
    expect(await screen.findByText('ready: Builder Co')).toBeInTheDocument()
    expect(mocks.setCompany).toHaveBeenLastCalledWith(OTHER.id)
    expect(seen.can('project.edit')).toBe(false)
    expect(localStorage.getItem('electriplan.company')).toBe(OTHER.id)
  })

  it('starts in the company chosen last time', async () => {
    localStorage.setItem('electriplan.company', OTHER.id)
    renderProvider()
    expect(await screen.findByText('ready: Builder Co')).toBeInTheDocument()
  })

  it('says so when the person is in no company, and sends none', async () => {
    mocks.myCompanies.mockResolvedValue([])
    renderProvider()
    expect(await screen.findByText('none')).toBeInTheDocument()
    expect(mocks.setCompany).toHaveBeenLastCalledWith(null)
    expect(mocks.currentCompany).not.toHaveBeenCalled()
  })

  it('reports an API that does not answer', async () => {
    mocks.myCompanies.mockRejectedValue(new Error('down'))
    renderProvider()
    expect(await screen.findByText('error')).toBeInTheDocument()
    expect(seen.error).toBe('down')
  })

  it('forgets the company when signed out', async () => {
    mocks.user = null
    renderProvider()
    await waitFor(() => expect(mocks.setCompany).toHaveBeenCalledWith(null))
    expect(mocks.myCompanies).not.toHaveBeenCalled()
  })
})

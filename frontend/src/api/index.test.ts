import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../lib/api', () => ({ api: mocks.api }))

import { apiGet, COMPANY_HEADER, type CurrentCompany, type GetPath, type GetResult, type Permission } from './index'

describe('apiGet', () => {
  afterEach(() => mocks.api.mockReset())

  it('calls the path as given', async () => {
    mocks.api.mockResolvedValue({ id: 'me' })
    await apiGet('/api/me')
    expect(mocks.api).toHaveBeenCalledWith('/api/me', {})
  })

  it('sends the company header for a company-scoped path', async () => {
    mocks.api.mockResolvedValue({})
    await apiGet('/api/organisations/current', { company: 'c0ffee00-0000-4000-8000-000000000001' })
    expect(mocks.api).toHaveBeenCalledWith('/api/organisations/current', {
      headers: { [COMPANY_HEADER]: 'c0ffee00-0000-4000-8000-000000000001' },
    })
    expect(COMPANY_HEADER).toBe('X-Organisation-Id')
  })

  it('adds query parameters, leaving out undefined ones', async () => {
    mocks.api.mockResolvedValue([])
    await apiGet('/api/reference/distributors', { query: { state: 'VIC' } })
    expect(mocks.api).toHaveBeenCalledWith('/api/reference/distributors?state=VIC', {})
    await apiGet('/api/reference/distributors', { query: { state: undefined } })
    expect(mocks.api).toHaveBeenLastCalledWith('/api/reference/distributors', {})
  })

  it('passes errors through', async () => {
    mocks.api.mockRejectedValue(Object.assign(new Error('As a builder you cannot do this (licence.view).'), { status: 403 }))
    await expect(apiGet('/api/organisations/current/licence')).rejects.toMatchObject({ status: 403 })
  })
})

describe('the generated types', () => {
  it('match the API', () => {
    expectTypeOf<GetResult<'/api/organisations/current'>>().toEqualTypeOf<CurrentCompany>()
    expectTypeOf<CurrentCompany['permissions']>().toEqualTypeOf<Permission[]>()
    expectTypeOf<'licence.view'>().toMatchTypeOf<Permission>()
    expectTypeOf<GetResult<'/api/organisations/current/licence'>['licenceEndsOn']>().toEqualTypeOf<string | null>()
    expectTypeOf<GetResult<'/api/me'>['copy']>().toMatchTypeOf<object | null>()
    expectTypeOf<'/api/me'>().toMatchTypeOf<GetPath>()
  })

  it('reject what the API would refuse', () => {
    // Checked by the type checker only (npm run typecheck); never called.
    const refused = () => [
      // @ts-expect-error /api/me is not company-scoped
      apiGet('/api/me', { company: 'x' }),
      // @ts-expect-error not a state
      apiGet('/api/reference/distributors', { query: { state: 'XX' } }),
      // @ts-expect-error not an API path
      apiGet('/api/nope'),
    ]
    expect(typeof refused).toBe('function')
  })
})

// Shared test data for the projects pages: one company, projects and houses as the API returns them.
export const COMPANY = {
  id: 'c0000000-0000-4000-8000-000000000001', name: 'Hypex', slug: 'hypex', role: 'owner', licence: 'active',
  permissions: ['company.view', 'project.edit', 'floor-plan.edit'],
}

export const VIEWER_COMPANY = { ...COMPANY, role: 'viewer', permissions: ['company.view'] }

export function company(overrides = {}) {
  const value = { status: 'ready', companies: [COMPANY], company: COMPANY, error: null, switchTo: () => {}, reload: () => {}, ...overrides }
  value.can = permission => value.company?.permissions?.includes(permission) ?? false
  return value
}

export const PROJECT = {
  id: 'p0000000-0000-4000-8000-000000000001', reference: 'PRJ-000042', name: '12 Example St', description: null,
  status: 'active', lotNumber: '12', site: { street: '12 Example St', suburb: 'Brunswick', state: 'VIC', postcode: '3056' },
  distributor: 'jemena', supplyPhases: 1, dueOn: null, archived: false, archivedAt: null,
  createdAt: '2026-10-01T00:00:00Z', lastActivityAt: '2026-10-08T00:00:00Z', version: 3,
  houses: [
    { id: 'h1', name: 'Type A', dwellingType: 'house', stage: 'awaiting_upload', archived: false, updatedAt: '2026-10-08T00:00:00Z' },
    { id: 'h2', name: 'Old design', dwellingType: 'house', stage: 'floor_plan_review', archived: true, updatedAt: '2026-10-01T00:00:00Z' },
  ],
}

export const SUMMARY = {
  id: PROJECT.id, reference: 'PRJ-000042', name: '12 Example St', suburb: 'Brunswick', state: 'VIC', status: 'active',
  archived: false, houseCount: 3, stages: [{ stage: 'awaiting_upload', count: 2 }, { stage: 'analysing', count: 1 }],
  lastActivityAt: '2026-10-08T00:00:00Z',
}

export const page = (items, total = items.length) => ({ items, page: 0, size: 25, total })

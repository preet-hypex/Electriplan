/**
 * Every call the projects pages make, typed from the API's contract. Each is
 * one line over lib/api, which adds the sign-in token and the company.
 */
import { api } from '../lib/api'
import type {
  AddressSuggestion, CurrentCompany, Distributor, House, HouseForm, HouseStage, Membership, Project, ProjectForm, ProjectPage,
  ProjectStatus, RecentHouse, StageEvent,
} from './index'

export type ProjectQuery = {
  q?: string
  status?: ProjectStatus
  archived?: boolean
  page?: number
  size?: number
}

/** "?q=x&page=1" from the values that are set. */
export function queryString(values: Record<string, string | number | boolean | undefined>): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  const text = params.toString()
  return text ? `?${text}` : ''
}

const post = (json?: unknown) => ({ method: 'POST', ...(json === undefined ? {} : { json }) })
const put = (json: unknown) => ({ method: 'PUT', json })

// Companies
export const myCompanies = (): Promise<Membership[]> => api('/api/organisations')
export const currentCompany = (): Promise<CurrentCompany> => api('/api/organisations/current')

// Projects
export const listProjects = (query: ProjectQuery = {}): Promise<ProjectPage> => api(`/api/projects${queryString(query)}`)
export const createProject = (form: ProjectForm): Promise<Project> => api('/api/projects', post(form))
export const getProject = (id: string): Promise<Project> => api(`/api/projects/${id}`)
export const updateProject = (id: string, form: ProjectForm): Promise<Project> => api(`/api/projects/${id}`, put(form))
export const archiveProject = (id: string): Promise<Project> => api(`/api/projects/${id}/archive`, post())
export const restoreProject = (id: string): Promise<Project> => api(`/api/projects/${id}/restore`, post())

// Houses
export const addHouse = (projectId: string, form: HouseForm): Promise<House> =>
  api(`/api/projects/${projectId}/houses`, post(form))
export const recentHouses = (size = 6): Promise<RecentHouse[]> => api(`/api/houses/recent${queryString({ size })}`)
/** How the house got where it is: every stage move, newest first. */
export const houseStages = (id: string): Promise<StageEvent[]> => api(`/api/houses/${id}/stages`)
export const getHouse = (id: string): Promise<House> => api(`/api/houses/${id}`)
export const updateHouse = (id: string, form: HouseForm): Promise<House> => api(`/api/houses/${id}`, put(form))
export const archiveHouse = (id: string): Promise<House> => api(`/api/houses/${id}/archive`, post())
export const restoreHouse = (id: string): Promise<House> => api(`/api/houses/${id}/restore`, post())

// Reference data
export const distributorsIn = (state: string): Promise<Distributor[]> =>
  api(`/api/reference/distributors${queryString({ state })}`)

/** Addresses matching what has been typed (3+ characters), split into the form's fields. */
export const findAddresses = (typed: string): Promise<AddressSuggestion[]> =>
  api(`/api/reference/addresses${queryString({ q: typed })}`)

// ---- Words for the codes (as the database's plan_stage labels) ----

export const STAGE_LABELS: Record<HouseStage, string> = {
  awaiting_upload: 'Awaiting floor plan',
  analysing: 'Analysing floor plan',
  floor_plan_review: 'Checking floor plan',
  floor_plan_approved: 'Floor plan approved',
  electrical_design: 'Designing electrical',
  electrical_review: 'With electrician',
  changes_requested: 'Changes requested',
  design_approved: 'Design approved',
  quoting: 'Preparing quote',
  quote_sent: 'Quote sent',
  won: 'Won',
  lost: 'Lost',
  on_hold: 'On hold',
  archived: 'Archived',
}

/** The colour family a stage is shown in. */
export function stageTone(stage: HouseStage): 'todo' | 'active' | 'done' | 'muted' | 'warn' {
  switch (stage) {
    case 'awaiting_upload': return 'todo'
    case 'changes_requested': return 'warn'
    case 'design_approved': case 'quote_sent': case 'won': return 'done'
    case 'lost': case 'on_hold': case 'archived': return 'muted'
    default: return 'active'
  }
}

export const STATUS_LABELS: Record<ProjectStatus, string> = {
  active: 'Active',
  on_hold: 'On hold',
  completed: 'Completed',
  cancelled: 'Cancelled',
}

export const DWELLING_LABELS: Record<NonNullable<HouseForm['dwellingType']>, string> = {
  house: 'House',
  townhouse: 'Townhouse',
  unit: 'Unit',
  granny_flat: 'Granny flat',
  extension: 'Extension',
  other: 'Other',
}

export const STATES = ['VIC', 'NSW', 'QLD', 'SA', 'WA', 'TAS', 'ACT', 'NT'] as const

/** The API's field errors ({errors: [{field, message}]}) as {field: message}, for showing beside inputs. */
export function fieldErrors(error: unknown): Record<string, string> {
  const errors = (error as { body?: { errors?: { field: string; message: string }[] } })?.body?.errors ?? []
  return Object.fromEntries(errors.map(e => [e.field, e.message]))
}

/** "3 min ago", "yesterday", or a date: when something last changed. */
export function ago(iso: string, now: Date = new Date()): string {
  const seconds = Math.max(0, (now.getTime() - new Date(iso).getTime()) / 1000)
  if (seconds < 60) return 'just now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`
  if (seconds < 172800) return 'yesterday'
  if (seconds < 604800) return `${Math.floor(seconds / 86400)} days ago`
  return new Date(iso).toLocaleDateString('en-AU', { day: 'numeric', month: 'short', year: 'numeric' })
}

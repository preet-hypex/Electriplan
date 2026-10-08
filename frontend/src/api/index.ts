/**
 * The Java API's types, generated from contracts/openapi.json (see
 * scripts/api-types.mjs), and a typed GET on top of lib/api.
 *
 *   const company = await apiGet('/api/organisations/current', { company: id })
 *   company.permissions.includes('licence.view')
 */
import { api } from '../lib/api'
import type { components, paths } from './schema'

export type { components, operations, paths } from './schema'

type Schemas = components['schemas']

export type Me = Schemas['Me']
export type SupabaseUser = Schemas['SupabaseUser']
export type Membership = Schemas['Membership']
export type CurrentCompany = Schemas['Current']
export type Licence = Schemas['Licence']
export type Distributor = Schemas['Distributor']
export type ErrorMessage = Schemas['ErrorMessage']
export type MemberRole = Schemas['MemberRole']
export type LicenceStatus = Schemas['LicenceStatus']
export type Permission = Schemas['Permission']
export type AustralianState = Schemas['AustralianState']
export type ProjectPage = Schemas['ProjectPage']
export type ProjectSummary = Schemas['ProjectSummary']
export type Project = Schemas['Project']
export type ProjectForm = Schemas['ProjectForm']
export type ProjectStatus = Schemas['ProjectStatus']
export type House = Schemas['House']
export type HouseSummary = Schemas['HouseSummary']
export type HouseForm = Schemas['HouseForm']
export type HouseStage = Schemas['HouseStage']
export type DwellingType = Schemas['DwellingType']
export type ValidationProblem = Schemas['ValidationProblem']
export type RecentHouse = Schemas['RecentHouse']
export type SiteForm = Schemas['SiteForm']
export type AddressSuggestion = Schemas['AddressSuggestion']
export type FloorPlanDocument = Schemas['FloorPlanDocument']
export type FloorPlanVersion = Schemas['FloorPlanVersion']

/** The header that picks the company a company-scoped request acts in. */
export const COMPANY_HEADER = 'X-Organisation-Id'

type GetOperation<P extends keyof paths> = paths[P] extends { get: infer O } ? O : never

/** Paths that have a GET. */
export type GetPath = { [P in keyof paths]: GetOperation<P> extends never ? never : P }[keyof paths]

/** The JSON a GET answers with on 200. */
export type GetResult<P extends GetPath> =
  GetOperation<P> extends { responses: { 200: { content: { 'application/json': infer T } } } } ? T : never

/** The query parameters a GET takes, if any. */
export type GetQuery<P extends GetPath> =
  GetOperation<P> extends { parameters: { query?: infer Q } } ? (Q extends undefined ? never : Q) : never

/** Whether a GET works inside one company (takes the company header). */
type TakesCompany<P extends GetPath> =
  GetOperation<P> extends { parameters: { header?: infer H } } ? (H extends { [COMPANY_HEADER]?: unknown } ? true : false) : false

export type GetOptions<P extends GetPath> = {
  query?: GetQuery<P>
} & (TakesCompany<P> extends true ? { company?: string } : { company?: never })

/**
 * GET an API path with its types. Errors are thrown as lib/api throws them:
 * an Error with the API's message, and status and body attached.
 */
export function apiGet<P extends GetPath>(path: P, options: GetOptions<P> = {} as GetOptions<P>): Promise<GetResult<P>> {
  const query = options.query as Record<string, string | number | boolean | undefined> | undefined
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined) params.set(key, String(value))
  }
  const search = params.size ? `?${params}` : ''
  const headers = options.company ? { [COMPANY_HEADER]: options.company } : undefined
  return api(`${path}${search}`, headers ? { headers } : {}) as Promise<GetResult<P>>
}

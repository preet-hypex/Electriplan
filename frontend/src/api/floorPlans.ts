/**
 * A house's floor plan: the draft the editor saves as it goes, and the
 * versions frozen from it. Typed from the API's contract.
 */
import { api } from '../lib/api'
import type { FloorPlanDocument, FloorPlanVersion } from './index'
import type { FloorPlan } from '../floorplan/model/types'

const base = (houseId: string) => `/api/houses/${houseId}/floor-plan`

/** The draft, or the newest version; rejects with status 404 when the house has no floor plan yet. */
export const openFloorPlan = (houseId: string): Promise<FloorPlanDocument> => api(base(houseId))

/**
 * Saves the plan as the house's draft. `version` is the draft's version from
 * the last open or save, or undefined when there is no draft yet.
 */
export const saveFloorPlanDraft = (houseId: string, plan: FloorPlan, version?: number): Promise<FloorPlanDocument> =>
  api(`${base(houseId)}/draft`, { method: 'PUT', json: { document: plan, version } })

export const saveFloorPlanVersion = (houseId: string, version: number, note?: string): Promise<FloorPlanVersion> =>
  api(`${base(houseId)}/versions`, { method: 'POST', json: { version, note } })

export const floorPlanHistory = (houseId: string): Promise<FloorPlanVersion[]> => api(`${base(houseId)}/versions`)

export const restoreFloorPlanVersion = (houseId: string, versionNo: number, version?: number): Promise<FloorPlanDocument> =>
  api(`${base(houseId)}/versions/${versionNo}/restore`, { method: 'POST', json: { version } })

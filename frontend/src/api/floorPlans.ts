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

/**
 * Uploads an image for the house: the API keeps it in the company's files,
 * the analyser reads it, and the plan becomes the house's draft (returned).
 * `version` is the draft's, when the house has one.
 */
export function uploadFloorPlanImage(houseId: string, file: File, version?: number): Promise<FloorPlanDocument> {
  const form = new FormData()
  form.append('file', file)
  if (version !== undefined) form.append('version', String(version))
  return api(`${base(houseId)}/uploads`, { method: 'POST', form })
}

export const saveFloorPlanVersion = (houseId: string, version: number, note?: string): Promise<FloorPlanVersion> =>
  api(`${base(houseId)}/versions`, { method: 'POST', json: { version, note } })

/**
 * Approves the floor plan: the draft (if any) is saved as a version with the
 * note, and the house moves to "Floor plan approved". Returns that version.
 */
export const approveFloorPlan = (houseId: string, version?: number, note?: string): Promise<FloorPlanDocument> =>
  api(`${base(houseId)}/approve`, { method: 'POST', json: { version, note } })

export const floorPlanHistory = (houseId: string): Promise<FloorPlanVersion[]> => api(`${base(houseId)}/versions`)

export const restoreFloorPlanVersion = (houseId: string, versionNo: number, version?: number): Promise<FloorPlanDocument> =>
  api(`${base(houseId)}/versions/${versionNo}/restore`, { method: 'POST', json: { version } })

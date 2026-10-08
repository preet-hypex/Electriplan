import type { FloorPlan } from '../model/types'
import { parseFloorPlan } from '../model/serialise'
import { COMPANY_HEADER, company } from '../../lib/api'

/** Same-origin by default; Vite (dev) and nginx (Docker) proxy /api/floorplan to the analyser. */
const BASE = import.meta.env.VITE_API_URL ?? ''

export function apiUrl(path: string): string {
  if (/^(https?:)?\/\//.test(path) || path.startsWith('data:') || path.startsWith('blob:')) {
    return path
  }
  return `${BASE}${path.startsWith('/') ? path : `/${path}`}`
}

/**
 * Where the analyser's access token comes from. Every call except health sends
 * it as a Bearer token; the analyser refuses anything without a valid one.
 * The app sets this (FloorPlanApp's getAccessToken) so the editor itself
 * knows nothing about Supabase.
 */
type AccessTokenProvider = () => Promise<string | null>
let accessToken: AccessTokenProvider = async () => null

export function setAccessTokenProvider(provider: AccessTokenProvider): void {
  accessToken = provider
}

async function authHeaders(): Promise<Record<string, string>> {
  const token = await accessToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
  }
}

async function unwrap(res: Response): Promise<unknown> {
  if (!res.ok) {
    let detail = res.statusText
    try {
      const body = await res.json()
      if (body && typeof body.detail === 'string') detail = body.detail
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(detail, res.status)
  }
  return res.json()
}

export interface AnalyseOptions {
  /** Override the auto-estimated scale, in mm per source pixel. */
  mmPerPx?: number
}

/**
 * What the analyser puts in source.imageUrl when it is not told where the
 * image is kept: it keeps no files, so the caller shows its own copy.
 */
export const LOCAL_IMAGE = 'local:uploaded-image'

/**
 * Upload an image and get back a FloorPlan reconstructed from its pixels, for
 * the scratch editor (a plan not saved to a house). Nothing keeps the image,
 * so the plan shows the picked file from the browser; a house's uploads are
 * kept by the API instead (api/floorPlans uploadFloorPlanImage).
 */
export async function analyse(file: File, options: AnalyseOptions = {}): Promise<FloorPlan> {
  const form = new FormData()
  form.append('file', file)
  if (options.mmPerPx !== undefined) form.append('mm_per_px', String(options.mmPerPx))

  const res = await fetch(apiUrl('/api/floorplan/analyse'), {
    method: 'POST',
    body: form,
    headers: await authHeaders(),
  })
  const plan = parseFloorPlan(await unwrap(res))
  if (plan.source?.imageUrl === LOCAL_IMAGE) {
    plan.source.imageUrl = URL.createObjectURL(file)
  }
  return plan
}

export interface CalibrateRequest {
  pixels: number
  millimetres: number
  /** Current mm/px, so the response can report the correction factor. */
  currentMmPerPx?: number
}

export interface CalibrateResponse {
  mmPerPx: number
  factor: number
  confidence: number
}

export async function calibrate(req: CalibrateRequest): Promise<CalibrateResponse> {
  const res = await fetch(apiUrl('/api/floorplan/calibrate'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
    body: JSON.stringify({
      pixels: req.pixels,
      millimetres: req.millimetres,
      current_mm_per_px: req.currentMmPerPx,
    }),
  })
  const body = (await unwrap(res)) as Record<string, number>
  return { mmPerPx: body.mm_per_px, factor: body.factor, confidence: body.confidence }
}

/**
 * Round-trip the plan through the backend's validator and get canonical JSON
 * back. The frontend could stringify locally, but going through /api/export
 * means the saved file is the shape the backend guarantees.
 */
export async function exportPlan(plan: FloorPlan): Promise<string> {
  const res = await fetch(apiUrl('/api/floorplan/export'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(await authHeaders()) },
    body: JSON.stringify({ format: 'json', plan }),
  })
  if (!res.ok) throw new ApiError(await res.text(), res.status)
  return res.text()
}

/**
 * An uploaded image, fetched with the access token, as an object URL for an
 * <img> or SVG <image> (which cannot send headers themselves). The caller
 * revokes it with URL.revokeObjectURL when done.
 */
export async function fetchImageObjectUrl(path: string): Promise<string> {
  // A house's image is one of its company's files: say which company.
  const chosen = company()
  const res = await fetch(apiUrl(path), {
    headers: { ...(await authHeaders()), ...(chosen ? { [COMPANY_HEADER]: chosen } : {}) },
  })
  if (!res.ok) throw new ApiError(`The plan image could not be loaded (${res.status}).`, res.status)
  return URL.createObjectURL(await res.blob())
}

export async function health(): Promise<boolean> {
  try {
    const res = await fetch(apiUrl('/api/floorplan/health'))
    return res.ok
  } catch {
    return false
  }
}

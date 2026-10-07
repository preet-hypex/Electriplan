import type { FloorPlan } from '../model/types'
import { parseFloorPlan } from '../model/serialise'

/** Same-origin by default; Vite (dev) and nginx (Docker) proxy /api/floorplan to the analyser. */
const BASE = import.meta.env.VITE_API_URL ?? ''

export function apiUrl(path: string): string {
  if (/^(https?:)?\/\//.test(path) || path.startsWith('data:') || path.startsWith('blob:')) {
    return path
  }
  return `${BASE}${path.startsWith('/') ? path : `/${path}`}`
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

/** Upload an image and get back a FloorPlan reconstructed from its pixels. */
export async function analyse(file: File, options: AnalyseOptions = {}): Promise<FloorPlan> {
  const form = new FormData()
  form.append('file', file)
  if (options.mmPerPx !== undefined) form.append('mm_per_px', String(options.mmPerPx))

  const res = await fetch(apiUrl('/api/floorplan/analyse'), { method: 'POST', body: form })
  return parseFloorPlan(await unwrap(res))
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
    headers: { 'Content-Type': 'application/json' },
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
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ format: 'json', plan }),
  })
  if (!res.ok) throw new ApiError(await res.text(), res.status)
  return res.text()
}

export async function health(): Promise<boolean> {
  try {
    const res = await fetch(apiUrl('/api/floorplan/health'))
    return res.ok
  } catch {
    return false
  }
}

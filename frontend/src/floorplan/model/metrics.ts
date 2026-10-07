/**
 * Figures and checks derived from a FloorPlan, for the editor's schedule,
 * navigator and checks panels. Pure functions of the plan: nothing here is
 * stored, so nothing here can fall out of step with the geometry.
 */
import type { FloorPlan, Point, Room } from './types'
import { bandOf } from './confidence'
import { polygonArea } from '../geometry/polygon'
import { distance } from '../geometry/vec'
import type { Selection } from '../state/store'

/** Below this, the scale was a guess and every length on the plan is suspect. */
export const SCALE_CONFIDENCE_FLOOR = 0.6

export function roomAreaM2(room: Room): number {
  return polygonArea(room.polygon) / 1_000_000
}

export function polygonPerimeterM(poly: Point[]): number {
  let mm = 0
  for (let i = 0; i < poly.length; i++) mm += distance(poly[i], poly[(i + 1) % poly.length])
  return mm / 1000
}

export interface ScheduleRow {
  id: string
  name: string
  areaM2: number
  perimeterM: number
  confidence: number | undefined
}

export interface RoomSchedule {
  rows: ScheduleRow[]
  totalAreaM2: number
  totalPerimeterM: number
}

/** One row per room, largest first, with totals. */
export function roomSchedule(plan: FloorPlan): RoomSchedule {
  const rows = plan.rooms
    .map((r) => ({
      id: r.id,
      name: r.name.trim(),
      areaM2: roomAreaM2(r),
      perimeterM: polygonPerimeterM(r.polygon),
      confidence: r.confidence,
    }))
    .sort((a, b) => b.areaM2 - a.areaM2 || a.id.localeCompare(b.id))
  return {
    rows,
    totalAreaM2: rows.reduce((s, r) => s + r.areaM2, 0),
    totalPerimeterM: rows.reduce((s, r) => s + r.perimeterM, 0),
  }
}

export type CheckSeverity = 'warning' | 'info'

export interface Check {
  /** Stable across re-renders, for React keys. */
  key: string
  severity: CheckSeverity
  title: string
  detail: string
  /** The object to select when the check is clicked, if it is about one. */
  target: Selection
}

const KIND_NAMES = {
  wall: 'Wall',
  room: 'Room',
  door: 'Door',
  window: 'Window',
  opening: 'Opening',
  label: 'Label',
} as const

/**
 * What to look at before trusting the plan, most important first. Warnings
 * are things likely to be wrong; info is worth a glance.
 */
export function planChecks(plan: FloorPlan): Check[] {
  const checks: Check[] = []

  const source = plan.source
  if (source && (source.scaleMethod === 'fallback' || source.scaleConfidence < SCALE_CONFIDENCE_FLOOR)) {
    checks.push({
      key: 'scale',
      severity: 'warning',
      title: 'Scale is uncertain',
      detail:
        source.scaleMethod === 'fallback'
          ? 'No dimension could be read, so the scale is an assumption. Calibrate it against a length you know.'
          : `Scale confidence is ${Math.round(source.scaleConfidence * 100)}%. Calibrate it against a length you know.`,
      target: null,
    })
  }

  for (const r of plan.rooms) {
    if (!r.name.trim()) {
      checks.push({
        key: `unnamed-${r.id}`,
        severity: 'warning',
        title: 'Room has no name',
        detail: `${r.id}, ${roomAreaM2(r).toFixed(1)} m². Name it so it can be scheduled.`,
        target: { kind: 'room', id: r.id },
      })
    }
  }

  for (const o of plan.openings) {
    checks.push({
      key: `gap-${o.id}`,
      severity: 'info',
      title: 'Opening not classified',
      detail: `${o.id}, ${Math.round(o.width)} mm wide. Make it a door or a window, or leave it as a passage.`,
      target: { kind: 'opening', id: o.id },
    })
  }

  const lowConfidence: Array<[keyof typeof KIND_NAMES, { id: string; confidence?: number }[]]> = [
    ['wall', plan.walls],
    ['room', plan.rooms],
    ['door', plan.doors],
    ['window', plan.windows],
    ['label', plan.labels],
  ]
  for (const [kind, items] of lowConfidence) {
    for (const item of items) {
      if (bandOf(item) !== 'low') continue
      checks.push({
        key: `low-${kind}-${item.id}`,
        severity: 'warning',
        title: `${KIND_NAMES[kind]} detected with low confidence`,
        detail: `${item.id}, ${Math.round((item.confidence ?? 0) * 100)}%. Check it against the original image.`,
        target: { kind, id: item.id },
      })
    }
  }

  for (const [i, w] of (plan.analysis?.warnings ?? []).entries()) {
    checks.push({ key: `analysis-${i}`, severity: 'info', title: 'Analysis note', detail: w, target: null })
  }

  const rank = { warning: 0, info: 1 }
  return checks.sort((a, b) => rank[a.severity] - rank[b.severity])
}

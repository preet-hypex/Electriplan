import type { FloorPlan, Point } from '../model/types'

export interface Bounds {
  minX: number
  minY: number
  maxX: number
  maxY: number
}

export const boundsWidth = (b: Bounds) => b.maxX - b.minX
export const boundsHeight = (b: Bounds) => b.maxY - b.minY

export function growBounds(b: Bounds, margin: number): Bounds {
  return {
    minX: b.minX - margin,
    minY: b.minY - margin,
    maxX: b.maxX + margin,
    maxY: b.maxY + margin,
  }
}

export function boundsOfPoints(points: Point[]): Bounds | null {
  if (points.length === 0) return null
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  for (const p of points) {
    if (p.x < minX) minX = p.x
    if (p.y < minY) minY = p.y
    if (p.x > maxX) maxX = p.x
    if (p.y > maxY) maxY = p.y
  }
  return { minX, minY, maxX, maxY }
}

/**
 * Bounds of everything worth looking at. Includes the source image region when
 * present so "fit to view" does not clip the background layer.
 */
export function planBounds(plan: FloorPlan): Bounds {
  const points: Point[] = []
  for (const w of plan.walls) {
    const h = w.thickness / 2
    points.push({ x: w.start.x - h, y: w.start.y - h }, { x: w.end.x + h, y: w.end.y + h })
  }
  for (const r of plan.rooms) points.push(...r.polygon)
  for (const l of plan.labels) points.push(l.position)

  const b = boundsOfPoints(points)
  if (b) return b

  if (plan.source) {
    const { planRegion, mmPerPx } = plan.source
    return {
      minX: 0,
      minY: 0,
      maxX: planRegion.width * mmPerPx,
      maxY: planRegion.height * mmPerPx,
    }
  }
  return { minX: 0, minY: 0, maxX: 10000, maxY: 10000 }
}

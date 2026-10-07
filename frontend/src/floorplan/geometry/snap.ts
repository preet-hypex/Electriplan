import type { Point, Wall } from '../model/types'
import { distance } from './vec'
import { projectOntoWall } from './wall'

export interface SnapSettings {
  /** Endpoint/wall snapping radius, in mm. */
  radius: number
  /** Grid step in mm. 0 disables grid snapping. */
  grid: number
  /**
   * Constrain new and dragged walls to the horizontal or vertical by default.
   * Holding Shift constrains regardless, so this only decides what happens
   * with no modifier held.
   */
  orthogonal: boolean
}

export const DEFAULT_SNAP: SnapSettings = {
  radius: 250,
  grid: 50,
  orthogonal: true,
}

export type SnapKind = 'endpoint' | 'wall' | 'grid' | 'none'

export interface SnapResult {
  point: Point
  kind: SnapKind
  /** The wall the point snapped onto, when kind is 'endpoint' or 'wall'. */
  wallId?: string
}

export function snapToGrid(p: Point, grid: number): Point {
  if (grid <= 0) return p
  return { x: Math.round(p.x / grid) * grid, y: Math.round(p.y / grid) * grid }
}

/**
 * Snap a free point to, in order of preference: an existing wall endpoint, a
 * point on an existing wall's centre line, then the grid.
 *
 * `excludeWallId` keeps a wall from snapping to itself while being dragged.
 */
export function snapPoint(
  p: Point,
  walls: Wall[],
  settings: SnapSettings = DEFAULT_SNAP,
  excludeWallId?: string,
): SnapResult {
  let bestEndpoint: SnapResult | null = null
  let bestOnWall: SnapResult | null = null
  let bestEndpointDist = settings.radius
  let bestOnWallDist = settings.radius

  for (const w of walls) {
    if (w.id === excludeWallId) continue

    for (const end of [w.start, w.end]) {
      const d = distance(p, end)
      if (d < bestEndpointDist) {
        bestEndpointDist = d
        bestEndpoint = { point: { ...end }, kind: 'endpoint', wallId: w.id }
      }
    }

    const proj = projectOntoWall(w, p)
    if (proj.distance < bestOnWallDist) {
      bestOnWallDist = proj.distance
      bestOnWall = { point: proj.point, kind: 'wall', wallId: w.id }
    }
  }

  if (bestEndpoint) return bestEndpoint
  if (bestOnWall) return bestOnWall
  if (settings.grid > 0) return { point: snapToGrid(p, settings.grid), kind: 'grid' }
  return { point: p, kind: 'none' }
}

/**
 * Pull `end` onto the horizontal or vertical through `start`, whichever is
 * closer. Used while drawing and while dragging an endpoint.
 */
export function constrainOrthogonal(start: Point, end: Point): Point {
  const dx = Math.abs(end.x - start.x)
  const dy = Math.abs(end.y - start.y)
  return dx >= dy ? { x: end.x, y: start.y } : { x: start.x, y: end.y }
}

/**
 * Snap while staying on an axis through `anchor`.
 *
 * Plain `snapPoint` will happily pull the point off the constraint line, which
 * defeats the whole purpose of holding Shift. This instead looks for places the
 * *constraint line itself* meets the drawing:
 *
 * - where the line crosses another wall — a T-junction, and the common case of
 *   "run this wall straight until it hits that one";
 * - an endpoint that already sits on the line, which is an exact corner.
 *
 * So the wall connects **and** stays straight, rather than having to choose.
 */
export function snapAlongAxis(
  anchor: Point,
  target: Point,
  walls: Wall[],
  settings: SnapSettings = DEFAULT_SNAP,
  excludeWallId?: string,
): SnapResult {
  const horizontal = Math.abs(target.x - anchor.x) >= Math.abs(target.y - anchor.y)
  /** Put a point back on the constraint line. */
  const onLine = (p: Point): Point =>
    horizontal ? { x: p.x, y: anchor.y } : { x: anchor.x, y: p.y }

  let best: SnapResult | null = null
  let bestDistance = settings.radius

  for (const wall of walls) {
    if (wall.id === excludeWallId) continue

    // Line up with an existing endpoint. The result is put back on the
    // constraint line rather than taken as-is: an endpoint a little off the
    // line would otherwise drag the wall off it, which is the one thing
    // holding Shift is meant to prevent. Where the endpoint is genuinely on
    // the line the projection is the endpoint, so an exact corner still is one.
    for (const end of [wall.start, wall.end]) {
      const offLine = horizontal ? Math.abs(end.y - anchor.y) : Math.abs(end.x - anchor.x)
      if (offLine > settings.radius / 2) continue
      const aligned = onLine(end)
      const along = distance(aligned, target)
      if (along < bestDistance) {
        bestDistance = along
        best = { point: aligned, kind: 'endpoint', wallId: wall.id }
      }
    }

    const crossing = axisCrossing(anchor, horizontal, wall)
    if (crossing) {
      const along = distance(crossing, target)
      if (along < bestDistance) {
        bestDistance = along
        best = { point: crossing, kind: 'wall', wallId: wall.id }
      }
    }
  }

  if (best) return best
  if (settings.grid > 0) return { point: onLine(snapToGrid(target, settings.grid)), kind: 'grid' }
  return { point: onLine(target), kind: 'none' }
}

/**
 * Where a horizontal or vertical line through `anchor` crosses a wall, or null
 * if it misses the wall's span.
 */
export function axisCrossing(anchor: Point, horizontal: boolean, wall: Wall): Point | null {
  const dx = wall.end.x - wall.start.x
  const dy = wall.end.y - wall.start.y

  if (horizontal) {
    if (Math.abs(dy) < 1e-9) return null // the wall is parallel to the line
    const t = (anchor.y - wall.start.y) / dy
    if (t < 0 || t > 1) return null
    return { x: wall.start.x + dx * t, y: anchor.y }
  }

  if (Math.abs(dx) < 1e-9) return null
  const t = (anchor.x - wall.start.x) / dx
  if (t < 0 || t > 1) return null
  return { x: anchor.x, y: wall.start.y + dy * t }
}

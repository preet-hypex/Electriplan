import type { Point, Wall } from '../model/types'
import { add, cross, distance, dot, normalise, scale, sub } from './vec'

export function wallVector(w: Wall): Point {
  return sub(w.end, w.start)
}

export function wallLength(w: Wall): number {
  return distance(w.start, w.end)
}

export function wallDirection(w: Wall): Point {
  return normalise(wallVector(w))
}

/** Angle in degrees, normalised to [0, 180) so a wall equals its reverse. */
export function wallAngle(w: Wall): number {
  const v = wallVector(w)
  let deg = (Math.atan2(v.y, v.x) * 180) / Math.PI
  if (deg < 0) deg += 180
  if (deg >= 180) deg -= 180
  return deg
}

export function wallMidpoint(w: Wall): Point {
  return { x: (w.start.x + w.end.x) / 2, y: (w.start.y + w.end.y) / 2 }
}

/**
 * Distance from `p` to the wall's centre line segment, and the parameter t in
 * [0,1] of the closest point along it.
 */
export function projectOntoWall(w: Wall, p: Point): { distance: number; t: number; point: Point } {
  const v = wallVector(w)
  const len2 = dot(v, v)
  if (len2 === 0) {
    return { distance: distance(p, w.start), t: 0, point: w.start }
  }
  const raw = dot(sub(p, w.start), v) / len2
  const t = Math.max(0, Math.min(1, raw))
  const point = add(w.start, scale(v, t))
  return { distance: distance(p, point), t, point }
}

/** True when `p` is within the wall's drawn body (centre line ± half thickness). */
export function hitTestWall(w: Wall, p: Point, extraTolerance = 0): boolean {
  const { distance: d } = projectOntoWall(w, p)
  return d <= w.thickness / 2 + extraTolerance
}

/**
 * Intersection of two wall centre lines treated as infinite lines.
 * Returns null when they are parallel (or one is degenerate).
 */
export function lineIntersection(a: Wall, b: Wall): Point | null {
  const r = wallVector(a)
  const s = wallVector(b)
  const denom = cross(r, s)
  if (Math.abs(denom) < 1e-9) return null
  const t = cross(sub(b.start, a.start), s) / denom
  return add(a.start, scale(r, t))
}

/**
 * Intersection restricted to both segments. Endpoint touches count, so a
 * T-junction reports an intersection.
 */
export function segmentIntersection(a: Wall, b: Wall, tol = 1e-6): Point | null {
  const r = wallVector(a)
  const s = wallVector(b)
  const denom = cross(r, s)
  if (Math.abs(denom) < 1e-9) return null
  const qp = sub(b.start, a.start)
  const t = cross(qp, s) / denom
  const u = cross(qp, r) / denom
  const lo = -tol
  if (t < lo || t > 1 + tol || u < lo || u > 1 + tol) return null
  return add(a.start, scale(r, t))
}

/** Are the two walls parallel within `angleTol` degrees? */
export function isParallel(a: Wall, b: Wall, angleTol = 3): boolean {
  const diff = Math.abs(wallAngle(a) - wallAngle(b))
  return Math.min(diff, 180 - diff) <= angleTol
}

/**
 * Parallel *and* sharing a centre line, within `offsetTol` mm of perpendicular
 * separation. Collinear walls are merge candidates.
 */
export function isCollinear(a: Wall, b: Wall, angleTol = 3, offsetTol = 40): boolean {
  if (!isParallel(a, b, angleTol)) return false
  const dir = wallDirection(a)
  const perpDistance = (p: Point) => Math.abs(cross(dir, sub(p, a.start)))
  return perpDistance(b.start) <= offsetTol && perpDistance(b.end) <= offsetTol
}

/** The four corners of the wall's drawn rectangle, for rendering and hit areas. */
export function wallQuad(w: Wall): Point[] {
  const d = wallDirection(w)
  const n = { x: -d.y, y: d.x }
  const h = w.thickness / 2
  return [
    add(w.start, scale(n, h)),
    add(w.end, scale(n, h)),
    add(w.end, scale(n, -h)),
    add(w.start, scale(n, -h)),
  ]
}

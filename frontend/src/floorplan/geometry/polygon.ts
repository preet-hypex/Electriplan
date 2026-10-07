import type { Point } from '../model/types'

/** Signed area × 2. Positive means clockwise in screen space (y down). */
export function signedArea2(poly: Point[]): number {
  let s = 0
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i]
    const b = poly[(i + 1) % poly.length]
    s += a.x * b.y - b.x * a.y
  }
  return s
}

/** Absolute area in the polygon's own units squared. */
export function polygonArea(poly: Point[]): number {
  if (poly.length < 3) return 0
  return Math.abs(signedArea2(poly)) / 2
}

/** Area-weighted centroid. Falls back to the vertex mean for degenerate rings. */
export function polygonCentroid(poly: Point[]): Point {
  if (poly.length === 0) return { x: 0, y: 0 }
  const a2 = signedArea2(poly)
  if (Math.abs(a2) < 1e-9) {
    const sum = poly.reduce((acc, p) => ({ x: acc.x + p.x, y: acc.y + p.y }), { x: 0, y: 0 })
    return { x: sum.x / poly.length, y: sum.y / poly.length }
  }
  let cx = 0
  let cy = 0
  for (let i = 0; i < poly.length; i++) {
    const p = poly[i]
    const q = poly[(i + 1) % poly.length]
    const f = p.x * q.y - q.x * p.y
    cx += (p.x + q.x) * f
    cy += (p.y + q.y) * f
  }
  return { x: cx / (3 * a2), y: cy / (3 * a2) }
}

/** Ray casting. Points exactly on an edge may report either way. */
export function pointInPolygon(poly: Point[], p: Point): boolean {
  let inside = false
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const a = poly[i]
    const b = poly[j]
    const straddles = a.y > p.y !== b.y > p.y
    if (straddles && p.x < ((b.x - a.x) * (p.y - a.y)) / (b.y - a.y) + a.x) {
      inside = !inside
    }
  }
  return inside
}

export function polygonBounds(poly: Point[]) {
  const xs = poly.map((p) => p.x)
  const ys = poly.map((p) => p.y)
  return {
    minX: Math.min(...xs),
    minY: Math.min(...ys),
    maxX: Math.max(...xs),
    maxY: Math.max(...ys),
  }
}

/**
 * A point guaranteed to sit inside the polygon, used to place a room label when
 * the area centroid falls outside an L-shaped room. Scans the horizontal line
 * through the centroid and takes the midpoint of the widest interior span.
 */
export function interiorPoint(poly: Point[]): Point {
  const c = polygonCentroid(poly)
  if (pointInPolygon(poly, c)) return c

  const { minX, maxX } = polygonBounds(poly)
  const xs: number[] = []
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i]
    const b = poly[(i + 1) % poly.length]
    if (a.y > c.y !== b.y > c.y) {
      xs.push(((b.x - a.x) * (c.y - a.y)) / (b.y - a.y) + a.x)
    }
  }
  xs.sort((p, q) => p - q)
  let best: Point = c
  let bestSpan = -1
  for (let i = 0; i + 1 < xs.length; i += 2) {
    const span = xs[i + 1] - xs[i]
    if (span > bestSpan) {
      bestSpan = span
      best = { x: (xs[i] + xs[i + 1]) / 2, y: c.y }
    }
  }
  return bestSpan > 0 ? best : { x: (minX + maxX) / 2, y: c.y }
}

export function polygonToPathData(poly: Point[]): string {
  if (poly.length === 0) return ''
  const [first, ...rest] = poly
  return `M ${first.x} ${first.y} ` + rest.map((p) => `L ${p.x} ${p.y}`).join(' ') + ' Z'
}

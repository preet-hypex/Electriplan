import type { Point } from '../model/types'

export const pt = (x: number, y: number): Point => ({ x, y })

export const add = (a: Point, b: Point): Point => ({ x: a.x + b.x, y: a.y + b.y })
export const sub = (a: Point, b: Point): Point => ({ x: a.x - b.x, y: a.y - b.y })
export const scale = (a: Point, k: number): Point => ({ x: a.x * k, y: a.y * k })

export const dot = (a: Point, b: Point): number => a.x * b.x + a.y * b.y
export const cross = (a: Point, b: Point): number => a.x * b.y - a.y * b.x

export const length = (a: Point): number => Math.hypot(a.x, a.y)
export const distance = (a: Point, b: Point): number => Math.hypot(b.x - a.x, b.y - a.y)

export function normalise(a: Point): Point {
  const l = length(a)
  return l === 0 ? { x: 0, y: 0 } : { x: a.x / l, y: a.y / l }
}

/** Unit vector 90° clockwise from `a` in screen space (y down). */
export const perpendicular = (a: Point): Point => ({ x: -a.y, y: a.x })

export const equalWithin = (a: Point, b: Point, tol: number): boolean =>
  Math.abs(a.x - b.x) <= tol && Math.abs(a.y - b.y) <= tol

export const round = (a: Point, dp = 1): Point => ({
  x: roundTo(a.x, dp),
  y: roundTo(a.y, dp),
})

export function roundTo(v: number, dp = 1): number {
  const f = 10 ** dp
  return Math.round(v * f) / f
}

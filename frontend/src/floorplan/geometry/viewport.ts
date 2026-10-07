import type { Point } from '../model/types'
import type { Bounds } from './bounds'

/**
 * Maps plan millimetres to canvas pixels.
 *
 *   screen = model * scale + t
 *
 * Rendered as a single SVG group transform, so the geometry itself is always
 * stored and edited in millimetres.
 */
export interface Viewport {
  /** Pixels per millimetre. */
  scale: number
  tx: number
  ty: number
}

export const MIN_SCALE = 0.002
export const MAX_SCALE = 2

export const IDENTITY_VIEWPORT: Viewport = { scale: 0.05, tx: 0, ty: 0 }

export function toScreen(v: Viewport, p: Point): Point {
  return { x: p.x * v.scale + v.tx, y: p.y * v.scale + v.ty }
}

export function toModel(v: Viewport, p: Point): Point {
  return { x: (p.x - v.tx) / v.scale, y: (p.y - v.ty) / v.scale }
}

/** Convert a pixel length (handle size, hit slop) into millimetres. */
export function pxToMm(v: Viewport, px: number): number {
  return px / v.scale
}

export function clampScale(scale: number): number {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale))
}

/** Zoom by `factor` keeping the model point under `anchor` (screen px) fixed. */
export function zoomAt(v: Viewport, anchor: Point, factor: number): Viewport {
  const scale = clampScale(v.scale * factor)
  const applied = scale / v.scale
  return {
    scale,
    tx: anchor.x - (anchor.x - v.tx) * applied,
    ty: anchor.y - (anchor.y - v.ty) * applied,
  }
}

/** Zoom to an exact scale about the centre of a viewport of the given size. */
export function zoomToScale(v: Viewport, scale: number, width: number, height: number): Viewport {
  return zoomAt(v, { x: width / 2, y: height / 2 }, clampScale(scale) / v.scale)
}

export function panBy(v: Viewport, dx: number, dy: number): Viewport {
  return { ...v, tx: v.tx + dx, ty: v.ty + dy }
}

/** Fit `bounds` into a `width` × `height` canvas with `padding` pixels of margin. */
export function fitBounds(bounds: Bounds, width: number, height: number, padding = 40): Viewport {
  const bw = Math.max(1, bounds.maxX - bounds.minX)
  const bh = Math.max(1, bounds.maxY - bounds.minY)
  const usableW = Math.max(1, width - padding * 2)
  const usableH = Math.max(1, height - padding * 2)
  const scale = clampScale(Math.min(usableW / bw, usableH / bh))
  return {
    scale,
    tx: (width - bw * scale) / 2 - bounds.minX * scale,
    ty: (height - bh * scale) / 2 - bounds.minY * scale,
  }
}

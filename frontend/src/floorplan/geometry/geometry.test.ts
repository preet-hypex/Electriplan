import { describe, expect, it } from 'vitest'
import type { Wall } from '../model/types'
import {
  hitTestWall,
  isCollinear,
  isParallel,
  lineIntersection,
  projectOntoWall,
  segmentIntersection,
  wallAngle,
  wallLength,
  wallQuad,
} from './wall'
import { interiorPoint, pointInPolygon, polygonArea, polygonCentroid } from './polygon'
import { axisCrossing, constrainOrthogonal, snapAlongAxis, snapPoint, snapToGrid } from './snap'
import { fitBounds, pxToMm, toModel, toScreen, zoomAt } from './viewport'
import { DEFAULT_WALL_THICKNESS, typicalWallThickness } from '../model/factory'

const wall = (x1: number, y1: number, x2: number, y2: number, thickness = 100): Wall => ({
  id: `w-${x1},${y1}-${x2},${y2}`,
  start: { x: x1, y: y1 },
  end: { x: x2, y: y2 },
  thickness,
})

describe('wall intersection', () => {
  it('finds the crossing point of two perpendicular walls', () => {
    const h = wall(0, 1000, 5000, 1000)
    const v = wall(2000, 0, 2000, 4000)
    expect(segmentIntersection(h, v)).toEqual({ x: 2000, y: 1000 })
  })

  it('returns null for parallel walls', () => {
    expect(segmentIntersection(wall(0, 0, 1000, 0), wall(0, 500, 1000, 500))).toBeNull()
    expect(lineIntersection(wall(0, 0, 1000, 0), wall(0, 500, 1000, 500))).toBeNull()
  })

  it('reports a T-junction where an endpoint lands on another wall', () => {
    const spine = wall(0, 0, 4000, 0)
    const branch = wall(1500, 0, 1500, 3000)
    expect(segmentIntersection(spine, branch)).toEqual({ x: 1500, y: 0 })
  })

  it('rejects an intersection that lies beyond both segments', () => {
    const a = wall(0, 0, 1000, 0)
    const b = wall(3000, -500, 3000, 500)
    expect(segmentIntersection(a, b)).toBeNull()
    // ...but the infinite lines still cross.
    expect(lineIntersection(a, b)).toEqual({ x: 3000, y: 0 })
  })

  it('measures length and angle', () => {
    expect(wallLength(wall(0, 0, 3000, 4000))).toBe(5000)
    expect(wallAngle(wall(0, 0, 1000, 0))).toBe(0)
    expect(wallAngle(wall(0, 0, 0, 1000))).toBe(90)
    // A wall equals its own reverse.
    expect(wallAngle(wall(1000, 0, 0, 0))).toBe(0)
  })

  it('projects a point onto a wall and clamps to the segment', () => {
    const w = wall(0, 0, 1000, 0)
    expect(projectOntoWall(w, { x: 400, y: 250 })).toMatchObject({ distance: 250, t: 0.4 })
    expect(projectOntoWall(w, { x: -500, y: 0 })).toMatchObject({ t: 0, distance: 500 })
    expect(projectOntoWall(w, { x: 1500, y: 0 })).toMatchObject({ t: 1, distance: 500 })
  })

  it('hit-tests within half the thickness', () => {
    const w = wall(0, 0, 1000, 0, 200)
    expect(hitTestWall(w, { x: 500, y: 99 })).toBe(true)
    expect(hitTestWall(w, { x: 500, y: 101 })).toBe(false)
  })

  it('builds the drawn rectangle around the centre line', () => {
    const quad = wallQuad(wall(0, 0, 1000, 0, 200))
    expect(quad).toEqual([
      { x: 0, y: 100 },
      { x: 1000, y: 100 },
      { x: 1000, y: -100 },
      { x: 0, y: -100 },
    ])
  })

  it('classifies parallel and collinear pairs', () => {
    expect(isParallel(wall(0, 0, 100, 0), wall(50, 900, 700, 900))).toBe(true)
    expect(isParallel(wall(0, 0, 100, 0), wall(0, 0, 0, 100))).toBe(false)
    // Same line, disjoint spans: collinear.
    expect(isCollinear(wall(0, 0, 1000, 0), wall(1400, 10, 2600, 10))).toBe(true)
    // Parallel but 900 mm apart: not collinear.
    expect(isCollinear(wall(0, 0, 1000, 0), wall(0, 900, 1000, 900))).toBe(false)
  })
})

describe('room polygon generation', () => {
  const square = [
    { x: 0, y: 0 },
    { x: 4000, y: 0 },
    { x: 4000, y: 3000 },
    { x: 0, y: 3000 },
  ]

  it('computes area regardless of winding', () => {
    expect(polygonArea(square)).toBe(12_000_000)
    expect(polygonArea([...square].reverse())).toBe(12_000_000)
  })

  it('computes the centroid', () => {
    expect(polygonCentroid(square)).toEqual({ x: 2000, y: 1500 })
  })

  it('tests containment', () => {
    expect(pointInPolygon(square, { x: 100, y: 100 })).toBe(true)
    expect(pointInPolygon(square, { x: -1, y: 100 })).toBe(false)
    expect(pointInPolygon(square, { x: 5000, y: 1500 })).toBe(false)
  })

  it('places a label inside an L-shaped room whose centroid is outside', () => {
    // An L whose area centroid falls in the notch.
    const L = [
      { x: 0, y: 0 },
      { x: 6000, y: 0 },
      { x: 6000, y: 1000 },
      { x: 1000, y: 1000 },
      { x: 1000, y: 6000 },
      { x: 0, y: 6000 },
    ]
    const c = polygonCentroid(L)
    expect(pointInPolygon(L, c)).toBe(false)
    expect(pointInPolygon(L, interiorPoint(L))).toBe(true)
  })

  it('treats a degenerate ring without dividing by zero', () => {
    const line = [
      { x: 0, y: 0 },
      { x: 100, y: 0 },
    ]
    expect(polygonArea(line)).toBe(0)
    expect(polygonCentroid(line)).toEqual({ x: 50, y: 0 })
  })
})

describe('coordinate conversion', () => {
  const v = { scale: 0.05, tx: 120, ty: -40 }

  it('round-trips model and screen space', () => {
    const model = { x: 3400, y: 2750 }
    const screen = toScreen(v, model)
    expect(screen).toEqual({ x: 3400 * 0.05 + 120, y: 2750 * 0.05 - 40 })
    const back = toModel(v, screen)
    expect(back.x).toBeCloseTo(model.x, 6)
    expect(back.y).toBeCloseTo(model.y, 6)
  })

  it('converts pixel lengths to millimetres', () => {
    expect(pxToMm(v, 6)).toBe(120)
  })

  it('keeps the anchor point fixed while zooming', () => {
    const anchor = { x: 400, y: 300 }
    const before = toModel(v, anchor)
    const zoomed = zoomAt(v, anchor, 2)
    const after = toModel(zoomed, anchor)
    expect(zoomed.scale).toBeCloseTo(0.1, 9)
    expect(after.x).toBeCloseTo(before.x, 6)
    expect(after.y).toBeCloseTo(before.y, 6)
  })

  it('clamps zoom to the allowed range', () => {
    expect(zoomAt(v, { x: 0, y: 0 }, 1e6).scale).toBeLessThanOrEqual(2)
    expect(zoomAt(v, { x: 0, y: 0 }, 1e-9).scale).toBeGreaterThan(0)
  })

  it('fits bounds into a canvas, centred', () => {
    const bounds = { minX: 0, minY: 0, maxX: 10000, maxY: 5000 }
    const fitted = fitBounds(bounds, 1000, 600, 50)
    // Width is the binding constraint: 900 usable px over 10 000 mm.
    expect(fitted.scale).toBeCloseTo(0.09, 9)
    const topLeft = toScreen(fitted, { x: 0, y: 0 })
    const bottomRight = toScreen(fitted, { x: 10000, y: 5000 })
    expect(topLeft.x).toBeCloseTo(50, 6)
    expect(bottomRight.x).toBeCloseTo(950, 6)
    // Vertically centred.
    expect(topLeft.y + bottomRight.y).toBeCloseTo(600, 6)
  })
})

describe('snapping', () => {
  const walls = [wall(0, 0, 5000, 0), wall(5000, 0, 5000, 4000)]

  it('prefers an existing endpoint', () => {
    const r = snapPoint({ x: 4900, y: 120 }, walls, { radius: 250, grid: 50, orthogonal: true })
    expect(r.kind).toBe('endpoint')
    expect(r.point).toEqual({ x: 5000, y: 0 })
  })

  it('falls back to a point on the wall body', () => {
    const r = snapPoint({ x: 2000, y: 90 }, walls, { radius: 250, grid: 50, orthogonal: true })
    expect(r.kind).toBe('wall')
    expect(r.point).toEqual({ x: 2000, y: 0 })
  })

  it('falls back to the grid when nothing is near', () => {
    const r = snapPoint({ x: 1237, y: 2984 }, walls, { radius: 250, grid: 50, orthogonal: true })
    expect(r.kind).toBe('grid')
    expect(r.point).toEqual({ x: 1250, y: 3000 })
  })

  it('ignores the wall being dragged', () => {
    const r = snapPoint({ x: 4990, y: 5 }, walls, { radius: 250, grid: 0, orthogonal: true }, walls[1].id)
    expect(r.wallId).toBe(walls[0].id)
  })

  it('constrains to the dominant axis', () => {
    expect(constrainOrthogonal({ x: 0, y: 0 }, { x: 1000, y: 80 })).toEqual({ x: 1000, y: 0 })
    expect(constrainOrthogonal({ x: 0, y: 0 }, { x: 80, y: 1000 })).toEqual({ x: 0, y: 1000 })
  })

  it('quantises to the grid', () => {
    expect(snapToGrid({ x: 124, y: -126 }, 50)).toEqual({ x: 100, y: -150 })
    expect(snapToGrid({ x: 124, y: -126 }, 0)).toEqual({ x: 124, y: -126 })
  })
})


describe('snapping while held straight', () => {
  const settings = { radius: 250, grid: 0, orthogonal: true }

  it('runs a wall straight onto the wall it meets', () => {
    // Drawing rightwards from (0,0); a vertical wall crosses at x = 5000.
    const crossing = [wall(5000, -1000, 5000, 1000)]
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 4900, y: 0 }, crossing, settings)
    expect(result.kind).toBe('wall')
    expect(result.point).toEqual({ x: 5000, y: 0 })
  })

  it('stays on the axis rather than being pulled off it', () => {
    // An endpoint 800 mm off the line is far outside the radius, so the free
    // snap would ignore it too — but this must also not drift off the axis.
    const off = [wall(4950, 800, 6000, 800)]
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 4900, y: 0 }, off, settings)
    expect(result.point.y).toBe(0)
  })

  it('never lets a nearby endpoint drag the wall off the axis', () => {
    // 100 mm off the line and well inside the snap radius: exactly the case
    // where gluing to it would break the constraint. It should line up with
    // the endpoint along the wall, but stay on the line.
    const nearlyInline = [wall(5000, 100, 5000, 3000)]
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 4930, y: 0 }, nearlyInline, settings)
    expect(result.point).toEqual({ x: 5000, y: 0 })
  })

  it('holds the vertical axis against a nearby endpoint too', () => {
    const nearlyInline = [wall(120, 3000, 3000, 3000)]
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 0, y: 2940 }, nearlyInline, settings)
    expect(result.point).toEqual({ x: 0, y: 3000 })
  })

  it('still lands exactly on an endpoint that is on the line', () => {
    const inline = [wall(5000, 0, 5000, 3000)]
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 4930, y: 0 }, inline, settings)
    expect(result.kind).toBe('endpoint')
    expect(result.point).toEqual({ x: 5000, y: 0 })
  })

  it('prefers an exact endpoint that already sits on the line', () => {
    const walls = [wall(5000, 0, 5000, 2000), wall(5000, -2000, 5000, 0)]
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 4950, y: 0 }, walls, settings)
    expect(result.kind).toBe('endpoint')
    expect(result.point).toEqual({ x: 5000, y: 0 })
  })

  it('ignores a crossing that is beyond the wall it would cross', () => {
    // The vertical wall stops well above the line, so there is nothing to meet.
    const above = [wall(5000, -3000, 5000, -2000)]
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 4900, y: 0 }, above, settings)
    expect(result.kind).not.toBe('wall')
    expect(result.point.y).toBe(0)
  })

  it('holds the vertical axis when the drag is mostly vertical', () => {
    const crossing = [wall(-1000, 3000, 1000, 3000)]
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 0, y: 2900 }, crossing, settings)
    expect(result.point).toEqual({ x: 0, y: 3000 })
  })

  it('falls back to the grid, still on the axis', () => {
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 4917, y: 0 }, [], {
      radius: 250,
      grid: 50,
      orthogonal: true,
    })
    expect(result.kind).toBe('grid')
    expect(result.point).toEqual({ x: 4900, y: 0 })
  })

  it('excludes the wall being dragged', () => {
    const self = wall(5000, -1000, 5000, 1000)
    const result = snapAlongAxis({ x: 0, y: 0 }, { x: 4900, y: 0 }, [self], settings, self.id)
    expect(result.kind).not.toBe('wall')
  })
})

describe('axisCrossing', () => {
  it('finds where a horizontal line crosses a wall', () => {
    expect(axisCrossing({ x: 0, y: 500 }, true, wall(2000, 0, 2000, 1000))).toEqual({
      x: 2000,
      y: 500,
    })
  })

  it('returns null for a wall parallel to the line', () => {
    expect(axisCrossing({ x: 0, y: 500 }, true, wall(0, 500, 1000, 500))).toBeNull()
  })

  it('returns null when the line misses the wall span', () => {
    expect(axisCrossing({ x: 0, y: 5000 }, true, wall(2000, 0, 2000, 1000))).toBeNull()
  })

  it('handles a vertical constraint line', () => {
    expect(axisCrossing({ x: 300, y: 0 }, false, wall(0, 900, 1000, 900))).toEqual({
      x: 300,
      y: 900,
    })
  })
})


describe('thickness of a newly drawn wall', () => {
  const of = (...thicknesses: number[]) =>
    thicknesses.map((t, i) => wall(0, i * 500, 1000, i * 500, t))

  it('matches the walls already in the plan, not a constant', () => {
    // Detected thickness is measured off the drawing, so it differs per plan.
    expect(typicalWallThickness(of(107.5, 98.6, 107.5, 110.2))).toBeCloseTo(107.5, 4)
    expect(typicalWallThickness(of(116.3, 103.3, 116.3))).toBeCloseTo(116.3, 4)
  })

  it('is not dragged up by the external walls', () => {
    // A new wall is nearly always an internal partition; the median lands
    // there, whereas an average would sit between internal and external.
    const mixed = of(100, 105, 110, 108, 440, 380)
    expect(typicalWallThickness(mixed)).toBeLessThan(150)
  })

  it('follows a scale calibration, which is where a constant drifts worst', () => {
    const before = of(100, 110, 120)
    const after = of(150, 165, 180) // the same plan, rescaled by 1.5
    expect(typicalWallThickness(before)).toBeCloseTo(110, 4)
    expect(typicalWallThickness(after)).toBeCloseTo(165, 4)
  })

  it('falls back to the default on an empty plan', () => {
    expect(typicalWallThickness([])).toBe(DEFAULT_WALL_THICKNESS)
  })

  it('ignores degenerate thicknesses', () => {
    expect(typicalWallThickness(of(0, 0, 200))).toBe(200)
  })
})

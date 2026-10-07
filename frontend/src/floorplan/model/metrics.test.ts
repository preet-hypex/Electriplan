import { describe, expect, it } from 'vitest'
import { emptyFloorPlan, type FloorPlan, type Room } from './types'
import { planChecks, polygonPerimeterM, roomAreaM2, roomSchedule } from './metrics'

const rect = (x: number, y: number, w: number, h: number) => [
  { x, y },
  { x: x + w, y },
  { x: x + w, y: y + h },
  { x, y: y + h },
]

const room = (id: string, name: string, w: number, h: number, confidence?: number): Room => ({
  id,
  name,
  polygon: rect(0, 0, w, h),
  labelPosition: { x: w / 2, y: h / 2 },
  confidence,
})

const plan = (patch: Partial<FloorPlan>): FloorPlan => ({ ...emptyFloorPlan(), ...patch })

describe('room figures', () => {
  it('measures area in m² and perimeter in m', () => {
    const r = room('r1', 'BED 1', 4000, 3000)
    expect(roomAreaM2(r)).toBeCloseTo(12)
    expect(polygonPerimeterM(r.polygon)).toBeCloseTo(14)
  })

  it('schedules rooms largest first, with totals', () => {
    const s = roomSchedule(plan({ rooms: [room('r1', 'BED', 3000, 3000), room('r2', ' LIVING ', 5000, 4000)] }))
    expect(s.rows.map((r) => r.name)).toEqual(['LIVING', 'BED'])
    expect(s.totalAreaM2).toBeCloseTo(29)
    expect(s.totalPerimeterM).toBeCloseTo(30)
  })
})

describe('plan checks', () => {
  it('has nothing to say about a clean plan', () => {
    expect(planChecks(plan({ rooms: [room('r1', 'BED', 3000, 3000, 0.95)] }))).toEqual([])
  })

  it('flags unnamed rooms and low-confidence objects, pointing at them', () => {
    const checks = planChecks(
      plan({
        rooms: [room('r1', '  ', 3000, 3000, 0.95)],
        walls: [{ id: 'w1', start: { x: 0, y: 0 }, end: { x: 1000, y: 0 }, thickness: 90, confidence: 0.4 }],
      }),
    )
    expect(checks.map((c) => c.target)).toEqual([
      { kind: 'room', id: 'r1' },
      { kind: 'wall', id: 'w1' },
    ])
    expect(checks.every((c) => c.severity === 'warning')).toBe(true)
  })

  it('treats manually drawn objects (no confidence) as trusted', () => {
    const checks = planChecks(
      plan({ walls: [{ id: 'w1', start: { x: 0, y: 0 }, end: { x: 1000, y: 0 }, thickness: 90 }] }),
    )
    expect(checks).toEqual([])
  })

  it('warns about a guessed scale before anything else, and lists unclassified openings as info', () => {
    const checks = planChecks(
      plan({
        openings: [{ id: 'o1', wallId: 'w1', position: 500, width: 900 }],
        source: {
          imageUrl: '/x.png',
          imageWidth: 100,
          imageHeight: 100,
          planRegion: { x: 0, y: 0, width: 100, height: 100 },
          mmPerPx: 10,
          scaleConfidence: 0.3,
          scaleMethod: 'fallback',
        },
      }),
    )
    expect(checks.map((c) => [c.key, c.severity])).toEqual([
      ['scale', 'warning'],
      ['gap-o1', 'info'],
    ])
  })
})

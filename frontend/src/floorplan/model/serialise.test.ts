import { describe, expect, it } from 'vitest'
import {
  FloorPlanParseError,
  parseFloorPlan,
  parseFloorPlanJson,
  serialiseFloorPlan,
} from './serialise'
import { sampleFloorPlan } from './sample'
import { FLOORPLAN_VERSION, emptyFloorPlan } from './types'

describe('FloorPlan serialisation', () => {
  it('round-trips the sample plan without losing anything', () => {
    const original = sampleFloorPlan()
    const restored = parseFloorPlanJson(serialiseFloorPlan(original))
    expect(restored.walls).toEqual(original.walls)
    expect(restored.rooms).toEqual(original.rooms)
    expect(restored.labels).toEqual(original.labels)
    expect(restored.version).toBe(FLOORPLAN_VERSION)
    expect(restored.units).toBe('mm')
  })

  it('round-trips a plan carrying a source image block', () => {
    const plan = {
      ...emptyFloorPlan(),
      source: {
        imageUrl: '/api/floorplan/images/abc.jpg',
        imageWidth: 2768,
        imageHeight: 1680,
        planRegion: { x: 409, y: 136, width: 1950, height: 1409 },
        mmPerPx: 12.5,
        scaleConfidence: 0.82,
        scaleMethod: 'ocr-dimensions' as const,
      },
    }
    const restored = parseFloorPlanJson(serialiseFloorPlan(plan))
    expect(restored.source).toEqual(plan.source)
  })

  it('round-trips every door style the backend can emit', () => {
    const plan = {
      ...emptyFloorPlan(),
      walls: [
        { id: 'w1', start: { x: 0, y: 0 }, end: { x: 6000, y: 0 }, thickness: 110 },
      ],
      doors: (['swing', 'sliding', 'garage'] as const).map((style, i) => ({
        id: `d${i}`,
        wallId: 'w1',
        position: 1000 * (i + 1),
        width: 870,
        style,
      })),
    }
    const restored = parseFloorPlanJson(serialiseFloorPlan(plan))
    expect(restored.doors.map((d) => d.style)).toEqual(['swing', 'sliding', 'garage'])
  })

  it('drops a door style it does not know rather than failing the whole plan', () => {
    const restored = parseFloorPlan({
      version: 1,
      units: 'mm',
      walls: [{ id: 'w1', start: { x: 0, y: 0 }, end: { x: 6000, y: 0 }, thickness: 110 }],
      doors: [{ id: 'd1', wallId: 'w1', position: 1000, width: 870, style: 'revolving' }],
    })
    // It falls back to a plain swinging door, which is the safe thing to draw.
    expect(restored.doors[0].style).toBe('swing')
  })

  it('fills in missing optional collections', () => {
    const restored = parseFloorPlan({ version: 1, units: 'mm', walls: [] })
    expect(restored.rooms).toEqual([])
    expect(restored.doors).toEqual([])
    expect(restored.dimensions).toEqual([])
  })

  it('drops unknown keys rather than carrying them through', () => {
    const restored = parseFloorPlan({
      version: 1,
      units: 'mm',
      walls: [{ id: 'w1', start: { x: 0, y: 0 }, end: { x: 1, y: 0 }, thickness: 100, bogus: 42 }],
    })
    expect(restored.walls[0]).not.toHaveProperty('bogus')
  })

  it('treats null optionals the same as absent ones', () => {
    // JSON has no undefined, so a backend that does not strip empty fields
    // sends null. That must not be a parse error.
    const restored = parseFloorPlan({
      rooms: [
        {
          id: 'room_001',
          name: 'LIVING',
          polygon: [{ x: 0, y: 0 }, { x: 1000, y: 0 }, { x: 0, y: 1000 }],
          labelPosition: { x: 0, y: 0 },
          colour: null,
          confidence: null,
          source: null,
        },
      ],
      labels: [{ id: 'l1', text: 'x', position: { x: 0, y: 0 }, type: 'room', roomId: null }],
    })
    expect(restored.rooms[0].colour).toBeUndefined()
    expect(restored.rooms[0].confidence).toBeUndefined()
    expect(restored.rooms[0].source).toBeUndefined()
    expect(restored.labels[0].roomId).toBeUndefined()
  })

  it('ignores a source value it does not recognise', () => {
    const restored = parseFloorPlan({
      walls: [
        { id: 'w', start: { x: 0, y: 0 }, end: { x: 1, y: 0 }, thickness: 1, source: 'telepathy' },
      ],
    })
    expect(restored.walls[0].source).toBeUndefined()
  })

  it('defaults an unrecognised label type to "other"', () => {
    const restored = parseFloorPlan({
      labels: [{ id: 'l1', text: 'x', position: { x: 0, y: 0 }, type: 'nonsense' }],
    })
    expect(restored.labels[0].type).toBe('other')
  })

  it('names the offending field when geometry is malformed', () => {
    expect(() =>
      parseFloorPlan({ walls: [{ id: 'w1', start: { x: 0 }, end: { x: 1, y: 0 }, thickness: 1 }] }),
    ).toThrow(/walls\[0\]\.start\.y/)

    expect(() => parseFloorPlan({ walls: [{ start: { x: 0, y: 0 }, end: { x: 1, y: 0 }, thickness: 1 }] }))
      .toThrow(/walls\[0\]\.id/)
  })

  it('rejects non-millimetre units and future versions', () => {
    expect(() => parseFloorPlan({ units: 'ft' })).toThrow(FloorPlanParseError)
    expect(() => parseFloorPlan({ version: FLOORPLAN_VERSION + 1 })).toThrow(/version/)
  })

  it('rejects non-JSON input with a readable message', () => {
    expect(() => parseFloorPlanJson('{not json')).toThrow(/Not valid JSON/)
    expect(() => parseFloorPlan([1, 2, 3])).toThrow(/expected a JSON object/)
  })

  it('rejects NaN and Infinity coordinates', () => {
    expect(() =>
      parseFloorPlan({
        walls: [{ id: 'w', start: { x: 0, y: 0 }, end: { x: 0, y: 0 }, thickness: Infinity }],
      }),
    ).toThrow(/thickness/)
  })
})

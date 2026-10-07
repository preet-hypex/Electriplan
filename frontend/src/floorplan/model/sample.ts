import type { FloorPlan, Point, Wall } from './types'
import { FLOORPLAN_VERSION } from './types'

/**
 * A hand-authored plan used to develop and demo the editor without a backend.
 *
 * This is a made-up 8.4 m × 6.2 m two-bedroom unit — deliberately NOT the test
 * floor plan. Nothing here is a stand-in for image analysis; the analyse
 * endpoint produces its own geometry from the uploaded pixels.
 */

const T = 110 // internal wall thickness, mm
const EXT = 200 // external wall thickness, mm

let n = 0
function wall(x1: number, y1: number, x2: number, y2: number, thickness = T): Wall {
  n += 1
  return {
    id: `wall_${String(n).padStart(3, '0')}`,
    start: { x: x1, y: y1 },
    end: { x: x2, y: y2 },
    thickness,
    confidence: 0.9,
    source: 'vision',
  }
}

const W = 8400
const H = 6200
const HALL_Y = 3600
const BED_SPLIT = 3400

const walls: Wall[] = [
  // Shell.
  wall(0, 0, W, 0, EXT),
  wall(W, 0, W, H, EXT),
  wall(W, H, 0, H, EXT),
  wall(0, H, 0, 0, EXT),
  // Corridor running the width of the unit.
  wall(0, HALL_Y, BED_SPLIT, HALL_Y),
  wall(BED_SPLIT, HALL_Y, W, HALL_Y),
  // Bedroom divider above the corridor.
  wall(BED_SPLIT, 0, BED_SPLIT, HALL_Y),
  // Bathroom below.
  wall(2400, HALL_Y, 2400, H),
  wall(5200, HALL_Y, 5200, H),
]

const rect = (x1: number, y1: number, x2: number, y2: number): Point[] => [
  { x: x1, y: y1 },
  { x: x2, y: y1 },
  { x: x2, y: y2 },
  { x: x1, y: y2 },
]

const rooms = [
  { id: 'room_001', name: 'BED 1', poly: rect(100, 100, 3345, 3545), conf: 0.92 },
  { id: 'room_002', name: 'BED 2', poly: rect(3455, 100, 8300, 3545), conf: 0.88 },
  { id: 'room_003', name: 'LIVING', poly: rect(100, 3655, 2345, 6100), conf: 0.95 },
  { id: 'room_004', name: 'BATH', poly: rect(2455, 3655, 5145, 6100), conf: 0.71 },
  { id: 'room_005', name: 'KITCHEN', poly: rect(5255, 3655, 8300, 6100), conf: 0.55 },
].map((r) => {
  const cx = (r.poly[0].x + r.poly[2].x) / 2
  const cy = (r.poly[0].y + r.poly[2].y) / 2
  return {
    id: r.id,
    name: r.name,
    polygon: r.poly,
    labelPosition: { x: cx, y: cy },
    confidence: r.conf,
    source: 'ocr' as const,
  }
})

export function sampleFloorPlan(): FloorPlan {
  return structuredClone({
    version: FLOORPLAN_VERSION,
    units: 'mm',
    walls,
    rooms,
    doors: [],
    windows: [],
    openings: [],
    labels: rooms.map((r, i) => ({
      id: `label_${String(i + 1).padStart(3, '0')}`,
      text: r.name,
      position: r.labelPosition,
      type: 'room' as const,
      roomId: r.id,
      confidence: r.confidence,
      source: 'ocr' as const,
    })),
    dimensions: [],
    analysis: {
      steps: [{ name: 'Sample plan loaded', ok: true, detail: 'no image analysed' }],
      wallCount: walls.length,
      roomCount: rooms.length,
      labelCount: rooms.length,
      dimensionCount: 0,
      warnings: [],
    },
  } satisfies FloorPlan)
}

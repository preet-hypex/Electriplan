import {
  FLOORPLAN_VERSION,
  emptyFloorPlan,
  type DoorSwing,
  type FloorPlan,
  type Point,
  type Room,
} from './types'

export class FloorPlanParseError extends Error {}

const isObject = (v: unknown): v is Record<string, unknown> =>
  typeof v === 'object' && v !== null && !Array.isArray(v)

function num(v: unknown, where: string): number {
  if (typeof v !== 'number' || !Number.isFinite(v)) {
    throw new FloorPlanParseError(`${where}: expected a finite number, got ${JSON.stringify(v)}`)
  }
  return v
}

function str(v: unknown, where: string): string {
  if (typeof v !== 'string') {
    throw new FloorPlanParseError(`${where}: expected a string, got ${JSON.stringify(v)}`)
  }
  return v
}

function point(v: unknown, where: string): Point {
  if (!isObject(v)) throw new FloorPlanParseError(`${where}: expected {x, y}`)
  return { x: num(v.x, `${where}.x`), y: num(v.y, `${where}.y`) }
}

function optionalNum(v: unknown, where: string): number | undefined {
  return v === undefined || v === null ? undefined : num(v, where)
}

function optionalStr(v: unknown, where: string): string | undefined {
  return v === undefined || v === null ? undefined : str(v, where)
}

/** JSON has no `undefined`, so an absent optional arrives as `null`. */
function optionalEnum<T extends string>(v: unknown, allowed: readonly T[]): T | undefined {
  return typeof v === 'string' && (allowed as readonly string[]).includes(v) ? (v as T) : undefined
}

const SOURCES = ['vision', 'ocr', 'geometry', 'manual'] as const
const DOOR_STYLES = ['swing', 'sliding', 'garage'] as const

/** Which side a door opens to: 90 or -90, nothing in between. */
function doorSwing(v: unknown, where: string): DoorSwing | undefined {
  const n = optionalNum(v, where)
  if (n === undefined || n === 90 || n === -90) return n
  throw new FloorPlanParseError(`${where}: expected 90 or -90, got ${n}`)
}

function hasThreeCorners(polygon: Point[]): polygon is Room['polygon'] {
  return polygon.length >= 3
}

function array(v: unknown, where: string): unknown[] {
  if (v === undefined || v === null) return []
  if (!Array.isArray(v)) throw new FloorPlanParseError(`${where}: expected an array`)
  return v
}

/**
 * Parse an unknown value into a FloorPlan, rejecting anything malformed with a
 * message that names the offending field. Unknown extra keys are dropped, so a
 * newer file opened in an older build degrades rather than corrupting.
 */
export function parseFloorPlan(input: unknown): FloorPlan {
  if (!isObject(input)) throw new FloorPlanParseError('Root: expected a JSON object')

  // A file without a version is taken to be the current one; any version other
  // than the one in contracts/floor-plan.schema.json is refused.
  if (input.version !== undefined && input.version !== FLOORPLAN_VERSION) {
    throw new FloorPlanParseError(
      `This file is version ${JSON.stringify(input.version)}; this build understands version ${FLOORPLAN_VERSION}.`,
    )
  }
  if (input.units !== undefined && input.units !== 'mm') {
    throw new FloorPlanParseError(`units: only "mm" is supported, got ${JSON.stringify(input.units)}`)
  }

  const plan: FloorPlan = emptyFloorPlan()

  plan.walls = array(input.walls, 'walls').map((raw, i) => {
    const w = isObject(raw) ? raw : {}
    return {
      id: str(w.id, `walls[${i}].id`),
      start: point(w.start, `walls[${i}].start`),
      end: point(w.end, `walls[${i}].end`),
      thickness: num(w.thickness, `walls[${i}].thickness`),
      confidence: optionalNum(w.confidence, `walls[${i}].confidence`),
      source: optionalEnum(w.source, SOURCES),
    }
  })

  plan.rooms = array(input.rooms, 'rooms').map((raw, i) => {
    const r = isObject(raw) ? raw : {}
    const polygon = array(r.polygon, `rooms[${i}].polygon`).map((p, j) =>
      point(p, `rooms[${i}].polygon[${j}]`),
    )
    if (!hasThreeCorners(polygon)) {
      throw new FloorPlanParseError(
        `rooms[${i}].polygon: a room needs at least 3 corners, got ${polygon.length}`,
      )
    }
    return {
      id: str(r.id, `rooms[${i}].id`),
      name: str(r.name ?? '', `rooms[${i}].name`),
      polygon,
      labelPosition: r.labelPosition
        ? point(r.labelPosition, `rooms[${i}].labelPosition`)
        : (polygon[0] ?? { x: 0, y: 0 }),
      colour: optionalStr(r.colour, `rooms[${i}].colour`),
      confidence: optionalNum(r.confidence, `rooms[${i}].confidence`),
      source: optionalEnum(r.source, SOURCES),
    }
  })

  plan.doors = array(input.doors, 'doors').map((raw, i) => {
    const d = isObject(raw) ? raw : {}
    return {
      id: str(d.id, `doors[${i}].id`),
      wallId: str(d.wallId, `doors[${i}].wallId`),
      position: num(d.position, `doors[${i}].position`),
      width: num(d.width, `doors[${i}].width`),
      style: optionalEnum(d.style, DOOR_STYLES) ?? 'swing',
      hingeAtStart: d.hingeAtStart === undefined || d.hingeAtStart === null
        ? undefined
        : Boolean(d.hingeAtStart),
      swing: doorSwing(d.swing, `doors[${i}].swing`),
      confidence: optionalNum(d.confidence, `doors[${i}].confidence`),
      source: optionalEnum(d.source, SOURCES),
    }
  })

  plan.windows = array(input.windows, 'windows').map((raw, i) => {
    const w = isObject(raw) ? raw : {}
    return {
      id: str(w.id, `windows[${i}].id`),
      wallId: str(w.wallId, `windows[${i}].wallId`),
      position: num(w.position, `windows[${i}].position`),
      width: num(w.width, `windows[${i}].width`),
      confidence: optionalNum(w.confidence, `windows[${i}].confidence`),
      source: optionalEnum(w.source, SOURCES),
    }
  })

  plan.openings = array(input.openings, 'openings').map((raw, i) => {
    const o = isObject(raw) ? raw : {}
    return {
      id: str(o.id, `openings[${i}].id`),
      wallId: str(o.wallId, `openings[${i}].wallId`),
      position: num(o.position, `openings[${i}].position`),
      width: num(o.width, `openings[${i}].width`),
      confidence: optionalNum(o.confidence, `openings[${i}].confidence`),
      source: optionalEnum(o.source, SOURCES),
    }
  })

  plan.labels = array(input.labels, 'labels').map((raw, i) => {
    const l = isObject(raw) ? raw : {}
    const type = l.type === 'room' || l.type === 'dimension' ? l.type : 'other'
    return {
      id: str(l.id, `labels[${i}].id`),
      text: str(l.text ?? '', `labels[${i}].text`),
      position: point(l.position, `labels[${i}].position`),
      type,
      roomId: optionalStr(l.roomId, `labels[${i}].roomId`),
      confidence: optionalNum(l.confidence, `labels[${i}].confidence`),
      source: optionalEnum(l.source, SOURCES),
    }
  })

  plan.dimensions = array(input.dimensions, 'dimensions').map((raw, i) => {
    const d = isObject(raw) ? raw : {}
    return {
      id: str(d.id, `dimensions[${i}].id`),
      start: point(d.start, `dimensions[${i}].start`),
      end: point(d.end, `dimensions[${i}].end`),
      value: num(d.value, `dimensions[${i}].value`),
      unit: d.unit === 'm' ? 'm' : 'mm',
      confidence: optionalNum(d.confidence, `dimensions[${i}].confidence`),
      source: optionalEnum(d.source, SOURCES),
    }
  })

  if (isObject(input.source)) {
    const s = input.source
    const region = isObject(s.planRegion) ? s.planRegion : {}
    plan.source = {
      imageUrl: str(s.imageUrl, 'source.imageUrl'),
      imageWidth: num(s.imageWidth, 'source.imageWidth'),
      imageHeight: num(s.imageHeight, 'source.imageHeight'),
      planRegion: {
        x: num(region.x, 'source.planRegion.x'),
        y: num(region.y, 'source.planRegion.y'),
        width: num(region.width, 'source.planRegion.width'),
        height: num(region.height, 'source.planRegion.height'),
      },
      mmPerPx: num(s.mmPerPx, 'source.mmPerPx'),
      scaleConfidence: num(s.scaleConfidence ?? 0, 'source.scaleConfidence'),
      scaleMethod:
        optionalEnum(s.scaleMethod, ['ocr-dimensions', 'wall-thickness', 'manual'] as const) ??
        'fallback',
    }
  }

  if (isObject(input.analysis)) {
    plan.analysis = input.analysis as unknown as FloorPlan['analysis']
  }

  return plan
}

export function serialiseFloorPlan(plan: FloorPlan): string {
  return JSON.stringify(plan, null, 2)
}

export function parseFloorPlanJson(text: string): FloorPlan {
  let raw: unknown
  try {
    raw = JSON.parse(text)
  } catch (e) {
    throw new FloorPlanParseError(`Not valid JSON: ${(e as Error).message}`)
  }
  return parseFloorPlan(raw)
}

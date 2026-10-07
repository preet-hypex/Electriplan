import type { Door, FloorPlan, Label, Point, Room, Wall, Window } from './types'

/**
 * Ids are only unique within a document, which is all the editor needs.
 * A monotonic counter seeded from the loaded plan avoids colliding with ids
 * the backend produced (wall_001, room_03, ...).
 */
let counter = 0

export function seedIdCounter(plan: FloorPlan): void {
  const ids = [
    ...plan.walls.map((w) => w.id),
    ...plan.rooms.map((r) => r.id),
    ...plan.labels.map((l) => l.id),
    ...plan.doors.map((d) => d.id),
    ...plan.windows.map((w) => w.id),
    ...plan.dimensions.map((d) => d.id),
  ]
  let max = 0
  for (const id of ids) {
    const m = /(\d+)$/.exec(id)
    if (m) max = Math.max(max, Number(m[1]))
  }
  counter = Math.max(counter, max)
}

function nextId(prefix: string): string {
  counter += 1
  return `${prefix}_${String(counter).padStart(3, '0')}`
}

/** Used only when there is nothing in the plan to measure against. */
export const DEFAULT_WALL_THICKNESS = 110

/**
 * The thickness a new wall should be drawn at: whatever the walls already in
 * the plan are.
 *
 * A constant is wrong here. Detected thickness is measured off the drawing and
 * varies plan to plan, and a scale calibration rescales every existing wall
 * while leaving a constant behind — so a hard-coded default drifts further out
 * the more the plan is corrected.
 *
 * The median rather than the mean: external walls are much thicker than
 * internal ones and would drag an average up, whereas a new wall is nearly
 * always an internal partition, which is what the median lands on.
 */
export function typicalWallThickness(walls: Wall[]): number {
  const measured = walls.map((w) => w.thickness).filter((t) => t > 0).sort((a, b) => a - b)
  if (measured.length === 0) return DEFAULT_WALL_THICKNESS
  const mid = measured.length >> 1
  return measured.length % 2 ? measured[mid] : (measured[mid - 1] + measured[mid]) / 2
}

export function createWall(start: Point, end: Point, thickness = DEFAULT_WALL_THICKNESS): Wall {
  return {
    id: nextId('wall'),
    start: { ...start },
    end: { ...end },
    thickness,
    source: 'manual',
  }
}

export function createRoom(name: string, polygon: Point[], labelPosition: Point): Room {
  return {
    id: nextId('room'),
    name,
    polygon: polygon.map((p) => ({ ...p })),
    labelPosition: { ...labelPosition },
    source: 'manual',
  }
}

export function createLabel(text: string, position: Point, type: Label['type'] = 'other'): Label {
  return {
    id: nextId('label'),
    text,
    position: { ...position },
    type,
    source: 'manual',
  }
}

export const DEFAULT_DOOR_WIDTH = 870
export const DEFAULT_WINDOW_WIDTH = 1200

export function createDoor(wallId: string, position: number, width = DEFAULT_DOOR_WIDTH): Door {
  return {
    id: nextId('door'),
    wallId,
    position,
    width,
    style: 'swing',
    hingeAtStart: true,
    swing: 90,
    source: 'manual',
  }
}

export function createWindow(
  wallId: string,
  position: number,
  width = DEFAULT_WINDOW_WIDTH,
): Window {
  return { id: nextId('window'), wallId, position, width, source: 'manual' }
}

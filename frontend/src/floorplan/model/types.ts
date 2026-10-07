/**
 * The FloorPlan model.
 *
 * This is the editable truth. The uploaded image is only a reference layer —
 * nothing in this file depends on it, and the editor never writes back to it.
 *
 * All coordinates and lengths are millimetres, in a plan-local space whose
 * origin is the top-left corner of the detected floor-plan region. Y grows
 * downwards, matching SVG.
 */

export interface Point {
  x: number
  y: number
}

/** How an object came to exist. Drives the confidence badges in the editor. */
export type Source = 'vision' | 'ocr' | 'geometry' | 'manual'

/** Fields every automatically detected object may carry. */
export interface Detected {
  confidence?: number
  source?: Source
}

export interface Wall extends Detected {
  id: string
  start: Point
  end: Point
  /** Wall thickness in mm, measured perpendicular to the centre line. */
  thickness: number
}

export interface Room extends Detected {
  id: string
  name: string
  polygon: Point[]
  labelPosition: Point
  /** Optional fill override. Undefined means the default room fill. */
  colour?: string
}

export interface Door extends Detected {
  id: string
  wallId: string
  /** Distance in mm from the wall's start point to the door's centre. */
  position: number
  width: number
  /**
   * How the door opens. A slider has no hinge and no swing, so the two fields
   * below are ignored for one.
   */
  style?: 'swing' | 'sliding' | 'garage'
  /** Which jamb the hinge is on, in the wall's own start-to-end direction. */
  hingeAtStart?: boolean
  /** Which side the leaf opens to: +90 is the wall's left-hand normal. */
  swing?: number
}

export interface Window extends Detected {
  id: string
  wallId: string
  position: number
  width: number
}

/**
 * A gap in a wall that is neither clearly a door nor clearly a window — a
 * cased opening, or one whose symbol could not be read. Kept so the editor can
 * show it and let the user say what it is.
 */
export interface Opening extends Detected {
  id: string
  wallId: string
  position: number
  width: number
}

export type LabelKind = 'room' | 'dimension' | 'other'

export interface Label extends Detected {
  id: string
  text: string
  position: Point
  type: LabelKind
  /** Set when the label was matched to a room during analysis. */
  roomId?: string
}

export interface Dimension extends Detected {
  id: string
  start: Point
  end: Point
  value: number
  unit: 'mm' | 'm'
}

/**
 * Where the plan came from. Optional: a hand-built plan has no source image.
 * The geometry above is complete without it.
 */
export interface PlanSource {
  /** URL the frontend can load the untouched upload from. */
  imageUrl: string
  imageWidth: number
  imageHeight: number
  /** The detected drawing region, in pixels of the original image. */
  planRegion: { x: number; y: number; width: number; height: number }
  /** Conversion actually applied to produce the mm coordinates above. */
  mmPerPx: number
  /** How the scale was arrived at. */
  scaleConfidence: number
  scaleMethod: 'ocr-dimensions' | 'wall-thickness' | 'manual' | 'fallback'
}

/** Counts and diagnostics the analysis screen reports. Never load-bearing. */
export interface AnalysisReport {
  steps: Array<{ name: string; ok: boolean; detail: string }>
  wallCount: number
  roomCount: number
  labelCount: number
  dimensionCount: number
  warnings: string[]
}

export interface FloorPlan {
  version: number
  units: 'mm'

  walls: Wall[]
  rooms: Room[]
  doors: Door[]
  windows: Window[]
  openings: Opening[]
  labels: Label[]
  dimensions: Dimension[]

  source?: PlanSource
  analysis?: AnalysisReport
}

export const FLOORPLAN_VERSION = 1

export function emptyFloorPlan(): FloorPlan {
  return {
    version: FLOORPLAN_VERSION,
    units: 'mm',
    walls: [],
    rooms: [],
    doors: [],
    windows: [],
    openings: [],
    labels: [],
    dimensions: [],
  }
}

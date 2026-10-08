/**
 * The FloorPlan model.
 *
 * This is the editable truth. The uploaded image is only a reference layer —
 * nothing in this file depends on it, and the editor never writes back to it.
 *
 * All coordinates and lengths are millimetres, in a plan-local space whose
 * origin is the top-left corner of the detected floor-plan region. Y grows
 * downwards, matching SVG.
 *
 * The types themselves are generated from contracts/floor-plan.schema.json
 * (into src/electrical/contracts.ts, with the other contracts), so the editor,
 * the analyser and the electrical engine share one definition. Change the
 * schema and run `npm run contracts`; never redefine them here.
 */
import type { FloorPlan, PlanItemSource } from '../../electrical/contracts'

export type {
  AnalysisReport,
  AnalysisStep,
  Dimension,
  DimensionUnit,
  Door,
  DoorStyle,
  DoorSwing,
  FloorPlan,
  Label,
  LabelKind,
  Opening,
  PlanRegion,
  PlanSource,
  Point,
  Room,
  ScaleMethod,
  Wall,
  Window,
} from '../../electrical/contracts'

/** How an object came to exist. Drives the confidence badges in the editor. */
export type Source = PlanItemSource

/** Fields every automatically detected object may carry. */
export interface Detected {
  confidence?: number
  source?: Source
}

/** The only FloorPlan version this build reads and writes (the schema's `version`). */
export const FLOORPLAN_VERSION: FloorPlan['version'] = 1

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

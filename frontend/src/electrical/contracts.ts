/* eslint-disable */
/**
 * Generated from contracts/*.schema.json by scripts/contract-types.mjs.
 * Do not edit by hand: change the schema and run `npm run contracts`.
 */

export type AustralianState = 'NSW' | 'VIC' | 'QLD' | 'WA' | 'SA' | 'TAS' | 'ACT' | 'NT'
/**
 * The electricity distributor (DNSP), by code: jemena, citipower... Configurable reference data, not a fixed list: the codes are rows of the database table electriplan.electricity_distributor (GET /api/reference/distributors), and the API checks a brief's code exists there and belongs to the brief's state. Same format as that table's code column.
 */
export type DistributorCode = string
export type CooktopType = 'induction' | 'electric' | 'gas' | 'none'
export type OvenType = 'electric' | 'gas' | 'none'
export type HotWaterType = 'heat-pump' | 'electric-storage' | 'electric-instantaneous' | 'gas' | 'solar-boosted'
export type AirConditioningType = 'ducted' | 'split' | 'none'
/**
 * An identifier, unique within its document: wall_004, lt_001, c_L1.
 */
export type Id = string
export type FixtureKind =
  | 'shower'
  | 'bath'
  | 'basin'
  | 'wc'
  | 'kitchen-sink'
  | 'laundry-tub'
  | 'shower-screen'
  | 'bench-run'
  | 'island-bench'
  | 'cooktop'
  | 'oven'
  | 'rangehood'
  | 'fridge-space'
  | 'dishwasher'
  | 'washing-machine'
  | 'dryer'
  | 'hot-water-unit'
  | 'ac-indoor-unit'
  | 'ac-outdoor-unit'
  | 'meter-box'
  | 'switchboard'
  | 'pool'
/**
 * A length or distance in millimetres, never negative.
 */
export type Millimetres = number
/**
 * Which face of a wall, looking from the wall's start towards its end.
 */
export type Side = 'left' | 'right'
export type PointKind =
  | 'downlight'
  | 'oyster'
  | 'batten'
  | 'pendant'
  | 'wall-light'
  | 'exterior-light'
  | 'exhaust-fan'
  | 'ceiling-fan'
  | 'smoke-alarm'
  | 'switch'
  | 'dimmer'
  | 'gpo-single'
  | 'gpo-double'
  | 'gpo-weatherproof'
  | 'appliance-outlet'
  | 'isolator'
  | 'data'
  | 'tv'
export type Placement = WallPlacement | CeilingPlacement
/**
 * Who placed an item: the engine, or a person (who the engine then designs around and never moves).
 */
export type Source = 'engine' | 'manual'
export type ZoneKind =
  | 'bath-zone-0'
  | 'bath-zone-1'
  | 'bath-zone-2'
  | 'bath-zone-3'
  | 'basin-zone'
  | 'sink-zone'
  | 'tub-zone'
  | 'pool-zone-0'
  | 'pool-zone-1'
  | 'pool-zone-2'
export type CircuitType = 'lighting' | 'power' | 'dedicated' | 'smoke-alarm'
export type DeviceKind = 'RCBO' | 'RCD' | 'MCB'
export type TripCurve = 'B' | 'C' | 'D'
export type Severity = 'error' | 'warning'
/**
 * A length in millimetres that must be greater than zero: a wall's thickness, an opening's width.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "positiveMillimetres".
 */
export type PositiveMillimetres = number
/**
 * How sure the analyser is of a detected item, from 0 to 1. Absent for items a person placed.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "confidence".
 */
export type Confidence = number
/**
 * How an item came to be in the plan: found in the image (vision), read from its text (ocr), inferred from other geometry (geometry), or drawn or corrected by a person (manual). Drives the editor's confidence badges.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "planItemSource".
 */
export type PlanItemSource = 'vision' | 'ocr' | 'geometry' | 'manual'
export type DoorStyle = 'swing' | 'sliding' | 'garage'
export type DoorSwing = 90 | -90
export type LabelKind = 'room' | 'dimension' | 'other'
export type DimensionUnit = 'mm' | 'm'
export type ScaleMethod = 'ocr-dimensions' | 'wall-thickness' | 'manual' | 'fallback'

export interface Contracts {
  projectBrief?: ProjectBrief
  fixture?: Fixture
  electricalDesign?: ElectricalDesign
  floorPlan?: FloorPlan
}
/**
 * What the floor plan cannot say about a house but its electrical design needs: supply, construction, appliances and the builder's preferences. See documents/electrical-engine-plan.md section 4.3. state selects the state rules applied on top of the national ones; which states the engine designs is decided by the rule pack.
 */
export interface ProjectBrief {
  version: 1
  state: AustralianState
  distributor: DistributorCode
  supply: Supply
  construction: Construction
  appliances: Appliances
  preferences?: Preferences
}
export interface Supply {
  /**
   * v1 designs single-phase only.
   */
  phases: 1 | 3
  nominalVoltage: 230
  /**
   * Metres from the point of supply to the switchboard, if known.
   */
  consumerMainsLengthM?: number
}
export interface Construction {
  storeys: number
  defaultCeilingHeight: number
  ceilingInsulated: boolean
  roofSpaceAccessible: boolean
  slab: boolean
}
export interface Appliances {
  cooktop: CooktopType
  oven: OvenType
  hotWater: HotWaterType
  airConditioning: AirConditioningType
  evCharger: boolean
  pool: boolean
}
/**
 * The builder's overrides of design-policy defaults for this house. Never overrides a mandatory rule. Omitted means the organisation's policy applies.
 */
export interface Preferences {
  downlightItemCode?: string
  switchHeight?: number
  gpoHeight?: number
  spareSwitchboardPolesPct?: number
}
/**
 * A fixed item in a house that electrical design depends on: wet fixtures (wet-area zones are measured from them), benches (kitchen outlets follow them), appliances (dedicated circuits), the meter box and switchboard (where every cable starts). Placed on the floor plan; see documents/electrical-engine-plan.md section 4.2. wallAnchor: for fixtures fixed to a wall (basin, bench run, switchboard, rangehood), which wall and where, so the fixture moves with it. waterOutlet: showers and baths, the fixed water outlet that an unenclosed shower's zones are measured from. heightMm: height of the fixture's top above the floor; a shower screen's height decides whether it bounds a zone.
 */
export interface Fixture {
  id: Id
  kind: FixtureKind
  roomId?: Id
  footprint: Footprint
  wallAnchor?: WallAnchor
  waterOutlet?: Point
  heightMm?: Millimetres
  /**
   * Appliances: nameplate rating, where known. Drives dedicated circuits and maximum demand.
   */
  ratingKw?: number
  /**
   * Placed by a person, or detected from the plan image (then to be confirmed).
   */
  source: 'manual' | 'vision'
  confidence?: number
}
/**
 * The fixture's outline seen from above: centre, width along its own x axis, depth, and rotation in degrees clockwise.
 */
export interface Footprint {
  centre: Point
  width: number
  depth: number
  rotationDeg: number
}
/**
 * A position in plan coordinates, millimetres. Origin at the top-left of the drawing area, y downwards (SVG convention); may be negative.
 */
export interface Point {
  x: number
  y: number
}
/**
 * A position on a wall face, so it moves with the wall: the wall, the distance from the wall's start, the face, and the height above the finished floor.
 */
export interface WallAnchor {
  wallId: Id
  position: Millimetres
  side: Side
  height?: Millimetres
}
/**
 * An electrical design for one house plan: every point (light, switch, outlet...), wet-area zones, circuits, the switchboard, maximum demand, and what breaks a rule or needs an electrician's decision. Produced by the engine, edited in the editor, stored versioned in electriplan.electrical_design_version.document. See documents/electrical-engine-plan.md section 5.
 */
export interface ElectricalDesign {
  version: 1
  planRef: PlanRef
  rulePack: RulePackRef
  points: DesignPoint[]
  zones: Zone[]
  circuits: Circuit[]
  switchboard?: Switchboard
  maxDemand?: MaxDemand
  violations: Violation[]
  decisionsRequired: DecisionRequired[]
}
/**
 * The floor plan this design was made from: its content hash and version, so a stale design can be detected.
 */
export interface PlanRef {
  planHash: string
  planVersion: number
}
/**
 * The rules the design was made and checked with: the rule pack release, and the state whose rules were applied on top of the national ones (each state adds its own, e.g. Queensland's smoke alarm in every bedroom). Together they say exactly which rules made the design.
 */
export interface RulePackRef {
  id: string
  version: string
  state: AustralianState
  standards: string[]
}
/**
 * One electrical item, on a wall or on the ceiling.
 *
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "designPoint".
 */
export interface DesignPoint {
  id: Id
  kind: PointKind
  roomId?: Id
  placement: Placement
  spec: PointSpec
  circuitId?: Id
  /**
   * Lights and fans: the switches that control them.
   */
  controlledBy?: Id[]
  /**
   * Switches: for each gang, the points it controls.
   */
  controls?: Id[][]
  /**
   * Why it is here, in the engine's words, citing the rules it applied.
   */
  rationale: string[]
  source: Source
}
/**
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "wallPlacement".
 */
export interface WallPlacement {
  type: 'wall'
  wallId: Id
  position: Millimetres
  side: Side
  height: Millimetres
}
/**
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "ceilingPlacement".
 */
export interface CeilingPlacement {
  type: 'ceiling'
  at: Point
}
/**
 * What the item is. Which properties apply depends on the kind; the catalogue item code ties it to the bill of materials.
 *
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "pointSpec".
 */
export interface PointSpec {
  itemCode?: string
  watts?: number
  lumens?: number
  /**
   * Recessed luminaire insulation-contact rating.
   */
  ic?: 'IC-4' | 'IC' | 'non-IC'
  /**
   * Ingress protection, e.g. IP44.
   */
  ip?: string
  ratingA?: number
  gangs?: number
  ways?: ('1-way' | '2-way' | 'intermediate')[]
}
/**
 * A keep-out volume from a fixture, for drawing and for validation: its outline on plan and its height from the floor.
 *
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "zone".
 */
export interface Zone {
  id: Id
  kind: ZoneKind
  fixtureId: Id
  /**
   * @minItems 3
   */
  polygon: [Point, Point, Point, ...Point[]]
  floorToHeight: Millimetres
  /**
   * The clause the zone comes from.
   */
  rule: string
}
/**
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "circuit".
 */
export interface Circuit {
  id: Id
  type: CircuitType
  label: string
  points: Id[]
  protectionId?: Id
  demandA?: number
  cable?: CableSpec
  voltageDropPct?: number
  zsOhm?: number
  zsMaxOhm?: number
  route?: RouteSegment[]
}
export interface CableSpec {
  /**
   * Conductor cross-section, mm².
   */
  csaMm2: number
  type: string
  lengthM: number
}
export interface RouteSegment {
  from: string
  to: string
  lengthM: number
}
/**
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "switchboard".
 */
export interface Switchboard {
  location: WallAnchor
  mainSwitch: MainSwitch
  devices: ProtectiveDevice[]
  spd?: SurgeProtection
  polesUsed: number
  polesTotal: number
}
export interface MainSwitch {
  ratingA: number
  poles: 1 | 3
}
export interface ProtectiveDevice {
  id: Id
  kind: DeviceKind
  ratingA: number
  curve?: TripCurve
  rcdMa?: number
  circuits: Id[]
}
export interface SurgeProtection {
  fitted: boolean
  reason: string
}
/**
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "maxDemand".
 */
export interface MaxDemand {
  method: string
  /**
   * @minItems 1
   * @maxItems 3
   */
  perPhaseA: [number] | [number, number] | [number, number, number]
  consumerMains?: ConsumerMains
}
export interface ConsumerMains {
  csaMm2: number
  type: string
}
/**
 * A rule the design breaks. An error (a mandatory rule) blocks committing the design.
 *
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "violation".
 */
export interface Violation {
  ruleId: string
  severity: Severity
  itemIds: Id[]
  message: string
}
/**
 * Something the engine could not decide and assumed instead, for the electrician to confirm.
 *
 * This interface was referenced by `ElectricalDesign`'s JSON-Schema
 * via the `definition` "decisionRequired".
 */
export interface DecisionRequired {
  id: Id
  itemIds: Id[]
  question: string
}
/**
 * An editable floor plan: walls, rooms, doors, windows, openings, labels and dimensions, in millimetres. Produced by the floor-plan analyser (floorplan/) from an uploaded image, edited in the editor, and read by the electrical engine, which never changes it. The image is only a reference: the geometry is complete without source. Coordinates are plan-local, origin at the top-left of the detected drawing region, y downwards. See documents/electrical-engine-plan.md sections 4.1 and 6.6.
 */
export interface FloorPlan {
  version: 1
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
/**
 * A wall's centre line from start to end, and its thickness measured perpendicular to that line.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "wall".
 */
export interface Wall {
  id: Id
  start: Point
  end: Point
  thickness: PositiveMillimetres
  confidence?: Confidence
  source?: PlanItemSource
}
/**
 * A room: its name as written on the plan (empty when none could be read), its outline, where its name is drawn, and an optional fill colour overriding the editor's default.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "room".
 */
export interface Room {
  id: Id
  name: string
  /**
   * @minItems 3
   */
  polygon: [Point, Point, Point, ...Point[]]
  labelPosition: Point
  colour?: string
  confidence?: Confidence
  source?: PlanItemSource
}
/**
 * A door in a wall. position: distance from the wall's start to the door's centre. style: how it opens (swing when absent); a sliding or garage door has no hinge or swing, so hingeAtStart and swing are ignored for one. hingeAtStart: which jamb the hinge is on, in the wall's start-to-end direction. swing: which side the leaf opens to, 90 for the wall's left-hand normal, -90 for its right.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "door".
 */
export interface Door {
  id: Id
  wallId: Id
  position: Millimetres
  width: PositiveMillimetres
  style?: DoorStyle
  hingeAtStart?: boolean
  swing?: DoorSwing
  confidence?: Confidence
  source?: PlanItemSource
}
/**
 * A window in a wall. position: distance from the wall's start to the window's centre.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "window".
 */
export interface Window {
  id: Id
  wallId: Id
  position: Millimetres
  width: PositiveMillimetres
  confidence?: Confidence
  source?: PlanItemSource
}
/**
 * A gap in a wall that is neither clearly a door nor clearly a window: a cased opening, or one whose symbol could not be read. Kept so the editor can show it and the user can say what it is; the engine treats it as a passage with no leaf. position: distance from the wall's start to the opening's centre.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "opening".
 */
export interface Opening {
  id: Id
  wallId: Id
  position: Millimetres
  width: PositiveMillimetres
  confidence?: Confidence
  source?: PlanItemSource
}
/**
 * Text on the plan, at the centre of where it is written. roomId: set when the label was matched to a room during analysis.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "label".
 */
export interface Label {
  id: Id
  text: string
  position: Point
  type: LabelKind
  roomId?: Id
  confidence?: Confidence
  source?: PlanItemSource
}
/**
 * A measurement written on the plan, drawn from start to end: its value, in the unit it was written in (the line itself is in millimetres like everything else). Confirms the plan's scale.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "dimension".
 */
export interface Dimension {
  id: Id
  start: Point
  end: Point
  value: number
  unit: DimensionUnit
  confidence?: Confidence
  source?: PlanItemSource
}
/**
 * The image the plan was analysed from, absent for a hand-built plan. imageUrl: where the untouched upload can be loaded from. planRegion: the detected drawing region, in pixels of the original image. mmPerPx: the scale applied to produce the millimetre coordinates. scaleConfidence and scaleMethod: how sure that scale is, and how it was found; below a threshold the engine refuses to size cables until the plan is calibrated.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "planSource".
 */
export interface PlanSource {
  imageUrl: string
  imageWidth: number
  imageHeight: number
  planRegion: PlanRegion
  mmPerPx: number
  scaleConfidence: Confidence
  scaleMethod: ScaleMethod
}
export interface PlanRegion {
  x: number
  y: number
  width: number
  height: number
}
/**
 * What the analyser did and found, for the analysis screen: each step and whether it worked, the counts, and warnings. Diagnostic only: nothing reads it to design.
 *
 * This interface was referenced by `FloorPlan`'s JSON-Schema
 * via the `definition` "analysisReport".
 */
export interface AnalysisReport {
  steps: AnalysisStep[]
  wallCount: number
  roomCount: number
  labelCount: number
  dimensionCount: number
  warnings: string[]
}
export interface AnalysisStep {
  name: string
  ok: boolean
  detail: string
}

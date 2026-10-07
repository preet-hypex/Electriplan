import { create } from 'zustand'
import type { Door, FloorPlan, Opening, Point, Wall, Window } from '../model/types'
import { emptyFloorPlan } from '../model/types'
import {
  createDoor,
  createWall,
  createWindow,
  seedIdCounter,
  typicalWallThickness,
} from '../model/factory'
import { DEFAULT_SNAP, type SnapSettings } from '../geometry/snap'
import {
  IDENTITY_VIEWPORT,
  fitBounds,
  panBy,
  zoomAt,
  zoomToScale,
  type Viewport,
} from '../geometry/viewport'
import { planBounds } from '../geometry/bounds'
import { interiorPoint } from '../geometry/polygon'

export type Tool = 'select' | 'wall' | 'room' | 'door' | 'window'

export type Selection =
  | { kind: 'wall'; id: string }
  | { kind: 'room'; id: string }
  | { kind: 'label'; id: string }
  | { kind: 'door'; id: string }
  | { kind: 'window'; id: string }
  | { kind: 'opening'; id: string }
  | null

/** Doors and windows differ only in what they are called and how they draw. */
export type OpeningKind = 'door' | 'window' | 'opening'

export type ViewMode = 'original' | 'reconstruction' | 'overlay'

/** Which end of a wall a drag is moving. 'body' moves the whole wall. */
export type WallHandle = 'start' | 'end' | 'body'

export interface DraftWall {
  start: Point
  end: Point
}

const HISTORY_LIMIT = 100

/**
 * Produce the next plan by mutating a deep copy. Plans are small (tens of
 * walls), so cloning per edit is cheaper than the bug surface of hand-written
 * immutable updates.
 */
function edit(plan: FloorPlan, fn: (draft: FloorPlan) => void): FloorPlan {
  const draft = structuredClone(plan)
  fn(draft)
  return draft
}

interface EditorState {
  plan: FloorPlan
  /**
   * Bumped only by loadPlan. The canvas frames the view when this changes, so
   * ordinary edits never yank the viewport out from under the user.
   */
  documentId: number
  past: FloorPlan[]
  future: FloorPlan[]
  /** Snapshot captured when an interaction began; pushed to history on end. */
  interactionBaseline: FloorPlan | null

  selection: Selection
  tool: Tool
  snap: SnapSettings
  viewport: Viewport
  viewMode: ViewMode
  backgroundOpacity: number
  showGrid: boolean
  /** Draw room names, OCR labels and dimension strings. */
  showText: boolean
  draftWall: DraftWall | null
  /** Set by the analysis flow so the editor can report what the backend did. */
  dirty: boolean
  /**
   * What the user calls this plan. Editor state, not part of the FloorPlan:
   * it names the saved file and is taken from the file a plan came from.
   */
  planName: string

  // -- document ------------------------------------------------------------
  loadPlan: (plan: FloorPlan, name?: string) => void
  setPlanName: (name: string) => void
  /** The plan has just been saved as it stands. */
  markSaved: () => void
  commit: (fn: (draft: FloorPlan) => void) => void

  // -- interactions (drag): one history entry per gesture ------------------
  beginInteraction: () => void
  updateInteraction: (fn: (draft: FloorPlan) => void) => void
  endInteraction: () => void
  cancelInteraction: () => void

  undo: () => void
  redo: () => void
  canUndo: () => boolean
  canRedo: () => boolean

  // -- selection & tools ---------------------------------------------------
  select: (selection: Selection) => void
  setTool: (tool: Tool) => void
  setSnap: (patch: Partial<SnapSettings>) => void

  // -- viewport ------------------------------------------------------------
  setViewport: (v: Viewport) => void
  zoomAtPoint: (anchor: Point, factor: number) => void
  setZoom: (scale: number, width: number, height: number) => void
  pan: (dx: number, dy: number) => void
  fitToView: (width: number, height: number) => void

  setViewMode: (mode: ViewMode) => void
  setBackgroundOpacity: (o: number) => void
  toggleGrid: () => void
  toggleText: () => void
  setDraftWall: (d: DraftWall | null) => void

  // -- domain operations ---------------------------------------------------
  addWall: (start: Point, end: Point, thickness?: number) => string
  deleteSelection: () => void
  updateWall: (id: string, patch: Partial<Omit<Wall, 'id'>>) => void
  /** Absolute geometry set used during a drag; no history entry of its own. */
  setWallEnds: (id: string, start: Point, end: Point) => void
  /** Insert an opening on a wall, at a distance along it. Returns its id. */
  addOpening: (kind: OpeningKind, wallId: string, position: number, width?: number) => string
  updateDoor: (id: string, patch: Partial<Omit<Door, 'id' | 'wallId'>>) => void
  updateWindow: (id: string, patch: Partial<Omit<Window, 'id' | 'wallId'>>) => void
  /** Turn a bare gap into a door or a window, keeping where and how wide. */
  convertOpening: (id: string, into: 'door' | 'window') => void
  /** Step a door through the four ways it can be hung. */
  rotateDoor: (id: string) => void
  /** Slide an opening along its wall during a drag; no history entry of its own. */
  setOpeningPosition: (kind: OpeningKind, id: string, position: number) => void
  /** Resize by dragging a jamb: centre and width move together. */
  setOpeningSpan: (kind: OpeningKind, id: string, position: number, width: number) => void
  renameRoom: (id: string, name: string) => void
  setRoomColour: (id: string, colour: string | undefined) => void
  moveRoomLabel: (id: string, position: Point) => void
  resetRoomLabel: (id: string) => void
}

function pushHistory(state: EditorState, baseline: FloorPlan): Partial<EditorState> {
  const past = [...state.past, baseline]
  if (past.length > HISTORY_LIMIT) past.shift()
  return { past, future: [] }
}

export const useEditor = create<EditorState>((set, get) => ({
  plan: emptyFloorPlan(),
  documentId: 0,
  past: [],
  future: [],
  interactionBaseline: null,

  selection: null,
  tool: 'select',
  snap: DEFAULT_SNAP,
  viewport: IDENTITY_VIEWPORT,
  viewMode: 'overlay',
  backgroundOpacity: 0.3,
  showGrid: false,
  showText: true,
  draftWall: null,
  dirty: false,
  planName: 'Untitled plan',

  setPlanName: (name) => set({ planName: name }),
  markSaved: () => set({ dirty: false }),

  loadPlan: (plan, name) => {
    seedIdCounter(plan)
    set((state) => ({
      planName: name?.trim() || 'Untitled plan',
      plan,
      documentId: state.documentId + 1,
      past: [],
      future: [],
      interactionBaseline: null,
      selection: null,
      draftWall: null,
      dirty: false,
      viewMode: plan.source ? 'overlay' : 'reconstruction',
    }))
  },

  commit: (fn) =>
    set((state) => ({
      ...pushHistory(state, state.plan),
      plan: edit(state.plan, fn),
      dirty: true,
    })),

  beginInteraction: () => set((state) => ({ interactionBaseline: state.plan })),

  updateInteraction: (fn) => set((state) => ({ plan: edit(state.plan, fn) })),

  endInteraction: () =>
    set((state) => {
      const baseline = state.interactionBaseline
      if (!baseline) return {}
      if (baseline === state.plan) return { interactionBaseline: null }
      return { ...pushHistory(state, baseline), interactionBaseline: null, dirty: true }
    }),

  cancelInteraction: () =>
    set((state) =>
      state.interactionBaseline
        ? { plan: state.interactionBaseline, interactionBaseline: null }
        : {},
    ),

  undo: () =>
    set((state) => {
      if (state.past.length === 0) return {}
      const past = [...state.past]
      const previous = past.pop() as FloorPlan
      return {
        past,
        plan: previous,
        future: [state.plan, ...state.future].slice(0, HISTORY_LIMIT),
        selection: null,
        dirty: true,
      }
    }),

  redo: () =>
    set((state) => {
      if (state.future.length === 0) return {}
      const [next, ...future] = state.future
      return {
        past: [...state.past, state.plan],
        plan: next,
        future,
        selection: null,
        dirty: true,
      }
    }),

  canUndo: () => get().past.length > 0,
  canRedo: () => get().future.length > 0,

  select: (selection) => set({ selection }),
  setTool: (tool) => set({ tool, draftWall: null }),
  setSnap: (patch) => set((state) => ({ snap: { ...state.snap, ...patch } })),

  setViewport: (viewport) => set({ viewport }),
  zoomAtPoint: (anchor, factor) => set((state) => ({ viewport: zoomAt(state.viewport, anchor, factor) })),
  setZoom: (scale, width, height) =>
    set((state) => ({ viewport: zoomToScale(state.viewport, scale, width, height) })),
  pan: (dx, dy) => set((state) => ({ viewport: panBy(state.viewport, dx, dy) })),
  fitToView: (width, height) =>
    set((state) => ({ viewport: fitBounds(planBounds(state.plan), width, height) })),

  setViewMode: (viewMode) => set({ viewMode }),
  setBackgroundOpacity: (backgroundOpacity) => set({ backgroundOpacity }),
  toggleGrid: () => set((state) => ({ showGrid: !state.showGrid })),
  toggleText: () => set((state) => ({ showText: !state.showText })),
  setDraftWall: (draftWall) => set({ draftWall }),

  addWall: (start, end, thickness) => {
    // Match the walls already in the plan rather than a constant.
    const wall = createWall(start, end, thickness ?? typicalWallThickness(get().plan.walls))
    get().commit((draft) => {
      draft.walls.push(wall)
    })
    set({ selection: { kind: 'wall', id: wall.id } })
    return wall.id
  },

  deleteSelection: () => {
    const { selection } = get()
    if (!selection) return
    get().commit((draft) => {
      if (selection.kind === 'wall') {
        draft.walls = draft.walls.filter((w) => w.id !== selection.id)
        // Openings reference a wall; they cannot outlive it.
        draft.doors = draft.doors.filter((d) => d.wallId !== selection.id)
        draft.windows = draft.windows.filter((w) => w.wallId !== selection.id)
      } else if (selection.kind === 'room') {
        draft.rooms = draft.rooms.filter((r) => r.id !== selection.id)
        draft.labels = draft.labels.filter((l) => l.roomId !== selection.id)
      } else if (selection.kind === 'door') {
        draft.doors = draft.doors.filter((d) => d.id !== selection.id)
      } else if (selection.kind === 'window') {
        draft.windows = draft.windows.filter((w) => w.id !== selection.id)
      } else if (selection.kind === 'opening') {
        draft.openings = draft.openings.filter((o) => o.id !== selection.id)
      } else {
        draft.labels = draft.labels.filter((l) => l.id !== selection.id)
      }
    })
    set({ selection: null })
  },

  updateWall: (id, patch) =>
    get().commit((draft) => {
      const w = draft.walls.find((x) => x.id === id)
      if (!w) return
      Object.assign(w, patch)
      // An edited wall is no longer purely the detector's opinion.
      w.source = 'manual'
      delete w.confidence
    }),

  setWallEnds: (id, start, end) =>
    get().updateInteraction((draft) => {
      const w = draft.walls.find((x) => x.id === id)
      if (!w) return
      w.start = { ...start }
      w.end = { ...end }
      w.source = 'manual'
      delete w.confidence
    }),

  addOpening: (kind, wallId, position, width) => {
    if (kind === 'opening') return ''
    const opening =
      kind === 'door' ? createDoor(wallId, position, width) : createWindow(wallId, position, width)
    get().commit((draft) => {
      if (kind === 'door') draft.doors.push(opening as Door)
      else draft.windows.push(opening as Window)
    })
    set({ selection: { kind, id: opening.id } })
    return opening.id
  },

  convertOpening: (id, into) => {
    const gap = get().plan.openings.find((o) => o.id === id)
    if (!gap) return
    // Keep the geometry the detector measured — it found the jambs, it just
    // could not read which kind of opening sits between them.
    const made =
      into === 'door'
        ? createDoor(gap.wallId, gap.position, gap.width)
        : createWindow(gap.wallId, gap.position, gap.width)
    get().commit((draft) => {
      draft.openings = draft.openings.filter((o) => o.id !== id)
      if (into === 'door') draft.doors.push(made as Door)
      else draft.windows.push(made as Window)
    })
    set({ selection: { kind: into, id: made.id } })
  },

  rotateDoor: (id) =>
    get().commit((draft) => {
      const door = draft.doors.find((d) => d.id === id)
      if (!door) return
      // Four quarter-turns: swap the side, and every second turn swap the jamb
      // too, so repeated presses walk all four hangings and come back round.
      const hinge = door.hingeAtStart ?? true
      const opens = (door.swing ?? 90) >= 0
      if (opens) {
        door.swing = -90
      } else {
        door.swing = 90
        door.hingeAtStart = !hinge
      }
      door.source = 'manual'
      delete door.confidence
    }),

  updateDoor: (id, patch) =>
    get().commit((draft) => {
      const door = draft.doors.find((d) => d.id === id)
      if (!door) return
      Object.assign(door, patch)
      door.source = 'manual'
      delete door.confidence
    }),

  updateWindow: (id, patch) =>
    get().commit((draft) => {
      const win = draft.windows.find((w) => w.id === id)
      if (!win) return
      Object.assign(win, patch)
      win.source = 'manual'
      delete win.confidence
    }),

  setOpeningPosition: (kind, id, position) =>
    get().updateInteraction((draft) => {
      const list: Array<Door | Window | Opening> =
        kind === 'door' ? draft.doors : kind === 'window' ? draft.windows : draft.openings
      const opening = list.find((o) => o.id === id)
      if (!opening) return
      opening.position = position
      opening.source = 'manual'
      delete opening.confidence
    }),

  setOpeningSpan: (kind, id, position, width) =>
    get().updateInteraction((draft) => {
      const list: Array<Door | Window | Opening> =
        kind === 'door' ? draft.doors : kind === 'window' ? draft.windows : draft.openings
      const opening = list.find((o) => o.id === id)
      if (!opening) return
      opening.position = position
      opening.width = width
      opening.source = 'manual'
      delete opening.confidence
    }),

  renameRoom: (id, name) =>
    get().commit((draft) => {
      const r = draft.rooms.find((x) => x.id === id)
      if (!r) return
      r.name = name
      r.source = 'manual'
      delete r.confidence
      const label = draft.labels.find((l) => l.roomId === id && l.type === 'room')
      if (label) label.text = name
    }),

  setRoomColour: (id, colour) =>
    get().commit((draft) => {
      const r = draft.rooms.find((x) => x.id === id)
      if (r) r.colour = colour
    }),

  moveRoomLabel: (id, position) =>
    get().updateInteraction((draft) => {
      const r = draft.rooms.find((x) => x.id === id)
      if (!r) return
      r.labelPosition = { ...position }
      const label = draft.labels.find((l) => l.roomId === id && l.type === 'room')
      if (label) label.position = { ...position }
    }),

  resetRoomLabel: (id) =>
    get().commit((draft) => {
      const r = draft.rooms.find((x) => x.id === id)
      if (!r) return
      r.labelPosition = interiorPoint(r.polygon)
      const label = draft.labels.find((l) => l.roomId === id && l.type === 'room')
      if (label) label.position = { ...r.labelPosition }
    }),
}))

export const selectedWall = (state: EditorState): Wall | undefined =>
  state.selection?.kind === 'wall'
    ? state.plan.walls.find((w) => w.id === state.selection!.id)
    : undefined

export const selectedRoom = (state: EditorState) =>
  state.selection?.kind === 'room'
    ? state.plan.rooms.find((r) => r.id === state.selection!.id)
    : undefined

export const selectedLabel = (state: EditorState) =>
  state.selection?.kind === 'label'
    ? state.plan.labels.find((l) => l.id === state.selection!.id)
    : undefined

export const selectedDoor = (state: EditorState) =>
  state.selection?.kind === 'door'
    ? state.plan.doors.find((d) => d.id === state.selection!.id)
    : undefined

export const selectedWindow = (state: EditorState) =>
  state.selection?.kind === 'window'
    ? state.plan.windows.find((w) => w.id === state.selection!.id)
    : undefined

export const selectedGap = (state: EditorState) =>
  state.selection?.kind === 'opening'
    ? state.plan.openings.find((o) => o.id === state.selection!.id)
    : undefined

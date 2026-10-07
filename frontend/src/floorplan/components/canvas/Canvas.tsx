import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useEditor, type Selection, type WallHandle } from '../../state/store'
import { pxToMm, toModel } from '../../geometry/viewport'
import { growBounds, planBounds } from '../../geometry/bounds'
import { constrainOrthogonal, snapAlongAxis, snapPoint } from '../../geometry/snap'
import { projectOntoWall, wallLength } from '../../geometry/wall'
import {
  DEFAULT_DOOR_WIDTH,
  DEFAULT_WINDOW_WIDTH,
  typicalWallThickness,
} from '../../model/factory'
import type { FloorPlan, Point } from '../../model/types'
import { BackgroundLayer, GridLayer } from './BackgroundLayer'
import {
  DimensionLayer,
  DraftWallLayer,
  LabelLayer,
  OpeningLayer,
  RoomLayer,
  WallHandles,
  WallLayer,
} from './layers'

/**
 * Drags are computed as an absolute offset from where the pointer went down,
 * applied to a snapshot taken at that moment. Accumulating per-move deltas
 * instead lets a single stray event permanently displace the object.
 */
type Gesture =
  | { kind: 'none' }
  | { kind: 'pan'; lastScreen: Point }
  | {
      kind: 'wall-drag'
      id: string
      handle: WallHandle
      pressModel: Point
      pressScreen: Point
      origin: { start: Point; end: Point }
      engaged: boolean
    }
  | {
      kind: 'label-drag'
      roomId: string
      pressModel: Point
      pressScreen: Point
      origin: Point
      engaged: boolean
    }
  | {
      kind: 'opening-drag'
      openingKind: 'door' | 'window' | 'opening'
      id: string
      wallId: string
      pressScreen: Point
      engaged: boolean
    }
  | {
      kind: 'opening-resize'
      openingKind: 'door' | 'window' | 'opening'
      id: string
      wallId: string
      /** Distance along the wall of the jamb that is staying put. */
      anchor: number
      pressScreen: Point
      engaged: boolean
    }
  | { kind: 'draw'; start: Point }

/** Pointer travel, in screen pixels, before a press becomes a drag. */
const DRAG_THRESHOLD_PX = 3

/**
 * Should this drag be held to an axis?
 *
 * Shift *forces* straight, which is the convention everywhere else. The
 * Orthogonal setting decides what happens with nothing held, so turning it off
 * gives free angles by default with Shift still available on demand.
 */
function straightening(event: { shiftKey: boolean }, snap: { orthogonal: boolean }): boolean {
  return event.shiftKey || snap.orthogonal
}
/** Narrowest an opening may be dragged, in millimetres. */
const MIN_OPENING_WIDTH = 150

/** Look an opening up whichever of the three lists it lives in. */
function openingById(plan: FloorPlan, kind: 'door' | 'window' | 'opening', id: string) {
  const list =
    kind === 'door' ? plan.doors : kind === 'window' ? plan.windows : plan.openings
  return list.find((o) => o.id === id)
}

export function Canvas() {
  const svgRef = useRef<SVGSVGElement>(null)
  const wrapRef = useRef<HTMLDivElement>(null)
  const gesture = useRef<Gesture>({ kind: 'none' })
  const [size, setSize] = useState({ width: 0, height: 0 })
  const [spaceHeld, setSpaceHeld] = useState(false)

  const plan = useEditor((s) => s.plan)
  const viewport = useEditor((s) => s.viewport)
  const selection = useEditor((s) => s.selection)
  const tool = useEditor((s) => s.tool)
  const viewMode = useEditor((s) => s.viewMode)
  const backgroundOpacity = useEditor((s) => s.backgroundOpacity)
  const showGrid = useEditor((s) => s.showGrid)
  const showText = useEditor((s) => s.showText)
  const draftWall = useEditor((s) => s.draftWall)

  const mmPerPixel = pxToMm(viewport, 1)

  // Track the container size so fit-to-view and the initial framing are right.
  useLayoutEffect(() => {
    const el = wrapRef.current
    if (!el) return
    const ro = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect
      setSize({ width, height })
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  // Frame the plan when a different document is loaded — not on every edit.
  const documentId = useEditor((s) => s.documentId)
  const framed = useRef<number>(-1)
  useEffect(() => {
    if (size.width === 0 || size.height === 0) return
    if (framed.current === documentId) return
    framed.current = documentId
    useEditor.getState().fitToView(size.width, size.height)
  }, [documentId, size.width, size.height])

  useEffect(() => {
    const down = (e: KeyboardEvent) => {
      if (e.code === 'Space') setSpaceHeld(true)
    }
    const up = (e: KeyboardEvent) => {
      if (e.code === 'Space') setSpaceHeld(false)
    }
    window.addEventListener('keydown', down)
    window.addEventListener('keyup', up)
    return () => {
      window.removeEventListener('keydown', down)
      window.removeEventListener('keyup', up)
    }
  }, [])

  const screenOf = useCallback((e: { clientX: number; clientY: number }): Point => {
    const rect = svgRef.current?.getBoundingClientRect()
    return { x: e.clientX - (rect?.left ?? 0), y: e.clientY - (rect?.top ?? 0) }
  }, [])

  const modelOf = useCallback(
    (e: { clientX: number; clientY: number }): Point =>
      toModel(useEditor.getState().viewport, screenOf(e)),
    [screenOf],
  )

  // -- wheel: zoom about the cursor ----------------------------------------
  useEffect(() => {
    const el = svgRef.current
    if (!el) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      const factor = Math.exp(-e.deltaY * 0.0015)
      useEditor.getState().zoomAtPoint(screenOf(e), factor)
    }
    el.addEventListener('wheel', onWheel, { passive: false })
    return () => el.removeEventListener('wheel', onWheel)
  }, [screenOf])

  const startPan = (e: React.PointerEvent) => {
    gesture.current = { kind: 'pan', lastScreen: screenOf(e) }
    ;(e.target as Element).setPointerCapture?.(e.pointerId)
  }

  /** Clicking a wall, room or label. Bubbles up from the layer components. */
  const handlePick = (picked: Selection, e: React.PointerEvent) => {
    if (e.button !== 0) return
    if (spaceHeld || e.altKey) return // let the pan gesture through
    // Drawing or placing takes priority over selecting.
    if (tool === 'wall' || tool === 'door' || tool === 'window') return
    e.stopPropagation()

    const store = useEditor.getState()
    store.select(picked)
    svgRef.current?.setPointerCapture(e.pointerId)

    const isLabel = (e.target as Element).getAttribute?.('data-role') === 'room-label'

    if (picked?.kind === 'wall') {
      const wall = store.plan.walls.find((w) => w.id === picked.id)
      if (!wall) return
      store.beginInteraction()
      gesture.current = {
        kind: 'wall-drag',
        id: picked.id,
        handle: 'body',
        pressModel: modelOf(e),
        pressScreen: screenOf(e),
        origin: { start: { ...wall.start }, end: { ...wall.end } },
        engaged: false,
      }
    } else if (
      picked?.kind === 'door' ||
      picked?.kind === 'window' ||
      picked?.kind === 'opening'
    ) {
      const opening = openingById(store.plan, picked.kind, picked.id)
      if (!opening) return
      store.beginInteraction()
      gesture.current = {
        kind: 'opening-drag',
        openingKind: picked.kind,
        id: picked.id,
        wallId: opening.wallId,
        pressScreen: screenOf(e),
        engaged: false,
      }
    } else if (picked?.kind === 'room' && isLabel) {
      const room = store.plan.rooms.find((r) => r.id === picked.id)
      if (!room) return
      store.beginInteraction()
      gesture.current = {
        kind: 'label-drag',
        roomId: picked.id,
        pressModel: modelOf(e),
        pressScreen: screenOf(e),
        origin: { ...room.labelPosition },
        engaged: false,
      }
    }
  }

  const grabWallHandle = (handle: 'start' | 'end', e: React.PointerEvent) => {
    if (e.button !== 0 || selection?.kind !== 'wall') return
    const store = useEditor.getState()
    const wall = store.plan.walls.find((w) => w.id === selection.id)
    if (!wall) return
    e.stopPropagation()
    svgRef.current?.setPointerCapture(e.pointerId)
    store.beginInteraction()
    gesture.current = {
      kind: 'wall-drag',
      id: selection.id,
      handle,
      pressModel: modelOf(e),
      pressScreen: screenOf(e),
      origin: { start: { ...wall.start }, end: { ...wall.end } },
      engaged: false,
    }
  }

  /** Grab a jamb handle: the other jamb stays put and the opening resizes. */
  const grabJamb = (
    kind: 'door' | 'window' | 'opening',
    id: string,
    jamb: 'start' | 'end',
    e: React.PointerEvent,
  ) => {
    if (e.button !== 0) return
    const store = useEditor.getState()
    const opening = openingById(store.plan, kind, id)
    if (!opening) return
    e.stopPropagation()
    svgRef.current?.setPointerCapture(e.pointerId)
    store.beginInteraction()
    gesture.current = {
      kind: 'opening-resize',
      openingKind: kind,
      id,
      wallId: opening.wallId,
      anchor: opening.position + (jamb === 'start' ? opening.width / 2 : -opening.width / 2),
      pressScreen: screenOf(e),
      engaged: false,
    }
  }

  const rotate = (id: string, e: React.PointerEvent) => {
    if (e.button !== 0) return
    e.stopPropagation()
    useEditor.getState().rotateDoor(id)
  }


  const onPointerDown = (e: React.PointerEvent) => {
    // Middle button, alt-drag or space-drag always pans, whatever the tool.
    if (e.button === 1 || e.altKey || spaceHeld) {
      e.preventDefault()
      startPan(e)
      return
    }
    if (e.button !== 0) return

    const store = useEditor.getState()
    if (tool === 'door' || tool === 'window') {
      placeOpening(tool, modelOf(e))
      return
    }
    if (tool === 'wall') {
      const raw = modelOf(e)
      const start = snapPoint(raw, store.plan.walls, store.snap).point
      svgRef.current?.setPointerCapture(e.pointerId)
      gesture.current = { kind: 'draw', start }
      store.setDraftWall({ start, end: start })
      return
    }

    // Empty background with the select tool: clear selection, then pan.
    store.select(null)
    startPan(e)
  }

  /**
   * A press only becomes a drag once the pointer has actually travelled. Without
   * this, the stray pointermove browsers emit between press and release turns
   * every click into a move.
   */
  const engage = (
    g: { engaged: boolean; pressScreen: Point },
    e: React.PointerEvent,
  ): boolean => {
    if (g.engaged) return true
    const s = screenOf(e)
    if (Math.hypot(s.x - g.pressScreen.x, s.y - g.pressScreen.y) < DRAG_THRESHOLD_PX) return false
    g.engaged = true
    return true
  }

  /**
   * Drop a new opening on whichever wall was clicked. An opening only exists
   * as a distance along a wall, so clicking empty space cannot make one — the
   * nearest wall within reach is found and the click projected onto it.
   */
  const placeOpening = (kind: 'door' | 'window', at: Point) => {
    const store = useEditor.getState()
    const reach = pxToMm(store.viewport, 24)

    let best: { id: string; position: number; distance: number } | null = null
    for (const wall of store.plan.walls) {
      const projected = projectOntoWall(wall, at)
      const slop = Math.max(wall.thickness, reach)
      if (projected.distance > slop) continue
      if (best === null || projected.distance < best.distance) {
        best = {
          id: wall.id,
          position: projected.t * wallLength(wall),
          distance: projected.distance,
        }
      }
    }
    if (!best) return

    // If the detector already found a gap about here, adopt it: it measured
    // the jambs, so its position and width beat anything a click can express.
    const gap = store.plan.openings.find(
      (o) => o.wallId === best!.id && Math.abs(o.position - best!.position) <= o.width,
    )
    if (gap) {
      store.convertOpening(gap.id, kind)
      store.setTool('select')
      return
    }

    const width = kind === 'door' ? DEFAULT_DOOR_WIDTH : DEFAULT_WINDOW_WIDTH
    const wall = store.plan.walls.find((w) => w.id === best!.id)!
    const half = Math.min(width, wallLength(wall)) / 2
    const position = Math.max(half, Math.min(wallLength(wall) - half, best.position))
    store.addOpening(kind, best.id, position, width)
    store.setTool('select')
  }

  const onPointerMove = (e: React.PointerEvent) => {
    const g = gesture.current
    const store = useEditor.getState()

    if (g.kind === 'pan') {
      const s = screenOf(e)
      store.pan(s.x - g.lastScreen.x, s.y - g.lastScreen.y)
      gesture.current = { kind: 'pan', lastScreen: s }
      return
    }

    if (g.kind === 'wall-drag') {
      if (!engage(g, e)) return
      const m = modelOf(e)
      const grid = store.snap.grid

      if (g.handle === 'body') {
        // The wall keeps its shape; only the offset is quantised.
        let dx = m.x - g.pressModel.x
        let dy = m.y - g.pressModel.y
        if (grid > 0) {
          dx = Math.round(dx / grid) * grid
          dy = Math.round(dy / grid) * grid
        }
        store.setWallEnds(
          g.id,
          { x: g.origin.start.x + dx, y: g.origin.start.y + dy },
          { x: g.origin.end.x + dx, y: g.origin.end.y + dy },
        )
        return
      }

      const anchor = g.handle === 'start' ? g.origin.end : g.origin.start
      const moving = g.handle === 'start' ? g.origin.start : g.origin.end
      let target = { x: moving.x + (m.x - g.pressModel.x), y: moving.y + (m.y - g.pressModel.y) }

      if (straightening(e, store.snap)) {
        // Stay on the axis *and* connect: snapAlongAxis looks for where the
        // constraint line meets the drawing rather than pulling off it.
        target = snapAlongAxis(
          anchor,
          constrainOrthogonal(anchor, target),
          store.plan.walls,
          store.snap,
          g.id,
        ).point
      } else {
        const snapped = snapPoint(target, store.plan.walls, store.snap, g.id)
        if (snapped.kind !== 'none') target = snapped.point
      }
      store.setWallEnds(
        g.id,
        g.handle === 'start' ? target : anchor,
        g.handle === 'start' ? anchor : target,
      )
      return
    }

    if (g.kind === 'opening-drag') {
      if (!engage(g, e)) return
      const wall = store.plan.walls.find((w) => w.id === g.wallId)
      if (!wall) return
      const opening = openingById(store.plan, g.openingKind, g.id)
      if (!opening) return
      // An opening slides along its wall and nowhere else, so the pointer is
      // projected onto the wall and clamped to keep the whole opening on it.
      const length = wallLength(wall)
      const half = Math.min(opening.width, length) / 2
      const projected = projectOntoWall(wall, modelOf(e))
      store.setOpeningPosition(
        g.openingKind,
        g.id,
        Math.max(half, Math.min(length - half, projected.t * length)),
      )
      return
    }

    if (g.kind === 'opening-resize') {
      if (!engage(g, e)) return
      const wall = store.plan.walls.find((w) => w.id === g.wallId)
      if (!wall) return
      const length = wallLength(wall)
      const dragged = Math.max(0, Math.min(length, projectOntoWall(wall, modelOf(e)).t * length))
      const width = Math.abs(dragged - g.anchor)
      if (width < MIN_OPENING_WIDTH) return
      store.setOpeningSpan(g.openingKind, g.id, (dragged + g.anchor) / 2, width)
      return
    }

    if (g.kind === 'label-drag') {
      if (!engage(g, e)) return
      const m = modelOf(e)
      store.moveRoomLabel(g.roomId, {
        x: g.origin.x + (m.x - g.pressModel.x),
        y: g.origin.y + (m.y - g.pressModel.y),
      })
      return
    }

    if (g.kind === 'draw') {
      let end = modelOf(e)
      if (straightening(e, store.snap)) {
        end = snapAlongAxis(
          g.start,
          constrainOrthogonal(g.start, end),
          store.plan.walls,
          store.snap,
        ).point
      } else {
        // Free angle: connect to an endpoint or to a point on a wall's body.
        const snapped = snapPoint(end, store.plan.walls, store.snap)
        if (snapped.kind === 'endpoint' || snapped.kind === 'wall') end = snapped.point
      }
      store.setDraftWall({ start: g.start, end })
    }
  }

  const onPointerUp = (e: React.PointerEvent) => {
    const g = gesture.current
    const store = useEditor.getState()
    gesture.current = { kind: 'none' }
    try {
      svgRef.current?.releasePointerCapture(e.pointerId)
    } catch {
      /* capture may already be gone */
    }

    if (
      g.kind === 'wall-drag' ||
      g.kind === 'label-drag' ||
      g.kind === 'opening-drag' ||
      g.kind === 'opening-resize'
    ) {
      store.endInteraction()
      return
    }

    if (g.kind === 'draw') {
      const draft = store.draftWall
      store.setDraftWall(null)
      if (!draft) return
      const length = Math.hypot(draft.end.x - draft.start.x, draft.end.y - draft.start.y)
      // Ignore an accidental click; a wall needs some length to be meaningful.
      if (length < 100) return
      store.addWall(draft.start, draft.end)
      store.setTool('select')
    }
  }

  const bounds = growBounds(planBounds(plan), 1000)
  const selectedWall =
    selection?.kind === 'wall' ? plan.walls.find((w) => w.id === selection.id) : undefined

  const showReconstruction = viewMode !== 'original'
  const showBackground = plan.source && viewMode !== 'reconstruction'
  const bgOpacity = viewMode === 'original' ? 1 : backgroundOpacity

  const cursor =
    spaceHeld ? 'grab' : tool === 'select' || tool === 'room' ? 'default' : 'crosshair'

  return (
    <div ref={wrapRef} className="relative h-full w-full overflow-hidden bg-slate-100">
      <svg
        ref={svgRef}
        className="h-full w-full touch-none"
        style={{ cursor }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
        onContextMenu={(e) => e.preventDefault()}
      >
        <rect width="100%" height="100%" fill="#f1f5f9" />
        <g transform={`translate(${viewport.tx} ${viewport.ty}) scale(${viewport.scale})`}>
          {showBackground && plan.source && (
            <BackgroundLayer source={plan.source} opacity={bgOpacity} />
          )}
          {showGrid && <GridLayer bounds={bounds} mmPerPixel={mmPerPixel} />}

          {showReconstruction && (
            <>
              <RoomLayer
                plan={plan}
                selection={selection}
                mmPerPixel={mmPerPixel}
                onPick={handlePick}
              />
              <WallLayer
                plan={plan}
                selection={selection}
                mmPerPixel={mmPerPixel}
                onPick={handlePick}
              />
              <OpeningLayer
                plan={plan}
                selection={selection}
                mmPerPixel={mmPerPixel}
                onPick={handlePick}
                onGrabJamb={grabJamb}
                onRotate={rotate}
              />
              {/* Room names, OCR labels and dimension strings — everything
                  the plan says rather than everything it is. */}
              {showText && (
                <>
                  <DimensionLayer plan={plan} mmPerPixel={mmPerPixel} />
                  <LabelLayer
                    plan={plan}
                    selection={selection}
                    mmPerPixel={mmPerPixel}
                    onPick={handlePick}
                  />
                </>
              )}
              {selectedWall && (
                <WallHandles
                  wall={selectedWall}
                  mmPerPixel={mmPerPixel}
                  onGrab={grabWallHandle}
                />
              )}
              {draftWall && (
                <DraftWallLayer
                  draft={draftWall}
                  mmPerPixel={mmPerPixel}
                  thickness={typicalWallThickness(plan.walls)}
                />
              )}
            </>
          )}
        </g>
      </svg>

      {viewMode === 'original' && (
        <div className="pointer-events-none absolute left-3 top-3 rounded bg-[color-mix(in_srgb,var(--ink)_80%,transparent)] px-2 py-1 text-xs text-white">
          Original image only — switch to Overlay or Reconstruction to edit
        </div>
      )}
      {(tool === 'door' || tool === 'window') && (
        <div className="pointer-events-none absolute left-3 top-3 rounded bg-[color-mix(in_srgb,var(--accent)_90%,transparent)] px-2 py-1 text-xs text-white">
          Click a wall to place a {tool}. Esc to cancel.
        </div>
      )}
      {tool === 'wall' && (
        <div className="pointer-events-none absolute left-3 top-3 rounded bg-[color-mix(in_srgb,var(--accent)_90%,transparent)] px-2 py-1 text-xs text-white">
          Drag to draw a wall. Shift holds it straight and runs it onto the wall
          it meets. Esc to cancel.
        </div>
      )}
    </div>
  )
}

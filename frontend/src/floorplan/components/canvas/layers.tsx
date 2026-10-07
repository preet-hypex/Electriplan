import type { Dimension, FloorPlan, Label, Room, Wall } from '../../model/types'
import { bandOf } from '../../model/confidence'
import { polygonToPathData } from '../../geometry/polygon'
import { wallLength, wallQuad } from '../../geometry/wall'
import type { Selection } from '../../state/store'

const WALL_FILL: Record<string, string> = {
  high: '#111827',
  unknown: '#111827',
  medium: '#b45309',
  low: '#eab308',
}

const ROOM_FILL: Record<string, string> = {
  high: '#3b82f6',
  unknown: '#3b82f6',
  medium: '#f59e0b',
  low: '#eab308',
}

export interface LayerProps {
  plan: FloorPlan
  selection: Selection
  /** One millimetre expressed in screen pixels — used to keep strokes crisp. */
  mmPerPixel: number
  onPick: (selection: Selection, event: React.PointerEvent) => void
}

export function RoomLayer({ plan, selection, mmPerPixel, onPick }: LayerProps) {
  return (
    <g data-layer="rooms">
      {plan.rooms.map((room: Room) => {
        const isSelected = selection?.kind === 'room' && selection.id === room.id
        const band = bandOf(room)
        const fill = room.colour ?? ROOM_FILL[band]
        return (
          <path
            key={room.id}
            d={polygonToPathData(room.polygon)}
            fill={fill}
            fillOpacity={isSelected ? 0.3 : 0.12}
            stroke={isSelected ? '#2563eb' : 'none'}
            strokeWidth={isSelected ? mmPerPixel * 2 : 0}
            style={{ cursor: 'pointer' }}
            onPointerDown={(e) => onPick({ kind: 'room', id: room.id }, e)}
          />
        )
      })}
    </g>
  )
}

export function WallLayer({ plan, selection, mmPerPixel, onPick }: LayerProps) {
  return (
    <g data-layer="walls">
      {plan.walls.map((wall: Wall) => {
        const isSelected = selection?.kind === 'wall' && selection.id === wall.id
        const band = bandOf(wall)
        const quad = wallQuad(wall)
        const points = quad.map((p) => `${p.x},${p.y}`).join(' ')
        return (
          <g key={wall.id}>
            {/* Invisible fat centre line so thin walls stay easy to grab. */}
            <line
              x1={wall.start.x}
              y1={wall.start.y}
              x2={wall.end.x}
              y2={wall.end.y}
              stroke="transparent"
              strokeWidth={Math.max(wall.thickness, mmPerPixel * 12)}
              strokeLinecap="butt"
              style={{ cursor: 'move' }}
              onPointerDown={(e) => onPick({ kind: 'wall', id: wall.id }, e)}
            />
            <polygon
              points={points}
              fill={isSelected ? '#2563eb' : WALL_FILL[band]}
              pointerEvents="none"
            />
          </g>
        )
      })}
    </g>
  )
}

export function LabelLayer({ plan, selection, mmPerPixel, onPick }: LayerProps) {
  const fontSize = Math.max(180, mmPerPixel * 13)
  return (
    <g data-layer="labels">
      {plan.rooms.map((room) => {
        const isSelected = selection?.kind === 'room' && selection.id === room.id
        return (
          <text
            key={room.id}
            x={room.labelPosition.x}
            y={room.labelPosition.y}
            fontSize={fontSize}
            textAnchor="middle"
            dominantBaseline="middle"
            fill={isSelected ? '#1d4ed8' : '#111827'}
            fontWeight={600}
            style={{ cursor: 'grab', userSelect: 'none' }}
            data-role="room-label"
            data-room-id={room.id}
            onPointerDown={(e) => onPick({ kind: 'room', id: room.id }, e)}
          >
            {room.name || '(unnamed)'}
          </text>
        )
      })}

      {/* OCR text that was not adopted as a room name, e.g. dimension strings. */}
      {plan.labels
        .filter((l: Label) => l.type !== 'room')
        .map((label) => {
          const isSelected = selection?.kind === 'label' && selection.id === label.id
          return (
            <text
              key={label.id}
              x={label.position.x}
              y={label.position.y}
              fontSize={fontSize * 0.72}
              textAnchor="middle"
              dominantBaseline="middle"
              fill={isSelected ? '#1d4ed8' : '#6b7280'}
              style={{ cursor: 'pointer', userSelect: 'none' }}
              onPointerDown={(e) => onPick({ kind: 'label', id: label.id }, e)}
            >
              {label.text}
            </text>
          )
        })}
    </g>
  )
}

/**
 * Doors and windows, drawn on the wall they belong to.
 *
 * They are stored as a position along a wall rather than as coordinates, so
 * moving the wall carries them with it — the layer resolves the wall and
 * interpolates every render, and nothing has to be kept in step by hand.
 */
/**
 * How tall an opening's click target is, in screen pixels. Generous on
 * purpose: the symbol is a hairline on a wall that is itself only a few pixels
 * wide, and a target the size of the drawing means every near miss lands on
 * the room behind instead.
 */
const OPENING_HIT_PX = 22
/** Size of the drag handles, in screen pixels. */
const HANDLE_PX = 5

export function OpeningLayer({
  plan,
  selection,
  mmPerPixel,
  onPick,
  onGrabJamb,
  onRotate,
}: {
  plan: FloorPlan
  selection: Selection
  mmPerPixel: number
  onPick: (selection: Selection, event: React.PointerEvent) => void
  onGrabJamb: (
    kind: 'door' | 'window' | 'opening',
    id: string,
    jamb: 'start' | 'end',
    event: React.PointerEvent,
  ) => void
  onRotate: (id: string, event: React.PointerEvent) => void
}) {
  const wallsById = new Map(plan.walls.map((w) => [w.id, w]))

  /** The two jamb handles, for dragging an opening wider or narrower. */
  const jambHandles = (
    kind: 'door' | 'window' | 'opening',
    id: string,
    at: { centre: { x: number; y: number }; dir: { x: number; y: number } },
    width: number,
  ) => {
    const half = width / 2
    return (['start', 'end'] as const).map((jamb) => {
      const sign = jamb === 'start' ? -1 : 1
      const x = at.centre.x + at.dir.x * half * sign
      const y = at.centre.y + at.dir.y * half * sign
      return (
        <rect
          key={jamb}
          x={x - mmPerPixel * HANDLE_PX}
          y={y - mmPerPixel * HANDLE_PX}
          width={mmPerPixel * HANDLE_PX * 2}
          height={mmPerPixel * HANDLE_PX * 2}
          fill="#ffffff"
          stroke="#2563eb"
          strokeWidth={mmPerPixel * 2}
          style={{ cursor: 'ew-resize' }}
          onPointerDown={(e) => onGrabJamb(kind, id, jamb, e)}
        />
      )
    })
  }

  const place = (wallId: string, position: number) => {
    const w = wallsById.get(wallId)
    if (!w) return null
    const length = wallLength(w)
    if (length === 0) return null
    const t = Math.max(0, Math.min(1, position / length))
    const dx = (w.end.x - w.start.x) / length
    const dy = (w.end.y - w.start.y) / length
    return {
      centre: { x: w.start.x + (w.end.x - w.start.x) * t, y: w.start.y + (w.end.y - w.start.y) * t },
      dir: { x: dx, y: dy },
      normal: { x: -dy, y: dx },
      thickness: w.thickness,
    }
  }

  return (
    <g data-layer="openings">
      {plan.windows.map((win) => {
        const at = place(win.wallId, win.position)
        if (!at) return null
        const half = win.width / 2
        const depth = Math.max(at.thickness, mmPerPixel * 3)
        const isSelected = selection?.kind === 'window' && selection.id === win.id
        const stroke = isSelected ? '#2563eb' : '#0284c7'
        const angle = (Math.atan2(at.dir.y, at.dir.x) * 180) / Math.PI
        // A white band knocked out of the wall, with the reveal line drawn
        // through it — the conventional plan symbol.
        return (
          <g key={win.id} style={{ cursor: 'pointer' }}>
            <rect
              x={-half}
              y={-depth / 2}
              width={win.width}
              height={depth}
              fill="#ffffff"
              stroke={stroke}
              strokeWidth={isSelected ? mmPerPixel * 2 : mmPerPixel}
              transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
            />
            <line
              x1={at.centre.x - at.dir.x * half}
              y1={at.centre.y - at.dir.y * half}
              x2={at.centre.x + at.dir.x * half}
              y2={at.centre.y + at.dir.y * half}
              stroke={stroke}
              strokeWidth={mmPerPixel * 1.5}
            />
            {/* A generous invisible target: the symbol itself is a hairline. */}
            <rect
              x={-half}
              y={-Math.max(depth, mmPerPixel * OPENING_HIT_PX) / 2}
              width={win.width}
              height={Math.max(depth, mmPerPixel * OPENING_HIT_PX)}
              fill="transparent"
              transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
              onPointerDown={(e) => onPick({ kind: 'window', id: win.id }, e)}
            />
            {isSelected && jambHandles('window', win.id, at, win.width)}
          </g>
        )
      })}

      {/* Gaps the detector found but could not name. Drawn as a plain break
          in the wall, so they read as "something is here, you decide". */}
      {plan.openings.map((gap) => {
        const at = place(gap.wallId, gap.position)
        if (!at) return null
        const half = gap.width / 2
        const isSelected = selection?.kind === 'opening' && selection.id === gap.id
        const angle = (Math.atan2(at.dir.y, at.dir.x) * 180) / Math.PI
        const depth = Math.max(at.thickness, mmPerPixel * 3)
        return (
          <g key={gap.id} style={{ cursor: 'pointer' }}>
            <rect
              x={-half}
              y={-depth / 2}
              width={gap.width}
              height={depth}
              fill="#ffffff"
              stroke={isSelected ? '#2563eb' : '#f59e0b'}
              strokeWidth={isSelected ? mmPerPixel * 2 : mmPerPixel}
              strokeDasharray={`${mmPerPixel * 5} ${mmPerPixel * 4}`}
              transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
            />
            <rect
              x={-half}
              y={-Math.max(depth, mmPerPixel * OPENING_HIT_PX) / 2}
              width={gap.width}
              height={Math.max(depth, mmPerPixel * OPENING_HIT_PX)}
              fill="transparent"
              transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
              onPointerDown={(e) => onPick({ kind: 'opening', id: gap.id }, e)}
            />
            {isSelected && jambHandles('opening', gap.id, at, gap.width)}
          </g>
        )
      })}

      {plan.doors.map((door) => {
        const at = place(door.wallId, door.position)
        if (!at) return null
        const half = door.width / 2
        const isSelected = selection?.kind === 'door' && selection.id === door.id
        const angle = (Math.atan2(at.dir.y, at.dir.x) * 180) / Math.PI

        if (door.style === 'garage') {
          // Drawn the way plans draw it: a dashed rectangle the width of the
          // opening, showing where the door sits when it is shut. There is no
          // leaf and no arc — a garage door lifts, so there is nothing to
          // sweep — and the dashes are the whole of what identifies it.
          const stroke = isSelected ? '#2563eb' : '#111827'
          const depth = Math.max(at.thickness * 0.8, mmPerPixel * 3)
          const dash = Math.max(at.thickness * 0.7, mmPerPixel * 3)
          return (
            <g key={door.id} style={{ cursor: 'pointer' }}>
              <rect
                x={-half}
                y={-at.thickness / 2}
                width={door.width}
                height={at.thickness}
                fill="#ffffff"
                transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
              />
              <rect
                x={-half}
                y={-depth / 2}
                width={door.width}
                height={depth}
                fill="none"
                stroke={stroke}
                strokeWidth={isSelected ? mmPerPixel * 1.6 : mmPerPixel}
                strokeDasharray={`${dash} ${dash * 0.7}`}
                transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
              />
              <rect
                x={-half}
                y={-Math.max(depth, mmPerPixel * OPENING_HIT_PX) / 2}
                width={door.width}
                height={Math.max(depth, mmPerPixel * OPENING_HIT_PX)}
                fill="transparent"
                transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
                onPointerDown={(e) => onPick({ kind: 'door', id: door.id }, e)}
              />
              {isSelected && jambHandles('door', door.id, at, door.width)}
            </g>
          )
        }

        if ((door.style ?? 'swing') === 'sliding') {
          // Two panels passing one another: each covers most of the opening,
          // offset to opposite sides of the wall line so both are visible.
          const stroke = isSelected ? '#2563eb' : '#111827'
          const panel = Math.max(at.thickness * 0.3, mmPerPixel * 2)
          const reach = door.width * 0.58
          const shift = Math.max(at.thickness * 0.28, mmPerPixel * 2)
          return (
            <g key={door.id} style={{ cursor: 'pointer' }}>
              <rect
                x={-half}
                y={-at.thickness / 2}
                width={door.width}
                height={at.thickness}
                fill="#ffffff"
                transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
              />
              {[-1, 1].map((side) => (
                <rect
                  key={side}
                  x={side < 0 ? -half : half - reach}
                  y={side * shift - panel / 2}
                  width={reach}
                  height={panel}
                  fill="#ffffff"
                  stroke={stroke}
                  strokeWidth={mmPerPixel}
                  transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
                />
              ))}
              <rect
                x={-half}
                y={-Math.max(at.thickness, mmPerPixel * OPENING_HIT_PX) / 2}
                width={door.width}
                height={Math.max(at.thickness, mmPerPixel * OPENING_HIT_PX)}
                fill="transparent"
                transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
                onPointerDown={(e) => onPick({ kind: 'door', id: door.id }, e)}
              />
              {isSelected && jambHandles('door', door.id, at, door.width)}
            </g>
          )
        }

        // The symbol as it is actually drawn: the leaf leaves one jamb at a
        // right angle to the wall, and a quarter arc of the same radius closes
        // from its tip back onto the other jamb. Which jamb and which side both
        // come from the detector, so the reconstruction is hung the way the
        // plan drew it rather than always the same way.
        const sign = (door.swing ?? 90) >= 0 ? 1 : -1
        const towards = (door.hingeAtStart ?? true) ? 1 : -1
        const hinge = {
          x: at.centre.x - at.dir.x * half * towards,
          y: at.centre.y - at.dir.y * half * towards,
        }
        const jamb = {
          x: at.centre.x + at.dir.x * half * towards,
          y: at.centre.y + at.dir.y * half * towards,
        }
        const leafDir = { x: at.normal.x * sign, y: at.normal.y * sign }
        const leafEnd = {
          x: hinge.x + leafDir.x * door.width,
          y: hinge.y + leafDir.y * door.width,
        }
        // SVG's sweep flag is "positive angle", which is clockwise on a y-down
        // canvas. Deriving it from the geometry keeps the arc a quarter turn
        // rather than the three-quarter turn the other way round.
        const cross = leafDir.x * at.dir.y * towards - leafDir.y * at.dir.x * towards
        const sweep = cross > 0 ? 1 : 0
        const panel = Math.max(at.thickness * 0.35, mmPerPixel * 1.5)
        const stroke = isSelected ? '#2563eb' : '#111827'

        return (
          <g key={door.id} style={{ cursor: 'pointer' }}>
            {/* The opening itself, knocked out of the wall. */}
            <rect
              x={-half}
              y={-at.thickness / 2}
              width={door.width}
              height={at.thickness}
              fill="#ffffff"
              transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${
                (Math.atan2(at.dir.y, at.dir.x) * 180) / Math.PI
              })`}
            />
            {/* The leaf: a solid panel, not a hairline. */}
            <line
              x1={hinge.x}
              y1={hinge.y}
              x2={leafEnd.x}
              y2={leafEnd.y}
              stroke={stroke}
              strokeWidth={panel}
              strokeLinecap="butt"
            />
            {/* The swing, thin and solid, landing on the far jamb. */}
            <path
              d={`M ${leafEnd.x} ${leafEnd.y} A ${door.width} ${door.width} 0 0 ${sweep} ${jamb.x} ${jamb.y}`}
              fill="none"
              stroke={stroke}
              strokeWidth={mmPerPixel}
            />
            <rect
              x={-half}
              y={-Math.max(at.thickness, mmPerPixel * OPENING_HIT_PX) / 2}
              width={door.width}
              height={Math.max(at.thickness, mmPerPixel * OPENING_HIT_PX)}
              fill="transparent"
              transform={`translate(${at.centre.x} ${at.centre.y}) rotate(${angle})`}
              onPointerDown={(e) => onPick({ kind: 'door', id: door.id }, e)}
            />
            {isSelected && (
              <>
                {/* The hinge, so which end is which is never in doubt. */}
                <circle
                  cx={hinge.x}
                  cy={hinge.y}
                  r={mmPerPixel * 4}
                  fill="#2563eb"
                  pointerEvents="none"
                />
                {jambHandles('door', door.id, at, door.width)}
                {/* Rotate, on the canvas: the leaf is what you are looking at
                    when you notice the door opens the wrong way. */}
                <g
                  style={{ cursor: 'pointer' }}
                  onPointerDown={(e) => onRotate(door.id, e)}
                >
                  <circle
                    cx={at.centre.x - leafDir.x * mmPerPixel * 20}
                    cy={at.centre.y - leafDir.y * mmPerPixel * 20}
                    r={mmPerPixel * 9}
                    fill="#2563eb"
                    stroke="#ffffff"
                    strokeWidth={mmPerPixel * 1.5}
                  />
                  <text
                    x={at.centre.x - leafDir.x * mmPerPixel * 20}
                    y={at.centre.y - leafDir.y * mmPerPixel * 20}
                    fontSize={mmPerPixel * 12}
                    textAnchor="middle"
                    dominantBaseline="central"
                    fill="#ffffff"
                    pointerEvents="none"
                  >
                    ⟳
                  </text>
                </g>
              </>
            )}
          </g>
        )
      })}
    </g>
  )
}

export function DimensionLayer({ plan, mmPerPixel }: { plan: FloorPlan; mmPerPixel: number }) {
  if (plan.dimensions.length === 0) return null
  const fontSize = Math.max(140, mmPerPixel * 10)
  return (
    <g data-layer="dimensions" pointerEvents="none">
      {plan.dimensions.map((d: Dimension) => {
        const mx = (d.start.x + d.end.x) / 2
        const my = (d.start.y + d.end.y) / 2
        const mm = d.unit === 'm' ? d.value * 1000 : d.value
        return (
          <g key={d.id}>
            <line
              x1={d.start.x}
              y1={d.start.y}
              x2={d.end.x}
              y2={d.end.y}
              stroke="#7c3aed"
              strokeWidth={mmPerPixel}
              strokeDasharray={`${mmPerPixel * 6} ${mmPerPixel * 4}`}
            />
            <text
              x={mx}
              y={my - fontSize * 0.4}
              fontSize={fontSize}
              textAnchor="middle"
              fill="#7c3aed"
            >
              {(mm / 1000).toFixed(2)} m
            </text>
          </g>
        )
      })}
    </g>
  )
}

/** Endpoint handles and a length readout for the selected wall. */
export function WallHandles({
  wall,
  mmPerPixel,
  onGrab,
}: {
  wall: Wall
  mmPerPixel: number
  onGrab: (handle: 'start' | 'end', event: React.PointerEvent) => void
}) {
  const r = mmPerPixel * 6
  const fontSize = Math.max(140, mmPerPixel * 11)
  const mx = (wall.start.x + wall.end.x) / 2
  const my = (wall.start.y + wall.end.y) / 2
  return (
    <g data-layer="handles">
      {(['start', 'end'] as const).map((handle) => (
        <circle
          key={handle}
          cx={wall[handle].x}
          cy={wall[handle].y}
          r={r}
          fill="#ffffff"
          stroke="#2563eb"
          strokeWidth={mmPerPixel * 2}
          style={{ cursor: 'crosshair' }}
          onPointerDown={(e) => onGrab(handle, e)}
        />
      ))}
      <text
        x={mx}
        y={my - r * 2}
        fontSize={fontSize}
        textAnchor="middle"
        fill="#2563eb"
        fontWeight={600}
        pointerEvents="none"
        style={{ userSelect: 'none' }}
      >
        {Math.round(wallLength(wall))} mm
      </text>
    </g>
  )
}

export function DraftWallLayer({
  draft,
  mmPerPixel,
  thickness,
}: {
  draft: { start: { x: number; y: number }; end: { x: number; y: number } }
  mmPerPixel: number
  thickness: number
}) {
  const length = Math.hypot(draft.end.x - draft.start.x, draft.end.y - draft.start.y)
  return (
    <g pointerEvents="none">
      <line
        x1={draft.start.x}
        y1={draft.start.y}
        x2={draft.end.x}
        y2={draft.end.y}
        stroke="#2563eb"
        strokeOpacity={0.7}
        strokeWidth={thickness}
      />
      <text
        x={(draft.start.x + draft.end.x) / 2}
        y={(draft.start.y + draft.end.y) / 2 - mmPerPixel * 14}
        fontSize={Math.max(140, mmPerPixel * 11)}
        textAnchor="middle"
        fill="#2563eb"
        fontWeight={600}
      >
        {Math.round(length)} mm
      </text>
    </g>
  )
}

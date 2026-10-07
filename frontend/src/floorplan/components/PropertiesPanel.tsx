import {
  useEditor,
  selectedDoor,
  selectedLabel,
  selectedRoom,
  selectedWall,
  selectedWindow,
  selectedGap,
} from '../state/store'
import { bandColour, bandOf, formatConfidence } from '../model/confidence'
import { planChecks, roomSchedule } from '../model/metrics'
import { polygonArea } from '../geometry/polygon'
import { wallAngle, wallLength } from '../geometry/wall'
import type { Wall } from '../model/types'
import { roundTo } from '../geometry/vec'
import type { Detected } from '../model/types'

function ConfidenceRow({ obj }: { obj: Detected }) {
  const band = bandOf(obj)
  return (
    <div className="flex items-center justify-between border-t border-slate-100 pt-2 text-xs">
      <span className="text-slate-500">Confidence</span>
      <span className="flex items-center gap-1.5">
        <span className="h-2 w-2 rounded-full" style={{ background: bandColour(band) }} />
        <span className="font-medium text-slate-700">{formatConfidence(obj)}</span>
        <span className="text-slate-400">{obj.source ?? 'manual'}</span>
      </span>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-[11px] font-medium uppercase tracking-wide text-slate-500">
        {label}
      </span>
      {children}
    </label>
  )
}

const inputClass =
  'w-full rounded border border-slate-300 px-2 py-1 text-sm tabular-nums focus:border-blue-500 focus:outline-none'

function NumberField({
  label,
  value,
  step = 1,
  onCommit,
}: {
  label: string
  value: number
  step?: number
  onCommit: (v: number) => void
}) {
  return (
    <Field label={label}>
      <input
        type="number"
        className={inputClass}
        step={step}
        value={roundTo(value, 1)}
        onChange={(e) => {
          const v = Number(e.target.value)
          if (Number.isFinite(v)) onCommit(v)
        }}
      />
    </Field>
  )
}

function WallProperties({ id }: { id: string }) {
  const wall = useEditor((s) => s.plan.walls.find((w) => w.id === id))
  const updateWall = useEditor((s) => s.updateWall)
  const deleteSelection = useEditor((s) => s.deleteSelection)
  if (!wall) return null

  return (
    <div className="flex flex-col gap-3">
      <Header title="Wall" id={wall.id} />
      <div className="grid grid-cols-2 gap-2">
        <NumberField
          label="Start X"
          value={wall.start.x}
          onCommit={(x) => updateWall(id, { start: { ...wall.start, x } })}
        />
        <NumberField
          label="Start Y"
          value={wall.start.y}
          onCommit={(y) => updateWall(id, { start: { ...wall.start, y } })}
        />
        <NumberField
          label="End X"
          value={wall.end.x}
          onCommit={(x) => updateWall(id, { end: { ...wall.end, x } })}
        />
        <NumberField
          label="End Y"
          value={wall.end.y}
          onCommit={(y) => updateWall(id, { end: { ...wall.end, y } })}
        />
      </div>
      <NumberField
        label="Thickness (mm)"
        value={wall.thickness}
        step={10}
        onCommit={(t) => updateWall(id, { thickness: Math.max(10, t) })}
      />
      <dl className="grid grid-cols-2 gap-y-1 text-xs text-slate-600">
        <dt className="text-slate-400">Length</dt>
        <dd className="text-right tabular-nums">{Math.round(wallLength(wall))} mm</dd>
        <dt className="text-slate-400">Angle</dt>
        <dd className="text-right tabular-nums">{roundTo(wallAngle(wall), 1)}°</dd>
      </dl>
      <ConfidenceRow obj={wall} />
      <DeleteButton onClick={deleteSelection} label="Delete wall" />
    </div>
  )
}

const ROOM_COLOURS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#64748b']

function RoomProperties({ id }: { id: string }) {
  const room = useEditor((s) => s.plan.rooms.find((r) => r.id === id))
  const renameRoom = useEditor((s) => s.renameRoom)
  const setRoomColour = useEditor((s) => s.setRoomColour)
  const resetRoomLabel = useEditor((s) => s.resetRoomLabel)
  const deleteSelection = useEditor((s) => s.deleteSelection)
  if (!room) return null

  const area = polygonArea(room.polygon) / 1_000_000 // mm² → m²

  return (
    <div className="flex flex-col gap-3">
      <Header title="Room" id={room.id} />
      <Field label="Name">
        <input
          className={inputClass}
          value={room.name}
          placeholder="Room name"
          onChange={(e) => renameRoom(id, e.target.value)}
        />
      </Field>
      <Field label="Colour">
        <div className="flex flex-wrap gap-1.5">
          {ROOM_COLOURS.map((c) => (
            <button
              key={c}
              type="button"
              aria-label={`Colour ${c}`}
              onClick={() => setRoomColour(id, c)}
              className={`h-6 w-6 rounded border-2 ${
                room.colour === c ? 'border-slate-900' : 'border-transparent'
              }`}
              style={{ background: c }}
            />
          ))}
          <button
            type="button"
            onClick={() => setRoomColour(id, undefined)}
            className="rounded border border-slate-300 px-2 text-xs text-slate-600 hover:bg-slate-100"
          >
            Default
          </button>
        </div>
      </Field>
      <dl className="grid grid-cols-2 gap-y-1 text-xs text-slate-600">
        <dt className="text-slate-400">Area</dt>
        <dd className="text-right tabular-nums">{area.toFixed(2)} m²</dd>
        <dt className="text-slate-400">Vertices</dt>
        <dd className="text-right tabular-nums">{room.polygon.length}</dd>
        <dt className="text-slate-400">Label at</dt>
        <dd className="text-right tabular-nums">
          {Math.round(room.labelPosition.x)}, {Math.round(room.labelPosition.y)}
        </dd>
      </dl>
      <button
        type="button"
        onClick={() => resetRoomLabel(id)}
        className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-700 hover:bg-slate-100"
      >
        Centre label
      </button>
      <ConfidenceRow obj={room} />
      <DeleteButton onClick={deleteSelection} label="Delete room" />
    </div>
  )
}

/** The wall an opening sits on, and how much room it has to slide. */
function useOpeningWall(wallId: string): { wall: Wall | undefined; length: number } {
  const wall = useEditor((s) => s.plan.walls.find((w) => w.id === wallId))
  return { wall, length: wall ? wallLength(wall) : 0 }
}

function OpeningPlacement({
  wallId,
  position,
  width,
  onPosition,
  onWidth,
}: {
  wallId: string
  position: number
  width: number
  onPosition: (v: number) => void
  onWidth: (v: number) => void
}) {
  const { wall, length } = useOpeningWall(wallId)
  // Keep the whole opening on the wall: the position is its centre.
  const half = Math.min(width, length) / 2
  const clamp = (v: number) => Math.max(half, Math.min(Math.max(half, length - half), v))

  return (
    <>
      <Field label="On wall">
        <p className="font-mono text-xs text-slate-500">{wall ? wall.id : 'missing wall'}</p>
      </Field>
      <NumberField
        label="Along wall (mm)"
        value={position}
        step={50}
        onCommit={(v) => onPosition(clamp(v))}
      />
      <NumberField
        label="Width (mm)"
        value={width}
        step={50}
        onCommit={(v) => onWidth(Math.max(50, Math.min(length, v)))}
      />
      <p className="text-[11px] text-slate-400">
        Wall is {Math.round(length)} mm long. Drag the opening on the canvas to slide it.
      </p>
    </>
  )
}

function DoorProperties({ id }: { id: string }) {
  const door = useEditor((s) => s.plan.doors.find((d) => d.id === id))
  const updateDoor = useEditor((s) => s.updateDoor)
  const rotateDoor = useEditor((s) => s.rotateDoor)
  const deleteSelection = useEditor((s) => s.deleteSelection)
  if (!door) return null

  const hingeAtStart = door.hingeAtStart ?? true
  const swing = door.swing ?? 90
  const style = door.style ?? 'swing'
  // Only a swinging door has a hinge and a side to open to.
  const swings = style === 'swing'
  const title = { swing: 'Door', sliding: 'Sliding door', garage: 'Garage door' }[style]

  return (
    <div className="flex flex-col gap-3">
      <Header title={title} id={door.id} />
      <Field label="Opens by">
        <Toggle
          options={[
            { label: 'Swinging', value: 'swing' as const },
            { label: 'Sliding', value: 'sliding' as const },
            { label: 'Garage', value: 'garage' as const },
          ]}
          value={door.style ?? 'swing'}
          onChange={(style) => updateDoor(id, { style })}
        />
      </Field>
      <OpeningPlacement
        wallId={door.wallId}
        position={door.position}
        width={door.width}
        onPosition={(position) => updateDoor(id, { position })}
        onWidth={(width) => updateDoor(id, { width })}
      />
      {swings && (
        <>
          <Field label="Hanging">
            <button
              type="button"
              onClick={() => rotateDoor(id)}
              className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
            >
              ⟳ Rotate 90°
            </button>
            <p className="mt-1 text-[11px] text-slate-400">
              Steps through all four ways the door can hang.
            </p>
          </Field>
          <Field label="Hinge">
            <Toggle
              options={[
                { label: 'Start jamb', value: true },
                { label: 'End jamb', value: false },
              ]}
              value={hingeAtStart}
              onChange={(v) => updateDoor(id, { hingeAtStart: v })}
            />
          </Field>
          <Field label="Opens to">
            <Toggle
              options={[
                { label: 'One side', value: 90 },
                { label: 'The other', value: -90 },
              ]}
              value={swing}
              onChange={(v) => updateDoor(id, { swing: v })}
            />
          </Field>
        </>
      )}
      <ConfidenceRow obj={door} />
      <DeleteButton onClick={deleteSelection} label="Delete door" />
    </div>
  )
}

function WindowProperties({ id }: { id: string }) {
  const win = useEditor((s) => s.plan.windows.find((w) => w.id === id))
  const updateWindow = useEditor((s) => s.updateWindow)
  const deleteSelection = useEditor((s) => s.deleteSelection)
  if (!win) return null

  return (
    <div className="flex flex-col gap-3">
      <Header title="Window" id={win.id} />
      <OpeningPlacement
        wallId={win.wallId}
        position={win.position}
        width={win.width}
        onPosition={(position) => updateWindow(id, { position })}
        onWidth={(width) => updateWindow(id, { width })}
      />
      <ConfidenceRow obj={win} />
      <DeleteButton onClick={deleteSelection} label="Delete window" />
    </div>
  )
}

function GapProperties({ id }: { id: string }) {
  const gap = useEditor((s) => s.plan.openings.find((o) => o.id === id))
  const convertOpening = useEditor((s) => s.convertOpening)
  const deleteSelection = useEditor((s) => s.deleteSelection)
  if (!gap) return null

  return (
    <div className="flex flex-col gap-3">
      <Header title="Opening" id={gap.id} />
      <p className="rounded border border-amber-200 bg-amber-50 p-2 text-xs text-amber-900">
        A gap was found in this wall, but its symbol could not be read. Say what
        it is and the position and width already measured are kept.
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => convertOpening(id, 'door')}
          className="flex-1 rounded bg-blue-600 px-2 py-1.5 text-sm text-white hover:bg-blue-700"
        >
          Make a door
        </button>
        <button
          type="button"
          onClick={() => convertOpening(id, 'window')}
          className="flex-1 rounded border border-slate-300 px-2 py-1.5 text-sm text-slate-700 hover:bg-slate-100"
        >
          Make a window
        </button>
      </div>
      <dl className="grid grid-cols-2 gap-y-1 text-xs text-slate-600">
        <dt className="text-slate-400">On wall</dt>
        <dd className="text-right font-mono">{gap.wallId}</dd>
        <dt className="text-slate-400">Width</dt>
        <dd className="text-right tabular-nums">{Math.round(gap.width)} mm</dd>
      </dl>
      <ConfidenceRow obj={gap} />
      <DeleteButton onClick={deleteSelection} label="Delete opening" />
    </div>
  )
}

function Toggle<T extends string | number | boolean>({
  options,
  value,
  onChange,
}: {
  options: Array<{ label: string; value: T }>
  value: T
  onChange: (value: T) => void
}) {
  return (
    <div className="flex overflow-hidden rounded border border-slate-300">
      {options.map((option) => (
        <button
          key={String(option.value)}
          type="button"
          onClick={() => onChange(option.value)}
          className={[
            'flex-1 px-2 py-1 text-xs transition',
            option.value === value
              ? 'bg-blue-600 text-white'
              : 'bg-white text-slate-600 hover:bg-slate-100',
          ].join(' ')}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

function LabelProperties({ id }: { id: string }) {
  const label = useEditor((s) => s.plan.labels.find((l) => l.id === id))
  const commit = useEditor((s) => s.commit)
  const deleteSelection = useEditor((s) => s.deleteSelection)
  if (!label) return null

  return (
    <div className="flex flex-col gap-3">
      <Header title={`Label (${label.type})`} id={label.id} />
      <Field label="Text">
        <input
          className={inputClass}
          value={label.text}
          onChange={(e) =>
            commit((draft) => {
              const l = draft.labels.find((x) => x.id === id)
              if (l) l.text = e.target.value
            })
          }
        />
      </Field>
      <ConfidenceRow obj={label} />
      <DeleteButton onClick={deleteSelection} label="Delete label" />
    </div>
  )
}

function Header({ title, id }: { title: string; id: string }) {
  return (
    <div>
      <h2 className="text-sm font-semibold text-slate-800">{title}</h2>
      <p className="font-mono text-[11px] text-slate-400">{id}</p>
    </div>
  )
}

function DeleteButton({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="mt-1 rounded border border-red-200 bg-red-50 px-2 py-1.5 text-sm text-red-700 hover:bg-red-100"
    >
      {label}
    </button>
  )
}

function Tile({ label, value, unit }: { label: string; value: string | number; unit?: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white px-3 py-2.5">
      <p className="text-[10.5px] font-semibold uppercase tracking-wider text-slate-400">{label}</p>
      <p className="mt-0.5 text-lg font-semibold tabular-nums text-slate-900">
        {value}
        {unit && <span className="ml-1 text-xs font-medium text-slate-400">{unit}</span>}
      </p>
    </div>
  )
}

/** What the plan holds, when nothing is selected. */
function PlanSummary({ onShowChecks }: { onShowChecks: () => void }) {
  const plan = useEditor((s) => s.plan)
  const schedule = roomSchedule(plan)
  const checks = planChecks(plan)
  const warnings = checks.filter((c) => c.severity === 'warning').length
  const swing = plan.doors.filter((d) => (d.style ?? 'swing') === 'swing').length
  const sliding = plan.doors.filter((d) => d.style === 'sliding').length
  const garage = plan.doors.filter((d) => d.style === 'garage').length

  const rows: Array<[string, number | string]> = [
    ['Swing doors', swing],
    ['Sliding doors', sliding],
    ['Garage doors', garage],
    ['Windows', plan.windows.length],
    ['Unclassified openings', plan.openings.length],
    ['Labels', plan.labels.length],
    ['Dimensions read', plan.dimensions.length],
  ]

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-2">
        <Tile label="Floor area" value={schedule.totalAreaM2.toFixed(1)} unit="m²" />
        <Tile label="Rooms" value={plan.rooms.length} />
        <Tile label="Walls" value={plan.walls.length} />
        <Tile label="Openings" value={plan.doors.length + plan.windows.length + plan.openings.length} />
      </div>

      {checks.length > 0 && (
        <button
          type="button"
          onClick={onShowChecks}
          className={`flex items-center gap-2 rounded-lg border p-2.5 text-left text-xs ${
            warnings > 0 ? 'border-amber-200 bg-amber-50 text-amber-900' : 'border-slate-200 bg-slate-50 text-slate-600'
          }`}
        >
          <span className="flex-1">
            <b className="font-semibold">{checks.length} item{checks.length === 1 ? '' : 's'} to review</b>
            {warnings > 0 && ` · ${warnings} warning${warnings === 1 ? '' : 's'}`}
          </span>
          <span className="font-medium">Open checks →</span>
        </button>
      )}

      <div>
        <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-500">Breakdown</p>
        <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white text-xs">
          {rows.map(([k, v]) => (
            <div key={k} className="flex justify-between px-3 py-1.5">
              <dt className="text-slate-500">{k}</dt>
              <dd className="tabular-nums font-medium text-slate-800">{v}</dd>
            </div>
          ))}
          {plan.source && (
            <div className="flex justify-between px-3 py-1.5">
              <dt className="text-slate-500">Scale</dt>
              <dd className="tabular-nums font-medium text-slate-800">{plan.source.mmPerPx.toFixed(2)} mm/px</dd>
            </div>
          )}
        </dl>
      </div>

      <p className="text-xs text-slate-400">
        Select an object on the canvas or in the navigator to edit it.
      </p>
    </div>
  )
}

/** The Properties tab: the selected object's fields, or the plan's figures when nothing is selected. */
export function PropertiesTab({ onShowChecks }: { onShowChecks: () => void }) {
  const selection = useEditor((s) => s.selection)
  const wall = useEditor(selectedWall)
  const room = useEditor(selectedRoom)
  const label = useEditor(selectedLabel)
  const door = useEditor(selectedDoor)
  const win = useEditor(selectedWindow)
  const gap = useEditor(selectedGap)

  return selection?.kind === 'wall' && wall ? (
    <WallProperties id={wall.id} />
  ) : selection?.kind === 'room' && room ? (
    <RoomProperties id={room.id} />
  ) : selection?.kind === 'door' && door ? (
    <DoorProperties id={door.id} />
  ) : selection?.kind === 'window' && win ? (
    <WindowProperties id={win.id} />
  ) : selection?.kind === 'opening' && gap ? (
    <GapProperties id={gap.id} />
  ) : selection?.kind === 'label' && label ? (
    <LabelProperties id={label.id} />
  ) : (
    <PlanSummary onShowChecks={onShowChecks} />
  )
}

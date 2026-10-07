import { useEditor } from '../state/store'
import { roomSchedule } from '../model/metrics'

const TOOL_HINTS = {
  select: 'Click to select · drag to move · Delete removes',
  wall: 'Drag to draw a wall · Shift keeps it straight',
  room: 'Click a room to select it',
  door: 'Click a wall to place a door',
  window: 'Click a wall to place a window',
} as const

const KIND_NAMES = {
  wall: 'Wall',
  room: 'Room',
  door: 'Door',
  window: 'Window',
  opening: 'Opening',
  label: 'Label',
} as const

/** One line along the bottom: what is selected, what the plan adds up to, and what the tool does. */
export function StatusBar() {
  const selection = useEditor((s) => s.selection)
  const tool = useEditor((s) => s.tool)
  const plan = useEditor((s) => s.plan)
  const hasImage = Boolean(plan.source)
  const area = roomSchedule(plan).totalAreaM2

  const cell = 'flex items-center gap-1.5 border-r border-slate-200 px-3 last:border-r-0'

  return (
    <footer className="flex h-7 shrink-0 items-center border-t border-slate-200 bg-white text-[11.5px] text-slate-500">
      <span className={cell}>
        {selection ? (
          <>
            <span className="h-1.5 w-1.5 rounded-full bg-blue-600" />
            <span className="text-slate-700">{KIND_NAMES[selection.kind]}</span>
            <span className="font-mono text-[10.5px]">{selection.id}</span>
          </>
        ) : (
          'Nothing selected'
        )}
      </span>
      <span className={`${cell} tabular-nums`}>
        {plan.rooms.length} rooms · {area.toFixed(1)} m² · {plan.walls.length} walls
      </span>
      <span className={cell}>{TOOL_HINTS[tool]}</span>
      <span className="ml-auto px-3 text-slate-400">
        {hasImage ? 'The image is reference only — edits change the plan, not the image' : 'No source image'}
      </span>
    </footer>
  )
}
